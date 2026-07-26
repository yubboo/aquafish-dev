package com.aquafish.core.install;

import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.config.AquafishPathResolver;
import com.aquafish.core.redis.RedisRuntimeSettingsService;
import com.aquafish.core.redis.RedisSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Aquafish application.yaml 写入服务。
 *
 * 当前阶段：
 * Step 17-22-3：安装配置写入 workdir/application.yaml。
 *
 * 写入位置：
 * workdir/application.yaml
 *
 * 注意：
 * 1. 这里只写配置；
 * 2. 不初始化数据库表；
 * 3. 不创建管理员；
 * 4. 不写 install.lock；
 * 5. install.lock 会在最终安装成功后写入。
 */
@Service
public class ApplicationConfigWriterService {

    private static final DateTimeFormatter BACKUP_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path workDir;

    private final Path applicationConfigFile;

    private final Path backupsDir;

    private final AuthoritativeInstallStatusService installStatusService;

    private final DatabaseRuntimeSettingsService databaseRuntimeSettingsService;

    private final RedisRuntimeSettingsService redisRuntimeSettingsService;

    private final SetupDeploymentContextService deploymentContextService;

    public ApplicationConfigWriterService(
        @Value("${aquafish.work-dir:workdir}") String workDir,
        AuthoritativeInstallStatusService installStatusService,
        DatabaseRuntimeSettingsService databaseRuntimeSettingsService,
        RedisRuntimeSettingsService redisRuntimeSettingsService,
        SetupDeploymentContextService deploymentContextService
    ) {
        this.workDir = AquafishPathResolver.resolveWorkDirPath(workDir);
        this.applicationConfigFile = this.workDir.resolve("application.yaml");
        this.backupsDir = this.workDir.resolve("backups").resolve("setup-config");
        this.installStatusService = installStatusService;
        this.databaseRuntimeSettingsService = databaseRuntimeSettingsService;
        this.redisRuntimeSettingsService = redisRuntimeSettingsService;
        this.deploymentContextService = deploymentContextService;
    }

    /**
     * 预览 application.yaml。
     */
    public Mono<ApplicationConfigPreview> preview(SetupApplicationConfigRequest request) {
        return Mono.defer(() -> {
            SetupApplicationConfigRequest normalized =
                safeRequest(request).normalized();

            return installStatusService.current()
                .map(status ->
                    new ApplicationConfigPreview(
                        normalizePath(applicationConfigFile),
                        status.installed(),
                        status.canInstall(),
                        buildYaml(normalized, deploymentContextService.current(), false),
                        status.canInstall()
                            ? "数据库尚未完成安装，可以写入 application.yaml。"
                            : "数据库已安装、状态不可用或目录不可写，不允许覆盖安装配置。"
                    )
                );
        });
    }

    /**
     * 写入 application.yaml。
     */
    public Mono<ApplicationConfigWriteResult> write(SetupApplicationConfigRequest request) {
        return Mono.defer(() -> {
            SetupApplicationConfigRequest normalized =
                safeRequest(request).normalized();

            return installStatusService.current()
                .flatMap(status -> {
                    if (!status.canInstall()) {
                        return Mono.error(
                            new IllegalStateException(
                                "数据库已安装、状态不可用或目录不可写，不允许覆盖安装配置。"
                            )
                        );
                    }

                    return writeFile(
                        normalized,
                        deploymentContextService.current(),
                        false,
                        "application.yaml 写入成功。下一步可以初始化数据库表和管理员账号。"
                    );
                });
        });
    }

    /**
     * 已安装数据库恢复专用写入入口。
     *
     * <p>调用方必须先重新确认目标数据库是 EXISTING_INSTALLED。
     * 本方法不会执行迁移或创建管理员。</p>
     */
    public Mono<ApplicationConfigWriteResult> recoverExisting(
        SetupApplicationConfigRequest request
    ) {
        return Mono.defer(() ->
            writeFile(
                safeRequest(request).normalized(),
                deploymentContextService.current(),
                true,
                "application.yaml 已按已有数据库恢复，并标记为已安装。"
            )
        );
    }

