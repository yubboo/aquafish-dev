package com.aquafish.core.database;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Aquafish 数据库类型。
 *
 * 当前阶段：
 * Step 17-22-1：数据库安装配置与连接测试底层。
 *
 * 当前支持：
 * 1. mysql：MySQL 8.x；
 * 2. mariadb：MariaDB；
 * 3. postgresql：PostgreSQL 14+。
 */
public enum DatabaseType {

    MYSQL("mysql", 3306),

    MARIADB("mariadb", 3306),

    POSTGRESQL("postgresql", 5432);

    private final String value;

    private final int defaultPort;

    DatabaseType(String value, int defaultPort) {
        this.value = value;
        this.defaultPort = defaultPort;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public int defaultPort() {
        return defaultPort;
    }

    @JsonCreator
    public static DatabaseType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return MYSQL;
        }

        String normalized = value.trim().toLowerCase();

        if ("mysql".equals(normalized)) {
            return MYSQL;
        }

        if ("mariadb".equals(normalized)) {
            return MARIADB;
        }

        if (
            "postgresql".equals(normalized) ||
            "postgres".equals(normalized) ||
            "pg".equals(normalized)
        ) {
            return POSTGRESQL;
        }

        throw new IllegalArgumentException("不支持的数据库类型：" + value);
    }
}
