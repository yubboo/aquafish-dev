package com.aquafish.theme.install;

import com.aquafish.core.config.WorkDirResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * 过期主题安装临时目录清理器。
 *
 * <p>
 * 只清理以下目录的直接子目录：
 * </p>
 *
 * <pre>
 * workdir/storage/temp/theme-install
 * </pre>
 *
 * <p>
 * 安装服务会在取得全局主题操作协调权后调用本组件，
 * 因此不会删除当前 JVM 中另一个正常主题操作正在使用的目录。
 * </p>
 */
@Component
public class ThemeInstallWorkspaceCleaner {

    /**
     * workdir 解析器。
     */
    private final WorkDirResolver
        workDirResolver;

    /**
     * 文件系统操作。
     */
    private final ThemeInstallFileOperations
        fileOperations;

    /**
     * 创建清理器。
     *
     * @param workDirResolver workdir 解析器
     * @param fileOperations 文件操作
     */
    public ThemeInstallWorkspaceCleaner(
        WorkDirResolver workDirResolver,
        ThemeInstallFileOperations
            fileOperations
    ) {
        if (workDirResolver == null) {
            throw new IllegalArgumentException(
                "临时目录清理器工作目录解析器不能为空。"
            );
        }

        if (fileOperations == null) {
            throw new IllegalArgumentException(
                "临时目录清理文件操作不能为空。"
            );
        }

        this.workDirResolver =
            workDirResolver;

        this.fileOperations =
            fileOperations;
    }

    /**
     * 清理超过指定年龄的安装工作目录。
     *
     * @param maximumAge 工作目录最大保留时间
     * @return 清理结果
     */
    public ThemeWorkspaceCleanupResult
        cleanupStale(
            Duration maximumAge
        ) {

        if (
            maximumAge == null
                || maximumAge.isZero()
                || maximumAge.isNegative()
        ) {
            throw new IllegalArgumentException(
                "过期工作目录保留时间必须大于 0。"
            );
        }

        workDirResolver
            .ensureBaseDirectories();

        Path installTempRoot =
            installTempRoot();

        try {
            Files.createDirectories(
                installTempRoot
            );
        } catch (IOException error) {
            return new ThemeWorkspaceCleanupResult(
                0,
                0,
                List.of(
                    "创建主题安装临时根目录失败："
                        + safeMessage(error)
                )
            );
        }

        Instant cutoff =
            Instant.now().minus(
                maximumAge
            );

        int scanned = 0;
        int deleted = 0;

        List<String> failures =
            new ArrayList<>();

        try (
            Stream<Path> entries =
                Files.list(installTempRoot)
        ) {
            List<Path> candidates =
                entries
                    .filter(
                        candidate ->
                            Files.isDirectory(
                                candidate,
                                LinkOption
                                    .NOFOLLOW_LINKS
                            )
                    )
                    .toList();

            for (Path candidate : candidates) {
                scanned++;

                final FileTime modifiedTime;

                try {
                    modifiedTime =
                        Files.getLastModifiedTime(
                            candidate,
                            LinkOption
                                .NOFOLLOW_LINKS
                        );
                } catch (IOException error) {
                    failures.add(
                        candidate
                            + "：无法读取最后修改时间："
                            + safeMessage(error)
                    );

                    continue;
                }

                if (
                    !modifiedTime
                        .toInstant()
                        .isBefore(cutoff)
                ) {
                    continue;
                }

                try {
                    fileOperations
                        .deleteRecursively(
                            candidate
                        );

                    if (
                        Files.exists(
                            candidate,
                            LinkOption
                                .NOFOLLOW_LINKS
                        )
                    ) {
                        failures.add(
                            candidate
                                + "：递归删除后目录仍然存在。"
                        );
                    } else {
                        deleted++;
                    }
                } catch (IOException error) {
                    failures.add(
                        candidate
                            + "：删除失败："
                            + safeMessage(error)
                    );
                }
            }
        } catch (IOException error) {
            failures.add(
                "读取主题安装临时目录失败："
                    + safeMessage(error)
            );
        }

        return new ThemeWorkspaceCleanupResult(
            scanned,
            deleted,
            failures
        );
    }

    /**
     * 获取主题安装临时根目录。
     *
     * @return workdir/storage/temp/theme-install
     */
    public Path installTempRoot() {
        return workDirResolver
            .tempDir()
            .resolve("theme-install")
            .toAbsolutePath()
            .normalize();
    }

    /**
     * 获取安全异常说明。
     */
    private String safeMessage(
        Throwable error
    ) {
        if (
            error == null
                || error.getMessage() == null
                || error.getMessage().isBlank()
        ) {
            return error == null
                ? "未知错误"
                : error
                    .getClass()
                    .getSimpleName();
        }

        return error.getMessage();
    }
}
