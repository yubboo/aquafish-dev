package com.aquafish.core.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Aquafish 路径解析器。
 *
 * 当前阶段：
 * Step 17-19-2：对齐 Halo 式 workdir 工作目录。
 *
 * 当前职责：
 * 1. 解析 workdir 工作目录。
 * 2. 兼容旧的 storage-path 解析方式。
 * 3. 统一处理相对路径和绝对路径。
 *
 * 为什么要有这个类：
 * Aquafish 后续要支持：
 * 1. 本地开发运行；
 * 2. JAR 部署运行；
 * 3. Docker 部署运行；
 * 4. Windows 路径；
 * 5. Linux 路径。
 *
 * 所以不能在业务代码里到处写：
 * ${user.home}/.aquafish/dev
 *
 * 必须统一通过路径解析器处理。
 */
public final class AquafishPathResolver {

    /**
     * 私有构造方法。
     *
     * 工具类不允许被 new。
     */
    private AquafishPathResolver() {
    }

    /**
     * 解析 workdir 工作目录。
     *
     * 规则：
     * 1. 如果配置是绝对路径，直接使用。
     * 2. 如果配置是相对路径，则基于当前进程启动目录解析。
     * 3. 如果配置为空，默认使用 workdir。
     *
     * 示例：
     * 配置：${user.home}/.aquafish/dev
     * 返回：当前用户目录下的 .aquafish/dev
     *
     * 配置：workdir
     * 返回：当前进程启动目录/workdir
     *
     * @param configuredWorkDir 配置里的 aquafish.work-dir
     * @return 规范化后的 workdir 绝对路径
     */
    public static Path resolveWorkDirPath(String configuredWorkDir) {
        String value = configuredWorkDir == null || configuredWorkDir.isBlank()
            ? "workdir"
            : configuredWorkDir.trim();

        return resolvePathAgainstWorkingDirectory(value);
    }

    /**
     * 解析旧 storage-path。
     *
     * 说明：
     * 这是兼容旧代码用的。
     *
     * 当前旧代码里还有：
     * aquafish.storage-path
     *
     * 后续会逐步迁移到：
     * aquafish.work-dir + /storage
     *
     * @param configuredStoragePath 配置里的 aquafish.storage-path
     * @return 规范化后的 storage 绝对路径
     */
    public static Path resolveStoragePath(String configuredStoragePath) {
        String value = configuredStoragePath == null || configuredStoragePath.isBlank()
            ? "storage"
            : configuredStoragePath.trim();

        return resolvePathAgainstWorkingDirectory(value);
    }

    /**
     * 把配置路径解析成绝对路径。
     *
     * 规则：
     * 1. 绝对路径直接返回。
     * 2. 相对路径基于当前进程启动目录。
     *
     * @param configuredPath 配置路径
     * @return 绝对路径
     */
    public static Path resolvePathAgainstWorkingDirectory(String configuredPath) {
        Path configured = Paths.get(configuredPath);

        if (configured.isAbsolute()) {
            return configured.normalize();
        }

        /*
         * 发行 JAR 可能被解压到任意位置，不能向父目录扫描源码仓库特征，
         * 否则会串读另一个实例的 application.yaml 和 install.lock。
         * 开发模式由 Gradle bootRun 显式注入用户目录下的实例 workdir，
         * 生产代码不会扫描源码仓库，也不会在仓库根目录创建运行数据。
         */
        return Paths.get("")
            .toAbsolutePath()
            .normalize()
            .resolve(configured)
            .normalize();
    }
}
