package com.aquafish.boot.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Aquafish standalone 单实例锁测试。
 */
class AquafishInstanceLockTest {

    /**
     * 每个测试使用独立 workdir。
     */
    @TempDir
    Path temporaryDirectory;

    /**
     * 同一个 workdir 不能同时取得两次实例锁。
     */
    @Test
    void shouldRejectSecondInstanceForSameWorkDir() {
        Path workDir =
            temporaryDirectory.resolve(
                "same-workdir"
            );

        try (
            AquafishInstanceLock firstLock =
                AquafishInstanceLock.acquire(
                    workDir
                )
        ) {
            assertTrue(firstLock.valid());

            AquafishInstanceAlreadyRunningException
                error =
                    assertThrows(
                        AquafishInstanceAlreadyRunningException.class,
                        () ->
                            AquafishInstanceLock
                                .acquire(workDir)
                    );

            assertEquals(
                workDir
                    .toAbsolutePath()
                    .normalize(),
                error.workDir()
            );

            assertTrue(
                error.getMessage().contains(
                    "已经被另一个主进程占用"
                )
            );
        }
    }

    /**
     * 第一个进程释放后，同一 workdir 可以重新启动。
     */
    @Test
    void shouldAcquireAgainAfterPreviousInstanceStops() {
        Path workDir =
            temporaryDirectory.resolve(
                "restart-workdir"
            );

        AquafishInstanceLock firstLock =
            AquafishInstanceLock.acquire(
                workDir
            );

        assertTrue(firstLock.valid());

        firstLock.close();

        try (
            AquafishInstanceLock secondLock =
                AquafishInstanceLock.acquire(
                    workDir
                )
        ) {
            assertTrue(secondLock.valid());

            assertEquals(
                firstLock.lockFile(),
                secondLock.lockFile()
            );
        }
    }

    /**
     * 锁文件必须位于 storage/locks，
     * 并保存当前进程和 workdir 的诊断信息。
     *
     * @throws Exception 文件读取失败
     */
    @Test
    void shouldWriteRuntimeMetadataUnderStorageLocks()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "metadata-workdir"
            );

        Path expectedLockFile =
            workDir
                .resolve("storage")
                .resolve("locks")
                .resolve(
                    "aquafish-instance.lock"
                )
                .toAbsolutePath()
                .normalize();

        String expectedPid =
            "pid="
                + ProcessHandle
                    .current()
                    .pid();

        String expectedWorkDir =
            "workDir="
                + workDir
                    .toAbsolutePath()
                    .normalize();

        /*
         * Windows 强制锁兼容：
         *
         * 持有排他 FileLock 时，不使用第二个文件通道
         * 打开同一个锁载体文件。
         */
        try (
            AquafishInstanceLock instanceLock =
                AquafishInstanceLock.acquire(
                    workDir
                )
        ) {
            assertEquals(
                expectedLockFile,
                instanceLock.lockFile()
            );

            assertTrue(
                Files.isRegularFile(
                    expectedLockFile
                )
            );

            String activeMetadata =
                instanceLock.metadata();

            assertTrue(
                activeMetadata.contains(
                    expectedPid
                )
            );

            assertTrue(
                activeMetadata.contains(
                    "startedAt="
                )
            );

            assertTrue(
                activeMetadata.contains(
                    expectedWorkDir
                )
            );
        }

        /*
         * 操作系统锁释放以后再从磁盘读取，
         * 验证元数据确实已经持久化。
         */
        String persistedContent =
            Files.readString(
                expectedLockFile,
                StandardCharsets.UTF_8
            );

        assertTrue(
            persistedContent.contains(
                expectedPid
            )
        );

        assertTrue(
            persistedContent.contains(
                "startedAt="
            )
        );

        assertTrue(
            persistedContent.contains(
                expectedWorkDir
            )
        );
    }

    /**
     * 只有遗留文件、没有操作系统锁时，
     * 不能误判为已有进程运行。
     *
     * @throws Exception 文件创建失败
     */
    @Test
    void shouldIgnoreStaleFileWithoutOperatingSystemLock()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "stale-file-workdir"
            );

        Path lockFile =
            workDir
                .resolve("storage")
                .resolve("locks")
                .resolve(
                    "aquafish-instance.lock"
                );

        Files.createDirectories(
            lockFile.getParent()
        );

        Files.writeString(
            lockFile,
            "pid=999999\nstartedAt=old\n",
            StandardCharsets.UTF_8
        );

        String currentPid =
            "pid="
                + ProcessHandle
                    .current()
                    .pid();

        /*
         * 文件存在但没有真实操作系统锁，
         * 不能误判为另一个 Aquafish 正在运行。
         */
        try (
            AquafishInstanceLock instanceLock =
                AquafishInstanceLock.acquire(
                    workDir
                )
        ) {
            assertTrue(
                instanceLock.valid()
            );

            /*
             * Windows 强制锁兼容：
             * 持锁期间直接检查实例锁保存的元数据。
             */
            assertTrue(
                instanceLock
                    .metadata()
                    .contains(
                        currentPid
                    )
            );
        }

        /*
         * 释放锁后读取磁盘，确认旧 PID 已被覆盖。
         */
        String persistedContent =
            Files.readString(
                lockFile,
                StandardCharsets.UTF_8
            );

        assertTrue(
            persistedContent.contains(
                currentPid
            )
        );

        assertTrue(
            !persistedContent.contains(
                "pid=999999"
            )
        );
    }

    /**
     * 不同 workdir 可以分别运行独立站点。
     */
    @Test
    void shouldAllowDifferentWorkDirectories() {
        Path firstWorkDir =
            temporaryDirectory.resolve(
                "site-a"
            );

        Path secondWorkDir =
            temporaryDirectory.resolve(
                "site-b"
            );

        try (
            AquafishInstanceLock firstLock =
                AquafishInstanceLock.acquire(
                    firstWorkDir
                );
            AquafishInstanceLock secondLock =
                AquafishInstanceLock.acquire(
                    secondWorkDir
                )
        ) {
            assertTrue(firstLock.valid());
            assertTrue(secondLock.valid());

            assertTrue(
                !firstLock
                    .lockFile()
                    .equals(
                        secondLock.lockFile()
                    )
            );
        }
    }
}
