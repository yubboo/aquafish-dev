package com.aquafish.core.database.migration.r2dbc;

import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import java.util.Objects;

/**
 * Aquafish R2DBC 数据库迁移相关真实表名。
 *
 * @param migrationsTable 版本历史表
 * @param migrationsLockTable 并发锁表
 */
public record R2dbcMigrationTableNames(
    String migrationsTable,
    String migrationsLockTable
) {

    public static final String
        MIGRATIONS_LOGICAL_NAME =
        "migrations";

    public static final String
        MIGRATIONS_LOCK_LOGICAL_NAME =
        "migrations_lock";

    public R2dbcMigrationTableNames {
        migrationsTable =
            requireIdentifier(
                migrationsTable,
                "R2DBC 迁移历史表名不能为空。"
            );

        migrationsLockTable =
            requireIdentifier(
                migrationsLockTable,
                "R2DBC 迁移锁表名不能为空。"
            );
    }

    /**
     * 根据数据库配置解析真实迁移表名。
     */
    public static R2dbcMigrationTableNames from(
        DatabaseSettings settings
    ) {
        DatabaseSettings safeSettings =
            Objects.requireNonNull(
                settings,
                "数据库配置不能为空。"
            ).normalized();

        return new R2dbcMigrationTableNames(
            TableNameResolver.tableName(
                safeSettings.tablePrefix(),
                MIGRATIONS_LOGICAL_NAME
            ),
            TableNameResolver.tableName(
                safeSettings.tablePrefix(),
                MIGRATIONS_LOCK_LOGICAL_NAME
            )
        );
    }

    private static String requireIdentifier(
        String value,
        String message
    ) {
        if (
            value == null
            || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                message
            );
        }

        String normalized =
            value.trim();

        if (!normalized.equals(value)) {
            throw new IllegalStateException(
                "数据库表名不能包含首尾空格。"
            );
        }

        if (normalized.length() > 64) {
            throw new IllegalStateException(
                "数据库表名长度不能超过 64。"
            );
        }

        for (
            int index = 0;
            index < normalized.length();
            index++
        ) {
            char current =
                normalized.charAt(index);

            if (
                current != '_'
                && !Character.isLetterOrDigit(
                    current
                )
            ) {
                throw new IllegalStateException(
                    "数据库表名只能包含字母、数字和下划线。"
                );
            }
        }

        return normalized;
    }
}
