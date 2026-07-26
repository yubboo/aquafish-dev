package com.aquafish.core.database.migration.r2dbc;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.r2dbc.RuntimeR2dbcConnectionFactory;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Aquafish 响应式数据库迁移状态检查器。
 *
 * <p>该检查器只认 Aquafish 当前 R2DBC 迁移历史。</p>
 */
@Service
public final class R2dbcMigrationStateInspector {

    private final DatabaseRuntimeSettingsService
        settingsService;

    private final RuntimeR2dbcConnectionFactory
        connectionFactory;

    private final R2dbcMigrationFactory
        migrationFactory;

    private final R2dbcMigrationCatalog
        migrationCatalog;

    private final R2dbcMigrationStateReader
        stateReader;

    public R2dbcMigrationStateInspector(
        DatabaseRuntimeSettingsService settingsService,
        RuntimeR2dbcConnectionFactory connectionFactory,
        R2dbcMigrationFactory migrationFactory,
        R2dbcMigrationCatalog migrationCatalog,
        R2dbcMigrationStateReader stateReader
    ) {
        this.settingsService =
            Objects.requireNonNull(
                settingsService,
                "数据库运行配置服务不能为空。"
            );

        this.connectionFactory =
            Objects.requireNonNull(
                connectionFactory,
                "运行时 R2DBC 连接工厂不能为空。"
            );

        this.migrationFactory =
            Objects.requireNonNull(
                migrationFactory,
                "R2DBC 迁移计划工厂不能为空。"
            );

        this.migrationCatalog =
            Objects.requireNonNull(
                migrationCatalog,
                "R2DBC 迁移版本目录不能为空。"
            );

        this.stateReader =
            Objects.requireNonNull(
                stateReader,
                "R2DBC 迁移状态读取器不能为空。"
            );
    }

    public Mono<R2dbcMigrationDatabaseState>
        inspect() {

        return Mono.defer(
            () ->
                inspect(
                    settingsService.current()
                )
        );
    }

    public Mono<R2dbcMigrationDatabaseState>
        inspect(
            DatabaseSettings settings
        ) {

        return Mono.defer(
            () -> {
                DatabaseSettings safeSettings =
                    requireSettings(
                        settings
                    );

                settingsService.useForInstallation(
                    safeSettings
                );

                /*
                 * RuntimeR2dbcConnectionFactory 会在创建连接时
                 * 根据数据库配置指纹决定复用或切换连接池。
                 *
                 * 状态检查不能主动 refresh：它可能与迁移事务
                 * 并发执行，无条件刷新会关闭其他操作正在使用的
                 * 共享连接池。
                 */

                R2dbcMigrationPlan plan =
                    migrationFactory.create(
                        safeSettings
                    );

                R2dbcMigrationCatalogSnapshot
                    catalogSnapshot =
                    migrationCatalog.read(
                        plan
                    );

                R2dbcMigrationTableNames
                    tableNames =
                    R2dbcMigrationTableNames.from(
                        safeSettings
                    );

                return stateReader
                    .read(
                        connectionFactory,
                        safeSettings,
                        tableNames
                    )
                    .map(
                        databaseSnapshot ->
                            buildState(
                                safeSettings,
                                tableNames,
                                catalogSnapshot,
                                databaseSnapshot
                            )
                    );
            }
        );
    }

    private R2dbcMigrationDatabaseState
        buildState(
            DatabaseSettings settings,
            R2dbcMigrationTableNames tableNames,
            R2dbcMigrationCatalogSnapshot catalog,
            R2dbcMigrationDatabaseSnapshot database
        ) {

        Set<Long> expectedVersions =
            catalog
                .entries()
                .stream()
                .map(
                    R2dbcMigrationCatalogEntry
                        ::version
                )
                .collect(
                    Collectors.toSet()
                );

        Set<Long> appliedVersions =
            new TreeSet<>(
                database.appliedVersions()
            );

        List<Long> unknownVersions =
            appliedVersions
                .stream()
                .filter(
                    version ->
                        !expectedVersions.contains(
                            version
                        )
                )
                .toList();

        long currentVersion =
            database.currentVersion();

        List<Long> missingVersions =
            database.migrationsTableExists()
                ? catalog
                    .entries()
                    .stream()
                    .map(
                        R2dbcMigrationCatalogEntry
                            ::version
                    )
                    .filter(
                        version ->
                            version <= currentVersion
                    )
                    .filter(
                        version ->
                            !appliedVersions.contains(
                                version
                            )
                    )
                    .toList()
                : List.of();

        boolean databaseAhead =
            currentVersion
                > catalog.latestVersion();

        boolean historyConsistent =
            unknownVersions.isEmpty()
                && missingVersions.isEmpty();

        boolean unmanagedDatabase =
            database.totalTables() > 0
                && !database.migrationsTableExists();

        List<R2dbcMigrationCatalogEntry>
            pendingEntries =
            databaseAhead
                ? List.of()
                : catalog.entriesAfter(
                    currentVersion
                );

        boolean canMigrate =
            !unmanagedDatabase
                && !databaseAhead
                && historyConsistent
                && (
                    database.emptyDatabase()
                        || database
                            .migrationsTableExists()
                );

        String note =
            note(
                database,
                databaseAhead,
                historyConsistent,
                unmanagedDatabase,
                pendingEntries
            );

        return new R2dbcMigrationDatabaseState(
            settings.type(),
            tableNames,
            database.totalTables(),
            database.emptyDatabase(),
            database.migrationsTableExists(),
            database.migrationsLockTableExists(),
            database.appliedVersions(),
            currentVersion,
            catalog.latestVersion(),
            pendingEntries.size(),
            pendingEntries,
            unknownVersions,
            missingVersions,
            databaseAhead,
            historyConsistent,
            unmanagedDatabase,
            canMigrate,
            note
        );
    }

    private String note(
        R2dbcMigrationDatabaseSnapshot database,
        boolean databaseAhead,
        boolean historyConsistent,
        boolean unmanagedDatabase,
        List<R2dbcMigrationCatalogEntry>
            pendingEntries
    ) {
        if (unmanagedDatabase) {
            return "检测到非空且没有 R2DBC 迁移历史的数据库，"
                + "开发阶段请清空数据库后重新初始化。";
        }

        if (databaseAhead) {
            return "数据库版本高于当前 Aquafish 代码版本，"
                + "禁止使用旧程序继续迁移。";
        }

        if (!historyConsistent) {
            return "数据库迁移历史存在未知版本或版本缺口，"
                + "禁止自动迁移。";
        }

        if (database.emptyDatabase()) {
            return "数据库为空，可以执行首次 R2DBC 迁移。";
        }

        if (pendingEntries.isEmpty()) {
            return "数据库已经是当前最新版本。";
        }

        return "数据库存在 "
            + pendingEntries.size()
            + " 个待执行迁移。";
    }

    private DatabaseSettings requireSettings(
        DatabaseSettings settings
    ) {
        if (settings == null) {
            throw new IllegalStateException(
                "数据库配置不能为空。"
            );
        }

        DatabaseSettings normalized =
            settings.normalized();

        if (!normalized.hasRequiredFields()) {
            throw new IllegalStateException(
                "数据库连接配置不完整。"
            );
        }

        return normalized;
    }
}
