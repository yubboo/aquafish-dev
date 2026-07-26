package com.aquafish.core.install;

import com.aquafish.core.database.AquafishDatabaseTableCatalog;
import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.migration.r2dbc.R2dbcMigrationTableNames;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 安装向导危险重装的精确数据库清理服务。
 *
 * <p>只有服务器重新检测为 EXISTING_INSTALLED 或
 * INCOMPLETE_INSTALLATION，并且用户完成数据丢失复选框和确认词后，
 * 才允许执行。</p>
 *
 * <p>只删除正式业务表白名单、迁移历史表和迁移锁表。
 * 不执行 DROP DATABASE，也不按照表前缀模糊删除未知表。</p>
 */
@Service
public final class SetupDatabaseResetService {

    private static final String REINSTALL_CONFIRMATION =
        "重新安装";

    private final SetupDatabaseInspectionService inspectionService;
    private final SetupDeploymentContextService contextService;
    private final DatabaseRuntimeSettingsService settingsService;
    private final ConnectionFactory connectionFactory;
    private final InstallLockService installLockService;

    public SetupDatabaseResetService(
        SetupDatabaseInspectionService inspectionService,
        SetupDeploymentContextService contextService,
        DatabaseRuntimeSettingsService settingsService,
        ConnectionFactory connectionFactory,
        InstallLockService installLockService
    ) {
        this.inspectionService = Objects.requireNonNull(
            inspectionService,
            "数据库识别服务不能为空。"
        );
        this.contextService = Objects.requireNonNull(
            contextService,
            "部署上下文服务不能为空。"
        );
        this.settingsService = Objects.requireNonNull(
            settingsService,
            "数据库运行配置服务不能为空。"
        );
        this.connectionFactory = Objects.requireNonNull(
            connectionFactory,
            "ConnectionFactory 不能为空。"
        );
        this.installLockService = Objects.requireNonNull(
            installLockService,
            "安装锁服务不能为空。"
        );
    }

    /**
     * 执行危险重装前的最终服务器核验和精确清理。
     */
    public Mono<SetupDatabaseResetResult> reset(
        SetupDatabaseResetRequest request
    ) {
        return Mono.defer(() -> {
            SetupDatabaseResetRequest safe =
                requireConfirmed(request);
            DatabaseSettings database =
                resolveDatabase(safe);

            return inspectionService.inspect(database)
                .map(inspection ->
                    requireResettable(
                        inspection,
                        safe.expectedMode()
                    )
                )
                .flatMap(before -> {
                    List<String> tables =
                        resetTableNames(database);

                    return dropTables(database, tables)
                        .then(
                            Mono.fromRunnable(
                                installLockService::deleteInstallLock
                            )
                                .subscribeOn(
                                    Schedulers.boundedElastic()
                                )
                        )
                        .then(
                            inspectionService.inspect(database)
                        )
                        .flatMap(after -> {
                            if (
                                after.mode()
                                    != SetupDatabaseMode.NEW_INSTALL
                                    || !after.newInstallAllowed()
                            ) {
                                return Mono.error(
                                    new IllegalStateException(
                                        "Aquafish 表清理后重新检测仍不是空数据库，已停止安装。"
                                    )
                                );
                            }

                            return Mono.just(
                                new SetupDatabaseResetResult(
                                    true,
                                    before.mode(),
                                    after.mode(),
                                    tables.size(),
                                    database.name(),
                                    database.tablePrefix(),
                                    "当前表前缀下的 Aquafish 表已精确清理，可以重新执行首次安装。"
                                )
                            );
                        });
                });
        });
    }

    /**
     * 校验复选框和允许重装的数据库状态。
     *
     * <p>包级可见，只供同包单元测试验证。</p>
     */
    SetupDatabaseResetRequest requireConfirmed(
        SetupDatabaseResetRequest request
    ) {
        if (request == null) {
            throw new IllegalStateException(
                "数据库重装请求不能为空。"
            );
        }

        SetupDatabaseResetRequest safe =
            request.normalized();

        if (!Boolean.TRUE.equals(safe.dataLossConfirmed())) {
            throw new IllegalStateException(
                "必须勾选确认永久删除当前 Aquafish 数据。"
            );
        }

        if (
            safe.expectedMode()
                != SetupDatabaseMode.EXISTING_INSTALLED
                && safe.expectedMode()
                    != SetupDatabaseMode.INCOMPLETE_INSTALLATION
        ) {
            throw new IllegalStateException(
                "当前数据库状态不允许使用重装清理入口。"
            );
        }

        return safe;
    }

    /**
     * 后端重新检测结果必须与用户确认时一致。
     *
     * <p>包级可见，只供同包单元测试验证。</p>
     */
    SetupDatabaseInspection requireResettable(
        SetupDatabaseInspection inspection,
        SetupDatabaseMode expectedMode
    ) {
        if (
            inspection == null
                || inspection.mode() != expectedMode
        ) {
            throw new IllegalStateException(
                "数据库状态已经变化，请重新测试后再确认重装。"
            );
        }

        boolean allowed =
            inspection.mode()
                == SetupDatabaseMode.EXISTING_INSTALLED
                ? inspection.fullReinstallAllowed()
                : inspection.residueCleanupAllowed();

        if (!allowed) {
            throw new IllegalStateException(
                "服务器未允许清理当前数据库状态。"
            );
        }

        return inspection;
    }

