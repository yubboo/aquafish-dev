package com.aquafish.core.database.migration.r2dbc;

import com.aquafish.core.database.DatabaseType;
import java.util.List;
import java.util.Objects;

/**
 * Aquafish 完整 R2DBC 数据库迁移状态。
 */
public record R2dbcMigrationDatabaseState(
    DatabaseType databaseType,
    R2dbcMigrationTableNames tableNames,
    long totalTables,
    boolean emptyDatabase,
    boolean migrationsTableExists,
    boolean migrationsLockTableExists,
    List<Long> appliedVersions,
    long currentVersion,
    long latestVersion,
    int pendingMigrations,
    List<R2dbcMigrationCatalogEntry> pendingEntries,
    List<Long> unknownAppliedVersions,
    List<Long> missingAppliedVersions,
    boolean databaseAhead,
    boolean historyConsistent,
    boolean unmanagedDatabase,
    boolean canMigrate,
    String note
) {

    public R2dbcMigrationDatabaseState {
        Objects.requireNonNull(
            databaseType,
            "数据库类型不能为空。"
        );

        Objects.requireNonNull(
            tableNames,
            "迁移相关表名不能为空。"
        );

        if (
            totalTables < 0
            || currentVersion < 0
            || latestVersion < 0
            || pendingMigrations < 0
        ) {
            throw new IllegalArgumentException(
                "数据库迁移状态中的数字不能小于零。"
            );
        }

        appliedVersions =
            copy(appliedVersions);

        pendingEntries =
            pendingEntries == null
                ? List.of()
                : List.copyOf(
                    pendingEntries
                );

        unknownAppliedVersions =
            copy(unknownAppliedVersions);

        missingAppliedVersions =
            copy(missingAppliedVersions);

        note =
            note == null
                ? ""
                : note;
    }

    private static List<Long> copy(
        List<Long> values
    ) {
        return values == null
            ? List.of()
            : List.copyOf(values);
    }
}
