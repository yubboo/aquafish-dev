package com.aquafish.core.install;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import com.aquafish.core.installation.SystemInstallationSchema;
import com.aquafish.core.redis.RedisRuntimeSettingsService;
import com.aquafish.core.redis.RedisSettings;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 把已经完整安装的数据库安全恢复为当前电脑的运行实例。
 *
 * <p>该服务只写 application.yaml 和 install.lock，不创建管理员、
 * 不执行数据库迁移，也不修改任何业务数据。</p>
 */
@Service
public final class SetupExistingInstallationRecoveryService {

    private static final String DEFAULT_THEME = "default";

    private final SetupDatabaseInspectionService inspectionService;
    private final SetupDeploymentContextService contextService;
    private final DatabaseRuntimeSettingsService databaseSettingsService;
    private final RedisRuntimeSettingsService redisSettingsService;
    private final ApplicationConfigWriterService configWriterService;
    private final InstallLockService installLockService;
    private final DatabaseClient databaseClient;

    public SetupExistingInstallationRecoveryService(
        SetupDatabaseInspectionService inspectionService,
        SetupDeploymentContextService contextService,
        DatabaseRuntimeSettingsService databaseSettingsService,
        RedisRuntimeSettingsService redisSettingsService,
        ApplicationConfigWriterService configWriterService,
        InstallLockService installLockService,
        DatabaseClient databaseClient
    ) {
        this.inspectionService = Objects.requireNonNull(
            inspectionService,
            "数据库识别服务不能为空。"
        );
        this.contextService = Objects.requireNonNull(
            contextService,
            "部署上下文服务不能为空。"
        );
        this.databaseSettingsService = Objects.requireNonNull(
            databaseSettingsService,
            "数据库运行配置服务不能为空。"
        );
        this.redisSettingsService = Objects.requireNonNull(
            redisSettingsService,
            "Redis 运行配置服务不能为空。"
        );
        this.configWriterService = Objects.requireNonNull(
            configWriterService,
            "配置写入服务不能为空。"
        );
        this.installLockService = Objects.requireNonNull(
            installLockService,
            "安装锁服务不能为空。"
        );
        this.databaseClient = Objects.requireNonNull(
            databaseClient,
            "DatabaseClient 不能为空。"
        );
    }

    /**
     * 恢复当前电脑运行配置。
     *
     * <p>每次执行都会重新确认目标数据库仍是完整 INSTALLED。</p>
     */
    public Mono<SetupExistingInstallationRecoveryResult> recover(
        SetupExistingInstallationRecoveryRequest request
    ) {
        return Mono.defer(() -> {
            SetupExistingInstallationRecoveryRequest safe =
                safeRequest(request).normalized();
            SetupDeploymentContext context = contextService.current();
            DatabaseSettings database = resolveDatabase(safe, context);
            RedisSettings redis = resolveRedis(safe, context);

            return inspectionService.inspect(database)
                .map(this::requireRecoverable)
                .flatMap(inspection ->
                    readSiteSettings(database)
                        .flatMap(site ->
                            configWriterService.recoverExisting(
                                new SetupApplicationConfigRequest(
                                    safe.serverPort(),
                                    context.databaseManaged()
                                        ? null
                                        : database,
                                    context.redisManaged()
                                        ? null
                                        : redis,
                                    site,
                                    DEFAULT_THEME
                                )
                            )
                            .flatMap(configResult ->
                                writeLock(database, inspection)
                                    .thenReturn(
                                        new SetupExistingInstallationRecoveryResult(
                                            true,
                                            configResult.applicationConfigFile(),
                                            normalizePath(
                                                installLockService.lockFile()
                                            ),
                                            inspection.installedAt(),
                                            database.name(),
                                            database.tablePrefix(),
                                            "已有 Aquafish 已恢复到当前电脑，即将进入登录页。"
                                        )
                                    )
                            )
                        )
                );
        });
    }

