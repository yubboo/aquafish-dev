package com.aquafish.core.install;

/**
 * 安装完成预览。
 *
 * 当前阶段：
 * Step 17-22-6：完成安装，写入 install.lock。
 */
public record SetupFinishPreview(
    boolean installed,
    boolean connected,
    boolean coreTablesReady,
    boolean adminExists,
    boolean applicationConfigExists,
    boolean lockFileExists,
    boolean canFinish,
    String workDir,
    String applicationConfigFile,
    String lockFile,
    String note,
    String errorMessage
) {
}