    private Mono<ApplicationConfigWriteResult> writeFile(
        SetupApplicationConfigRequest normalized,
        SetupDeploymentContext deploymentContext,
        boolean installed,
        String successMessage
    ) {
        return Mono.fromCallable(() -> {
            String yaml = buildYaml(
                normalized,
                deploymentContext,
                installed
            );
            String backupFile = null;

            try {
                Files.createDirectories(workDir);
                Files.createDirectories(backupsDir);

                if (Files.exists(applicationConfigFile)) {
                    Path backupPath = backupsDir.resolve(
                        "application-"
                            + LocalDateTime.now().format(BACKUP_TIME_FORMATTER)
                            + ".yaml.bak"
                    );

                    Files.copy(applicationConfigFile, backupPath);
                    backupFile = normalizePath(backupPath);
                }

                Files.writeString(
                    applicationConfigFile,
                    yaml,
                    StandardCharsets.UTF_8
                );
                /*
                 * 分发安装的数据库/Redis来自表单，需要覆盖当前进程默认值；
                 * 托管模式继续使用部署平台注入值，绝不让页面请求覆盖平台密钥。
                 */
                if (!deploymentContext.databaseManaged()) {
                    databaseRuntimeSettingsService.useForInstallation(
                        normalized.database()
                    );
                }
                if (!deploymentContext.redisManaged()) {
                    redisRuntimeSettingsService.useForInstallation(
                        normalized.redis()
                    );
                }

                return new ApplicationConfigWriteResult(
                    normalizePath(applicationConfigFile),
                    backupFile,
                    true,
                    installed,
                    yaml,
                    successMessage
                );
            } catch (IOException error) {
                throw new IllegalStateException(
                    "application.yaml 写入失败：" + error.getMessage(),
                    error
                );
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String buildYaml(
        SetupApplicationConfigRequest request,
        SetupDeploymentContext deploymentContext,
        boolean installed
    ) {
        DatabaseSettings database = deploymentContext.databaseManaged()
            ? databaseRuntimeSettingsService.current().normalized()
            : request.database().normalized();
        RedisSettings redis = deploymentContext.redisManaged()
            ? redisRuntimeSettingsService.current().normalized()
            : request.redis().normalized();
        SiteSettings site = request.site().normalized();

        StringBuilder yaml = new StringBuilder();

        yaml.append("server:\n");
        yaml.append("  port: ").append(request.serverPort()).append("\n");
        yaml.append("\n");

        yaml.append("aquafish:\n");
        yaml.append("  work-dir: ").append(quote(normalizePathForYaml(workDir))).append("\n");
        yaml.append("  external-url: ").append(quote(site.url())).append("\n");
        yaml.append("\n");

        yaml.append("  site:\n");
        yaml.append("    name: ").append(quote(site.name())).append("\n");
        yaml.append("    url: ").append(quote(site.url())).append("\n");
        yaml.append("    locale: ").append(quote(site.locale())).append("\n");
        yaml.append("    timezone: ").append(quote(site.timezone())).append("\n");
        yaml.append("\n");

        yaml.append("  database:\n");
        if (!deploymentContext.databaseManaged()) {
            yaml.append("    type: ").append(database.type().value()).append("\n");
            yaml.append("    host: ").append(quote(database.host())).append("\n");
            yaml.append("    port: ").append(database.port()).append("\n");
            yaml.append("    name: ").append(quote(database.name())).append("\n");
            yaml.append("    username: ").append(quote(database.username())).append("\n");
            yaml.append("    password: ").append(quote(database.password())).append("\n");
        } else {
            yaml.append("    # 连接参数由部署平台环境变量管理，不在文件中复制密码。\n");
        }
        yaml.append("    table-prefix: ").append(quote(database.tablePrefix())).append("\n");

        if (
            database.type() == DatabaseType.MYSQL
                || database.type() == DatabaseType.MARIADB
        ) {
            yaml.append("    charset: utf8mb4\n");
            yaml.append("    collation: utf8mb4_general_ci\n");
        } else if (database.type() == DatabaseType.POSTGRESQL) {
            yaml.append("    schema: public\n");
        }

        yaml.append("\n");

        yaml.append("  redis:\n");
        yaml.append("    enabled: ").append(redis.enabled()).append("\n");
        if (redis.enabled() && !deploymentContext.redisManaged()) {
            yaml.append("    host: ").append(quote(redis.host())).append("\n");
            yaml.append("    port: ").append(redis.port()).append("\n");
            yaml.append("    database: ").append(redis.database()).append("\n");
            yaml.append("    username: ").append(quote(redis.username())).append("\n");
            yaml.append("    password: ").append(quote(redis.password())).append("\n");
            yaml.append("    ssl: ").append(redis.ssl()).append("\n");
        } else if (redis.enabled()) {
            yaml.append("    # 连接参数由部署平台环境变量管理，不在文件中复制密码。\n");
        }
        yaml.append("\n");

        yaml.append("  theme:\n");
        yaml.append("    active: ").append(quote(request.activeTheme())).append("\n");
        yaml.append("\n");

        yaml.append("  install:\n");
        yaml.append("    locked: ").append(installed).append("\n");

        return yaml.toString();
    }

    private SetupApplicationConfigRequest safeRequest(SetupApplicationConfigRequest request) {
        if (request == null) {
            return new SetupApplicationConfigRequest(
                8520,
                DatabaseSettings.defaultMysql(),
                RedisSettings.disabled(),
                SiteSettings.defaultSettings(),
                "default"
            );
        }

        return request;
    }

    private String quote(String value) {
        if (value == null) {
            return "''";
        }

        return "'" + value.replace("'", "''") + "'";
    }

    private String normalizePath(Path path) {
        if (path == null) {
            return "";
        }

        return path.toAbsolutePath().normalize().toString();
    }

    private String normalizePathForYaml(Path path) {
        return normalizePath(path).replace("\\", "/");
    }
}
