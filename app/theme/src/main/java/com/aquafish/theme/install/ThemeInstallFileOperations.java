package com.aquafish.theme.install;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import org.springframework.stereotype.Component;

/**
 * 主题安装文件系统基础操作。
 *
 * <p>
 * 当前第 43-2 步只提供：
 * </p>
 *
 * <ul>
 *     <li>路径边界判断；</li>
 *     <li>不跟随符号链接的递归删除。</li>
 * </ul>
 *
 * <p>
 * 原子移动将在后续正式提交阶段加入。
 * </p>
 */
@Component
public class ThemeInstallFileOperations {

    /**
     * 判断目标路径是否位于指定根目录内。
     *
     * @param rootDirectory 根目录
     * @param candidatePath 待检查路径
     * @return 位于根目录本身或其内部时返回 true
     */
    public boolean isWithin(
        Path rootDirectory,
        Path candidatePath
    ) {
        if (
            rootDirectory == null
                || candidatePath == null
        ) {
            return false;
        }

        Path normalizedRoot =
            rootDirectory
                .toAbsolutePath()
                .normalize();

        Path normalizedCandidate =
            candidatePath
                .toAbsolutePath()
                .normalize();

        return normalizedCandidate.startsWith(
            normalizedRoot
        );
    }

    /**
     * 把临时主题目录移动到正式主题目录。
     *
     * <p>
     * 永远不使用 REPLACE_EXISTING，
     * 因此该方法不能覆盖任何已有主题。
     * </p>
     *
     * <p>
     * 首先尝试 ATOMIC_MOVE。
     * 文件系统不支持时：
     * </p>
     *
     * <ul>
     *     <li>严格策略：抛出 AtomicMoveNotSupportedException；</li>
     *     <li>默认策略：使用普通同文件系统移动安全降级。</li>
     * </ul>
     *
     * @param sourceDirectory 临时主题目录
     * @param targetDirectory 正式主题目录
     * @param requireAtomicMove 是否强制原子移动
     * @return 实际使用原子移动时返回 true
     * @throws IOException 移动失败
     */
    public boolean moveDirectory(
        Path sourceDirectory,
        Path targetDirectory,
        boolean requireAtomicMove
    ) throws IOException {

        if (
            sourceDirectory == null
                || targetDirectory == null
        ) {
            throw new IllegalArgumentException(
                "主题移动源目录和目标目录不能为空。"
            );
        }

        Path normalizedSource =
            sourceDirectory
                .toAbsolutePath()
                .normalize();

        Path normalizedTarget =
            targetDirectory
                .toAbsolutePath()
                .normalize();

        if (
            Files.isSymbolicLink(
                normalizedSource
            )
                || !Files.isDirectory(
                    normalizedSource,
                    LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw new IOException(
                "主题移动源不是安全普通目录："
                    + normalizedSource
            );
        }

        if (
            Files.exists(
                normalizedTarget,
                LinkOption.NOFOLLOW_LINKS
            )
        ) {
            throw new FileAlreadyExistsException(
                normalizedTarget.toString()
            );
        }

        try {
            Files.move(
                normalizedSource,
                normalizedTarget,
                StandardCopyOption.ATOMIC_MOVE
            );

            return true;
        } catch (
            AtomicMoveNotSupportedException
                error
        ) {
            if (requireAtomicMove) {
                throw error;
            }

            Files.move(
                normalizedSource,
                normalizedTarget
            );

            return false;
        }
    }

    /**
     * 递归删除文件或目录。
     *
     * <p>
     * Files.walkFileTree 默认不跟随符号链接，
     * 因此即使目录中意外出现符号链接，
     * 也只会删除链接本身，不会进入链接目标。
     * </p>
     *
     * @param target 待删除目标
     * @throws IOException 删除失败
     */
    public void deleteRecursively(
        Path target
    ) throws IOException {

        if (target == null) {
            return;
        }

        Path normalizedTarget =
            target
                .toAbsolutePath()
                .normalize();

        if (
            !Files.exists(
                normalizedTarget,
                LinkOption.NOFOLLOW_LINKS
            )
        ) {
            return;
        }

        Files.walkFileTree(
            normalizedTarget,
            new SimpleFileVisitor<Path>() {

                /**
                 * 删除普通文件和符号链接。
                 */
                @Override
                public FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attributes
                ) throws IOException {

                    Files.deleteIfExists(file);

                    return FileVisitResult.CONTINUE;
                }

                /**
                 * 某些损坏链接读取属性时会进入该回调。
                 */
                @Override
                public FileVisitResult visitFileFailed(
                    Path file,
                    IOException error
                ) throws IOException {

                    if (
                        Files.isSymbolicLink(file)
                    ) {
                        Files.deleteIfExists(file);

                        return FileVisitResult.CONTINUE;
                    }

                    throw error;
                }

                /**
                 * 子文件删除完成后删除目录本身。
                 */
                @Override
                public FileVisitResult
                    postVisitDirectory(
                        Path directory,
                        IOException error
                    ) throws IOException {

                    if (error != null) {
                        throw error;
                    }

                    Files.deleteIfExists(directory);

                    return FileVisitResult.CONTINUE;
                }
            }
        );
    }
}
