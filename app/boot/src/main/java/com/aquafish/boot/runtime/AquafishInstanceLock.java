package com.aquafish.boot.runtime;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Aquafish standalone 单实例运行锁。
 *
 * <p>
 * 一个 workdir 同一时刻只允许一个 Aquafish 主进程持有。
 * </p>
 *
 * <p>锁文件位置：</p>
 *
 * <pre>
 * workdir/storage/locks/aquafish-instance.lock
 * </pre>
 *
 * <p>
 * 文件是否存在不代表进程正在运行。
 * 真正的互斥依据是操作系统 FileLock。
 * </p>
 */
public final class AquafishInstanceLock
    implements AutoCloseable {

    /**
     * 锁文件最多读取的诊断信息字节数。
     */
    private static final int
        MAX_METADATA_BYTES = 8192;

    /**
     * 规范化 workdir。
     */
    private final Path workDir;

    /**
     * 操作系统锁载体。
     */
    private final Path lockFile;

    /**
     * 锁文件通道。
     */
    private final FileChannel fileChannel;

    /**
     * 操作系统排他锁。
     */
    private final FileLock fileLock;

    /**
     * 当前进程写入的诊断信息。
     */
    private final String metadata;

    /**
     * 是否已经释放。
     */
    private boolean closed;

    /**
     * 创建已取得的实例锁。
     */
    private AquafishInstanceLock(
        Path workDir,
        Path lockFile,
        FileChannel fileChannel,
        FileLock fileLock,
        String metadata
    ) {
        this.workDir = workDir;
        this.lockFile = lockFile;
        this.fileChannel = fileChannel;
        this.fileLock = fileLock;
        this.metadata = metadata;
    }

    /**
     * 取得指定 workdir 的 standalone 实例锁。
     *
     * @param requestedWorkDir Aquafish 工作目录
     * @return 已取得的实例锁
     * @throws AquafishInstanceAlreadyRunningException
     *         同一个 workdir 已被另一个进程占用
     */
    public static AquafishInstanceLock acquire(
        Path requestedWorkDir
    ) {
        Path normalizedWorkDir =
            normalizeWorkDir(
                requestedWorkDir
            );

        Path locksDirectory =
            normalizedWorkDir
                .resolve("storage")
                .resolve("locks")
                .toAbsolutePath()
                .normalize();

        Path lockFile =
            locksDirectory
                .resolve(
                    "aquafish-instance.lock"
                )
                .toAbsolutePath()
                .normalize();

        validateLockPath(
            normalizedWorkDir,
            locksDirectory,
            lockFile
        );

        FileChannel channel = null;

        try {
            Files.createDirectories(
                locksDirectory
            );

            if (
                Files.isSymbolicLink(
                    locksDirectory
                )
                || !Files.isDirectory(
                    locksDirectory,
                    LinkOption.NOFOLLOW_LINKS
                )
            ) {
                throw new IllegalStateException(
                    "Aquafish 运行锁目录不是安全普通目录："
                        + locksDirectory
                );
            }

            channel =
                FileChannel.open(
                    lockFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE
                );

            final FileLock operatingSystemLock;

            try {
                operatingSystemLock =
                    channel.tryLock();
            } catch (
                OverlappingFileLockException
                    error
            ) {
                String ownerInformation =
                    readMetadata(channel);

                closeQuietly(channel);

                throw new AquafishInstanceAlreadyRunningException(
                    normalizedWorkDir,
                    lockFile,
                    ownerInformation
                );
            }

            if (operatingSystemLock == null) {
                String ownerInformation =
                    readMetadata(channel);

                closeQuietly(channel);

                throw new AquafishInstanceAlreadyRunningException(
                    normalizedWorkDir,
                    lockFile,
                    ownerInformation
                );
            }

            String metadata =
                createMetadata(
                    normalizedWorkDir
                );

            writeMetadata(
                channel,
                metadata
            );

            return new AquafishInstanceLock(
                normalizedWorkDir,
                lockFile,
                channel,
                operatingSystemLock,
                metadata
            );
        } catch (
            AquafishInstanceAlreadyRunningException
                error
        ) {
            throw error;
        } catch (IOException error) {
            closeQuietly(channel);

            throw new IllegalStateException(
                "创建 Aquafish standalone 实例锁失败："
                    + lockFile,
                error
            );
        } catch (RuntimeException error) {
            closeQuietly(channel);
            throw error;
        }
    }

    /**
     * 获取当前 workdir。
     *
     * @return 规范化绝对路径
     */
    public Path workDir() {
        return workDir;
    }

    /**
     * 获取锁文件路径。
     *
     * @return 锁载体路径
     */
    public Path lockFile() {
        return lockFile;
    }

    /**
     * 获取当前进程诊断信息。
     *
     * @return 元数据
     */
    public String metadata() {
        return metadata;
    }

    /**
     * 判断实例锁是否仍然有效。
     *
     * @return 当前进程仍持锁时返回 true
     */
    public synchronized boolean valid() {
        return !closed
            && fileChannel.isOpen()
            && fileLock.isValid();
    }

    /**
     * 释放 standalone 实例锁。
     *
     * <p>
     * 不删除锁载体文件，防止释放与下一进程取得锁之间
     * 出现删除同名文件的竞态。
     * 下一次成功启动会覆盖其中的诊断信息。
     * </p>
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;

        try {
            if (fileLock.isValid()) {
                fileLock.release();
            }
        } catch (IOException ignored) {
            /*
             * 文件通道关闭时仍会尝试释放操作系统锁。
             */
        }

        closeQuietly(fileChannel);
    }

    /**
     * 标准化 workdir。
     */
    private static Path normalizeWorkDir(
        Path requestedWorkDir
    ) {
        if (requestedWorkDir == null) {
            throw new IllegalArgumentException(
                "Aquafish workdir 不能为空。"
            );
        }

        return requestedWorkDir
            .toAbsolutePath()
            .normalize();
    }

    /**
     * 检查锁文件必须位于当前 workdir 内。
     */
    private static void validateLockPath(
        Path workDir,
        Path locksDirectory,
        Path lockFile
    ) {
        if (
            !locksDirectory.startsWith(
                workDir
            )
            || !lockFile.startsWith(
                locksDirectory
            )
            || lockFile.getParent() == null
            || !lockFile
                .getParent()
                .equals(locksDirectory)
        ) {
            throw new IllegalStateException(
                "Aquafish 实例锁路径越界："
                    + lockFile
            );
        }
    }

    /**
     * 创建当前进程诊断信息。
     */
    private static String createMetadata(
        Path workDir
    ) {
        return "pid="
            + ProcessHandle
                .current()
                .pid()
            + System.lineSeparator()
            + "startedAt="
            + Instant.now()
            + System.lineSeparator()
            + "workDir="
            + workDir
            + System.lineSeparator();
    }

    /**
     * 覆盖写入当前进程诊断信息。
     */
    private static void writeMetadata(
        FileChannel channel,
        String metadata
    ) throws IOException {
        byte[] bytes =
            metadata.getBytes(
                StandardCharsets.UTF_8
            );

        channel.truncate(0);
        channel.position(0);

        ByteBuffer buffer =
            ByteBuffer.wrap(bytes);

        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }

        channel.force(true);
    }

    /**
     * 读取锁载体中的诊断信息。
     */
    private static String readMetadata(
        FileChannel channel
    ) {
        if (
            channel == null
            || !channel.isOpen()
        ) {
            return "";
        }

        try {
            long originalPosition =
                channel.position();

            channel.position(0);

            int readableBytes =
                (int) Math.min(
                    Math.max(
                        channel.size(),
                        0
                    ),
                    MAX_METADATA_BYTES
                );

            if (readableBytes == 0) {
                channel.position(
                    originalPosition
                );

                return "";
            }

            ByteBuffer buffer =
                ByteBuffer.allocate(
                    readableBytes
                );

            while (
                buffer.hasRemaining()
                && channel.read(buffer) >= 0
            ) {
                // 持续读取，直到缓冲区填满或到达文件末尾。
            }

            channel.position(
                originalPosition
            );

            buffer.flip();

            return StandardCharsets.UTF_8
                .decode(buffer)
                .toString()
                .trim();
        } catch (IOException error) {
            return "";
        }
    }

    /**
     * 安静关闭文件通道。
     */
    private static void closeQuietly(
        FileChannel channel
    ) {
        if (channel == null) {
            return;
        }

        try {
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException ignored) {
            /*
             * 关闭失败不覆盖真正的启动错误。
             */
        }
    }
}