    /**
     * 只允许完整已安装数据库进入恢复流程。
     *
     * <p>包级可见，只供同包单元测试验证。</p>
     */
    SetupDatabaseInspection requireRecoverable(
        SetupDatabaseInspection inspection
    ) {
        if (
            inspection == null
                || inspection.mode()
                    != SetupDatabaseMode.EXISTING_INSTALLED
                || !inspection.recoveryAllowed()
        ) {
            throw new IllegalStateException(
                "目标数据库不是可恢复的完整 Aquafish 实例。"
            );
        }

        return inspection;
    }

    /**
     * 从已有 options 表读取原站点设置。
     */
    private Mono<SiteSettings> readSiteSettings(
        DatabaseSettings settings
    ) {
        String table = TableNameResolver.tableName(
            settings.tablePrefix(),
            TableNames.OPTIONS
        );
        String sql =
            "select option_key, "
                + "coalesce(option_value, '') as option_value "
                + "from "
                + table
                + " where option_key in "
                + "('site.name','site.url','site.locale','site.timezone')";

        return databaseClient.sql(sql)
            .map((row, metadata) ->
                Map.entry(
                    text(row.get("option_key", String.class)),
                    text(row.get("option_value", String.class))
                )
            )
            .all()
            .filter(entry -> !entry.getKey().isBlank())
            .collectMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                LinkedHashMap::new
            )
            .map(options ->
                new SiteSettings(
                    value(options, "site.name", "Aquafish"),
                    value(
                        options,
                        "site.url",
                        "http://127.0.0.1:8520"
                    ),
                    value(options, "site.locale", "zh-CN"),
                    value(
                        options,
                        "site.timezone",
                        "Asia/Shanghai"
                    )
                ).normalized()
            );
    }

    /**
     * 恢复兼容安装锁。
     */
    private Mono<Void> writeLock(
        DatabaseSettings settings,
        SetupDatabaseInspection inspection
    ) {
        String installedVersion =
            inspection.installedVersion().isBlank()
                ? SystemInstallationSchema.CURRENT_INSTALLATION_VERSION
                : inspection.installedVersion();
        String prefix =
            TableNameResolver.normalizeConfiguredPrefix(
                settings.tablePrefix()
            );

        return Mono.fromRunnable(() ->
            installLockService.writeInstallLock(
                "installed=true\n"
                    + "installedAt=" + inspection.installedAt() + "\n"
                    + "version=" + installedVersion + "\n"
                    + "databaseType=" + settings.type().value() + "\n"
                    + "databaseName=" + settings.name() + "\n"
                    + "tablePrefix=" + prefix + "\n"
                    + "source=existing-database-recovery\n"
            )
        )
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    private DatabaseSettings resolveDatabase(
        SetupExistingInstallationRecoveryRequest request,
        SetupDeploymentContext context
    ) {
        DatabaseSettings settings = context.databaseManaged()
            ? databaseSettingsService.current().normalized()
            : request.database();

        if (settings == null || !settings.hasRequiredFields()) {
            throw new IllegalStateException(
                "数据库连接配置不完整。"
            );
        }

        DatabaseSettings safe = settings.normalized();
        databaseSettingsService.useForInstallation(safe);
        return safe;
    }

    private RedisSettings resolveRedis(
        SetupExistingInstallationRecoveryRequest request,
        SetupDeploymentContext context
    ) {
        RedisSettings settings = context.redisManaged()
            ? redisSettingsService.current().normalized()
            : request.redis().normalized();

        redisSettingsService.useForInstallation(settings);
        return settings;
    }

    private SetupExistingInstallationRecoveryRequest safeRequest(
        SetupExistingInstallationRecoveryRequest request
    ) {
        return request == null
            ? new SetupExistingInstallationRecoveryRequest(
                8520,
                null,
                RedisSettings.disabled()
            )
            : request;
    }

    private String value(
        Map<String, String> values,
        String key,
        String fallback
    ) {
        String current = values.get(key);
        return current == null || current.isBlank()
            ? fallback
            : current.trim();
    }

    private String text(Object value) {
        return value == null
            ? ""
            : value.toString().trim();
    }

    private String normalizePath(Path value) {
        return value == null
            ? ""
            : value.toAbsolutePath().normalize().toString();
    }
}
