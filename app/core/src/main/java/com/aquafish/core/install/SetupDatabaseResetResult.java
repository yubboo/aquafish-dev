package com.aquafish.core.install;

/**
 * 安装向导精确清理结果。
 */
public record SetupDatabaseResetResult(
    boolean reset,
    SetupDatabaseMode previousMode,
    SetupDatabaseMode currentMode,
    int processedTableCount,
    String databaseName,
    String tablePrefix,
    String message
) {
}
