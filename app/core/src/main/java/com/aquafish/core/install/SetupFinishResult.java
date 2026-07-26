package com.aquafish.core.install;

/**
 * 安装完成结果。
 *
 * 当前阶段：
 * Step 17-22-6：完成安装，写入 install.lock。
 */
public record SetupFinishResult(
    boolean installed,
    boolean locked,
    boolean applicationConfigUpdated,
    String workDir,
    String applicationConfigFile,
    String lockFile,
    String installedAt,
    String note
) {
}
