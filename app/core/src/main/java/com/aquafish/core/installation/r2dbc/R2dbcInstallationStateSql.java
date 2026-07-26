package com.aquafish.core.installation.r2dbc;

import com.aquafish.core.database.DatabaseType;
import java.util.Objects;

/**
 * Aquafish 响应式安装状态仓库 SQL。
 *
 * <p>
 * 本类只负责构造固定 SQL，
 * 不连接数据库，也不保存任何连接信息。
 * </p>
 */
public final class R2dbcInstallationStateSql {

    private static final String COLUMNS =
        "singleton_id, "
            + "instance_id, "
            + "installation_state, "
            + "state_version, "
            + "initialization_attempt_id, "
            + "initialization_started_at, "
            + "installed_at, "
            + "installed_version, "
            + "last_error_code, "
            + "last_error_message, "
            + "created_at, "
            + "updated_at";

    private R2dbcInstallationStateSql() {
    }

    /**
     * 读取单例安装状态记录。
     */
    public static String selectCurrent(
        DatabaseType databaseType,
        String tableName,
        boolean forUpdate
    ) {
        String sql =
            "SELECT "
                + COLUMNS
                + " FROM "
                + quoteIdentifier(
                    databaseType,
                    tableName
                )
                + " WHERE singleton_id = :singletonId";

        return forUpdate
            ? sql + " FOR UPDATE"
            : sql;
    }

    /**
     * 创建第一条 INITIALIZING 记录。
     */
    public static String insertInitializing(
        DatabaseType databaseType,
        String tableName
    ) {
        return "INSERT INTO "
            + quoteIdentifier(
                databaseType,
                tableName
            )
            + " ("
            + COLUMNS
            + ") VALUES ("
            + ":singletonId, "
            + ":instanceId, "
            + ":installationState, "
            + ":stateVersion, "
            + ":attemptId, "
            + ":startedAt, "
            + "NULL, "
            + "NULL, "
            + "NULL, "
            + "NULL, "
            + ":createdAt, "
            + ":updatedAt"
            + ")";
    }

    /**
     * 把 FAILED 或 UNINITIALIZED 重新推进到 INITIALIZING。
     */
    public static String updateToInitializing(
        DatabaseType databaseType,
        String tableName
    ) {
        return "UPDATE "
            + quoteIdentifier(
                databaseType,
                tableName
            )
            + " SET "
            + "installation_state = :newState, "
            + "state_version = :newVersion, "
            + "initialization_attempt_id = :attemptId, "
            + "initialization_started_at = :startedAt, "
            + "installed_at = NULL, "
            + "installed_version = NULL, "
            + "last_error_code = NULL, "
            + "last_error_message = NULL, "
            + "updated_at = :updatedAt "
            + "WHERE singleton_id = :singletonId "
            + "AND state_version = :expectedVersion "
            + "AND installation_state = :expectedState";
    }

    /**
     * 把匹配的 INITIALIZING 尝试推进到 INSTALLED。
     */
    public static String updateToInstalled(
        DatabaseType databaseType,
        String tableName
    ) {
        return "UPDATE "
            + quoteIdentifier(
                databaseType,
                tableName
            )
            + " SET "
            + "installation_state = :newState, "
            + "state_version = :newVersion, "
            + "installed_at = :installedAt, "
            + "installed_version = :installedVersion, "
            + "last_error_code = NULL, "
            + "last_error_message = NULL, "
            + "updated_at = :updatedAt "
            + "WHERE singleton_id = :singletonId "
            + "AND state_version = :expectedVersion "
            + "AND installation_state = :expectedState "
            + "AND initialization_attempt_id = :attemptId";
    }

    /**
     * 把匹配的 INITIALIZING 尝试推进到 FAILED。
     */
    public static String updateToFailed(
        DatabaseType databaseType,
        String tableName
    ) {
        return "UPDATE "
            + quoteIdentifier(
                databaseType,
                tableName
            )
            + " SET "
            + "installation_state = :newState, "
            + "state_version = :newVersion, "
            + "installed_at = NULL, "
            + "installed_version = NULL, "
            + "last_error_code = :errorCode, "
            + "last_error_message = :errorMessage, "
            + "updated_at = :updatedAt "
            + "WHERE singleton_id = :singletonId "
            + "AND state_version = :expectedVersion "
            + "AND installation_state = :expectedState "
            + "AND initialization_attempt_id = :attemptId";
    }

    /**
     * 根据数据库类型安全引用表名。
     */
    public static String quoteIdentifier(
        DatabaseType databaseType,
        String identifier
    ) {
        DatabaseType safeType =
            Objects.requireNonNull(
                databaseType,
                "数据库类型不能为空。"
            );

        String safeIdentifier =
            requireIdentifier(
                identifier
            );

        return switch (safeType) {
            case MYSQL, MARIADB ->
                "`"
                    + safeIdentifier
                    + "`";

            case POSTGRESQL ->
                "\""
                    + safeIdentifier
                    + "\"";
        };
    }

    private static String requireIdentifier(
        String value
    ) {
        if (
            value == null
            || value.isBlank()
            || value.length() > 64
        ) {
            throw new IllegalStateException(
                "数据库表名非法。"
            );
        }

        for (
            int index = 0;
            index < value.length();
            index++
        ) {
            char current =
                value.charAt(index);

            if (
                current != '_'
                && !Character.isLetterOrDigit(
                    current
                )
            ) {
                throw new IllegalStateException(
                    "数据库表名包含非法字符。"
                );
            }
        }

        return value;
    }
}
