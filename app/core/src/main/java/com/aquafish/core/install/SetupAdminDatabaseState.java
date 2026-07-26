package com.aquafish.core.install;

/**
 * 安装管理员创建前的数据库只读检查结果。
 */
public record SetupAdminDatabaseState(
    boolean coreTablesReady,
    boolean initializing,
    boolean adminExists
) {
}
