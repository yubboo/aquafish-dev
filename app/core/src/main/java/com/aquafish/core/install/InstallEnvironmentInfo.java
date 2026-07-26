package com.aquafish.core.install;

/**
 * Aquafish 安装环境信息。
 *
 * 当前阶段：
 * Step 17-22-2：安装状态与 install.lock。
 *
 * 作用：
 * 1. 给安装向导展示环境检查结果；
 * 2. 检查 Java、OS、workdir、storage、themes、plugins 等基础信息；
 * 3. 后续可以扩展磁盘空间、目录权限、数据库驱动、主题目录等检查。
 */
public record InstallEnvironmentInfo(
    String javaVersion,
    String javaVendor,
    String javaHome,
    String osName,
    String osVersion,
    String osArch,
    String userDir,
    String workDir,
    String storageDir,
    String themesDir,
    String pluginsDir,
    String backupsDir,
    String lockFile,
    boolean workDirExists,
    boolean workDirWritable,
    boolean storageDirExists,
    boolean storageDirWritable,
    boolean themesDirExists,
    boolean pluginsDirExists,
    boolean backupsDirExists,
    boolean lockFileExists
) {
}
