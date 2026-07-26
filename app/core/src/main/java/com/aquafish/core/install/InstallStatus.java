package com.aquafish.core.install;

/**
 * Aquafish 文件兼容状态。
 *
 * <p>该模型只描述工作目录和 install.lock 文件，不能用于判断系统是否
 * 已经完成安装。权威安装状态必须通过 {@link AuthoritativeInstallStatusService}
 * 从数据库 system_instances 读取。</p>
 */
public record InstallStatus(
    boolean installed,
    boolean locked,
    boolean canInstall,
    String workDir,
    String lockFile,
    String applicationConfigFile,
    boolean workDirExists,
    boolean workDirWritable,
    boolean storageDirExists,
    boolean storageDirWritable,
    boolean themesDirExists,
    boolean pluginsDirExists,
    boolean applicationConfigExists,
    String installedAt,
    String lockSummary
) {
}
