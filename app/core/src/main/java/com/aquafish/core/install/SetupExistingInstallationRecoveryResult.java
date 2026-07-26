package com.aquafish.core.install;

/**
 * 已安装 Aquafish 恢复结果。
 */
public record SetupExistingInstallationRecoveryResult(
    boolean recovered,
    String applicationConfigFile,
    String installLockFile,
    String installedAt,
    String databaseName,
    String tablePrefix,
    String message
) {
}
