package com.aquafish.core.install;

/**
 * 由数据库安装状态计算出的权威安装结果。
 */
public record AuthoritativeInstallStatus(
    boolean installed,
    boolean locked,
    boolean canInstall,
    boolean stateAvailable,
    String databaseState,
    boolean applicationConfigExists,
    String installedAt,
    String safeMessage
) {
}
