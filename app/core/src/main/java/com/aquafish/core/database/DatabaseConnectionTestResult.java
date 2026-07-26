package com.aquafish.core.database;

/**
 * 数据库连接测试结果。
 *
 * 当前阶段：
 * Step 17-22-1：数据库安装配置与连接测试底层。
 *
 * 注意：
 * 这里不能返回 password。
 */
public record DatabaseConnectionTestResult(
    DatabaseType type,
    String host,
    int port,
    String name,
    String username,
    String tablePrefix,
    String connectionUrl,
    long elapsedMillis,
    boolean connected,
    String databaseProductName,
    String databaseProductVersion,
    String driverName,
    String driverVersion,
    String errorMessage
) {

    public static DatabaseConnectionTestResult success(
        DatabaseSettings settings,
        String connectionUrl,
        long elapsedMillis,
        String databaseProductName,
        String databaseProductVersion,
        String driverName,
        String driverVersion
    ) {
        DatabaseSettings safe = settings.normalized();

        return new DatabaseConnectionTestResult(
            safe.type(),
            safe.host(),
            safe.port(),
            safe.name(),
            safe.username(),
            safe.tablePrefix(),
            connectionUrl,
            Math.max(0L, elapsedMillis),
            true,
            databaseProductName,
            databaseProductVersion,
            driverName,
            driverVersion,
            null
        );
    }

    public static DatabaseConnectionTestResult failure(
        DatabaseSettings settings,
        String connectionUrl,
        long elapsedMillis,
        String errorMessage
    ) {
        DatabaseSettings safe = settings.normalized();

        return new DatabaseConnectionTestResult(
            safe.type(),
            safe.host(),
            safe.port(),
            safe.name(),
            safe.username(),
            safe.tablePrefix(),
            connectionUrl,
            Math.max(0L, elapsedMillis),
            false,
            null,
            null,
            null,
            null,
            errorMessage
        );
    }
}