    /**
     * 确认词必须是当前数据库名或“重新安装”。
     *
     * <p>包级可见，只供同包单元测试验证。</p>
     */
    void requireConfirmationText(
        SetupDatabaseResetRequest request,
        DatabaseSettings database
    ) {
        String text = request.confirmationText();

        if (
            !database.name().equals(text)
                && !REINSTALL_CONFIRMATION.equals(text)
        ) {
            throw new IllegalStateException(
                "确认词不正确，请输入当前数据库名或“重新安装”。"
            );
        }
    }

    /**
     * 生成精确清理白名单。
     *
     * <p>71 张正式业务表，加 migrations 和 migrations_lock。</p>
     */
    List<String> resetTableNames(
        DatabaseSettings settings
    ) {
        LinkedHashSet<String> names =
            new LinkedHashSet<>(
                AquafishDatabaseTableCatalog
                    .physicalTableNames(settings)
            );

        R2dbcMigrationTableNames migrationNames =
            R2dbcMigrationTableNames.from(settings);

        names.add(migrationNames.migrationsLockTable());
        names.add(migrationNames.migrationsTable());

        return List.copyOf(names);
    }

    /**
     * 仅用于测试生成的精确 DROP 语句。
     */
    List<String> dropStatements(
        DatabaseSettings settings
    ) {
        return resetTableNames(settings)
            .stream()
            .map(table ->
                dropStatement(
                    settings.type(),
                    table
                )
            )
            .toList();
    }

    private DatabaseSettings resolveDatabase(
        SetupDatabaseResetRequest request
    ) {
        DatabaseSettings settings =
            contextService.current().databaseManaged()
                ? settingsService.current().normalized()
                : request.database();

        if (settings == null || !settings.hasRequiredFields()) {
            throw new IllegalStateException(
                "数据库连接配置不完整。"
            );
        }

        DatabaseSettings safe = settings.normalized();
        requireConfirmationText(request, safe);
        settingsService.useForInstallation(safe);
        return safe;
    }

    /**
     * 使用同一 R2DBC Connection 顺序清理表。
     */
    private Mono<Void> dropTables(
        DatabaseSettings settings,
        List<String> tables
    ) {
        return Mono.usingWhen(
            Mono.from(connectionFactory.create()),
            connection ->
                dropOnConnection(
                    connection,
                    settings.type(),
                    tables
                ),
            connection ->
                Mono.from(connection.close())
        );
    }

    /**
     * MySQL/MariaDB 在同一连接中临时关闭外键检查；
     * PostgreSQL 对精确白名单表使用 CASCADE。
     */
    private Mono<Void> dropOnConnection(
        Connection connection,
        DatabaseType type,
        List<String> tables
    ) {
        if (
            type == DatabaseType.MYSQL
                || type == DatabaseType.MARIADB
        ) {
            Mono<Void> drops =
                execute(
                    connection,
                    "SET FOREIGN_KEY_CHECKS = 0"
                )
                    .thenMany(
                        Flux.fromIterable(tables)
                            .concatMap(table ->
                                execute(
                                    connection,
                                    dropStatement(type, table)
                                )
                            )
                    )
                    .then();

            return drops
                .then(
                    execute(
                        connection,
                        "SET FOREIGN_KEY_CHECKS = 1"
                    )
                )
                .onErrorResume(error ->
                    execute(
                        connection,
                        "SET FOREIGN_KEY_CHECKS = 1"
                    )
                        .onErrorResume(
                            ignored -> Mono.empty()
                        )
                        .then(Mono.error(error))
                );
        }

        return Flux.fromIterable(tables)
            .concatMap(table ->
                execute(
                    connection,
                    dropStatement(type, table)
                )
            )
            .then();
    }

    private Mono<Void> execute(
        Connection connection,
        String sql
    ) {
        return Flux.from(
            connection
                .createStatement(sql)
                .execute()
        )
            .flatMap(Result::getRowsUpdated)
            .then();
    }

    private String dropStatement(
        DatabaseType type,
        String tableName
    ) {
        return switch (type) {
            case MYSQL, MARIADB ->
                "DROP TABLE IF EXISTS "
                    + mysqlIdentifier(tableName);

            case POSTGRESQL ->
                "DROP TABLE IF EXISTS "
                    + postgresIdentifier(tableName)
                    + " CASCADE";
        };
    }

    private String mysqlIdentifier(String value) {
        return "`"
            + value.replace("`", "``")
            + "`";
    }

    private String postgresIdentifier(String value) {
        return "\""
            + value.replace("\"", "\"\"")
            + "\"";
    }
}
