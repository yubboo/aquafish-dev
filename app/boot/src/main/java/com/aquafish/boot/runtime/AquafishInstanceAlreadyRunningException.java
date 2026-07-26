package com.aquafish.boot.runtime;

import java.nio.file.Path;

/**
 * 同一个 workdir 已经被另一个 Aquafish 主进程占用。
 *
 * <p>
 * 该异常只处理 standalone 部署的运行实例冲突，
 * 不代表系统正在执行首次安装。
 * </p>
 */
public class AquafishInstanceAlreadyRunningException
    extends IllegalStateException {

    /**
     * 被占用的 workdir。
     */
    private final Path workDir;

    /**
     * 操作系统锁载体路径。
     */
    private final Path lockFile;

    /**
     * 当前锁文件中的诊断信息。
     */
    private final String ownerInformation;

    /**
     * 创建实例占用异常。
     *
     * @param workDir 工作目录
     * @param lockFile 锁载体文件
     * @param ownerInformation 占用进程信息
     */
    public AquafishInstanceAlreadyRunningException(
        Path workDir,
        Path lockFile,
        String ownerInformation
    ) {
        super(
            createMessage(
                workDir,
                lockFile,
                ownerInformation
            )
        );

        this.workDir = workDir;
        this.lockFile = lockFile;
        this.ownerInformation =
            normalizeOwnerInformation(
                ownerInformation
            );
    }

    /**
     * 获取被占用的工作目录。
     *
     * @return workdir
     */
    public Path workDir() {
        return workDir;
    }

    /**
     * 获取锁载体文件。
     *
     * @return 锁文件
     */
    public Path lockFile() {
        return lockFile;
    }

    /**
     * 获取诊断信息。
     *
     * @return 上一个或当前进程写入的信息
     */
    public String ownerInformation() {
        return ownerInformation;
    }

    /**
     * 创建安全错误说明。
     */
    private static String createMessage(
        Path workDir,
        Path lockFile,
        String ownerInformation
    ) {
        return "当前 Aquafish workdir 已经被另一个主进程占用："
            + workDir
            + "。锁载体："
            + lockFile
            + "。占用信息："
            + normalizeOwnerInformation(
                ownerInformation
            );
    }

    /**
     * 标准化诊断信息。
     */
    private static String normalizeOwnerInformation(
        String ownerInformation
    ) {
        if (
            ownerInformation == null
            || ownerInformation.isBlank()
        ) {
            return "无法读取占用进程信息";
        }

        return ownerInformation.trim();
    }
}
