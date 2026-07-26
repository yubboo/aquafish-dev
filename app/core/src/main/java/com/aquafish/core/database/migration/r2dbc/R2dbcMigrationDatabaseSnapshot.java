package com.aquafish.core.database.migration.r2dbc;

import java.util.List;
import java.util.TreeSet;

/**
 * 从数据库直接读取的迁移原始状态。
 *
 * @param totalTables 当前表前缀下已存在的 Aquafish 正式业务表数量
 * @param migrationsTableExists R2DBC 迁移历史表是否存在
 * @param migrationsLockTableExists R2DBC 迁移锁表是否存在
 * @param appliedVersions 已执行的迁移版本
 */
public record R2dbcMigrationDatabaseSnapshot(
    long totalTables,
    boolean migrationsTableExists,
    boolean migrationsLockTableExists,
    List<Long> appliedVersions
) {

    public R2dbcMigrationDatabaseSnapshot {
        if (totalTables < 0) {
            throw new IllegalArgumentException(
                "数据库表数量不能小于零。"
            );
        }

        TreeSet<Long> normalizedVersions =
            new TreeSet<>();

        if (appliedVersions != null) {
            for (Long version : appliedVersions) {
                if (
                    version == null
                    || version <= 0
                ) {
                    throw new IllegalArgumentException(
                        "数据库迁移历史包含非法版本。"
                    );
                }

                normalizedVersions.add(
                    version
                );
            }
        }

        appliedVersions =
            List.copyOf(
                normalizedVersions
            );

        if (
            !migrationsTableExists
            && !appliedVersions.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "迁移历史表不存在时不能包含已执行版本。"
            );
        }
    }

    public boolean emptyDatabase() {
        return totalTables == 0;
    }

    public long currentVersion() {
        return appliedVersions.isEmpty()
            ? 0
            : appliedVersions.getLast();
    }
}
