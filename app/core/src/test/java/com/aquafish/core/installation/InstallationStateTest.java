package com.aquafish.core.installation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Aquafish 安装状态模型测试。
 */
class InstallationStateTest {

    /**
     * 正常安装状态流转。
     */
    @Test
    void shouldAllowNormalInstallationTransitions() {
        assertTrue(
            InstallationState
                .UNINITIALIZED
                .allowsTransitionTo(
                    InstallationState
                        .INITIALIZING
                )
        );

        assertTrue(
            InstallationState
                .INITIALIZING
                .allowsTransitionTo(
                    InstallationState
                        .INSTALLED
                )
        );

        assertTrue(
            InstallationState
                .INITIALIZING
                .allowsTransitionTo(
                    InstallationState
                        .FAILED
                )
        );

        assertTrue(
            InstallationState
                .FAILED
                .allowsTransitionTo(
                    InstallationState
                        .INITIALIZING
                )
        );
    }

    /**
     * 已安装状态不能重新进入初始化。
     */
    @Test
    void shouldKeepInstalledStateTerminal() {
        assertFalse(
            InstallationState
                .INSTALLED
                .allowsTransitionTo(
                    InstallationState
                        .INITIALIZING
                )
        );

        assertFalse(
            InstallationState
                .INSTALLED
                .canStartInitialization()
        );

        assertTrue(
            InstallationState
                .INSTALLED
                .installed()
        );
    }

    /**
     * 状态解析应忽略大小写和两侧空格。
     */
    @Test
    void shouldParseDatabaseStateSafely() {
        assertEquals(
            InstallationState.INSTALLED,
            InstallationState
                .fromDatabaseValue(
                    " installed "
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                InstallationState
                    .fromDatabaseValue(
                        "BROKEN"
                    )
        );
    }

    /**
     * INITIALIZING 记录必须包含尝试 ID 和开始时间。
     */
    @Test
    void shouldRequireInitializationAttemptMetadata() {
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SystemInstallationRecord(
                    (short) 1,
                    UUID.randomUUID(),
                    InstallationState
                        .INITIALIZING,
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Instant.now(),
                    Instant.now()
                )
        );
    }

    /**
     * INSTALLED 记录必须包含安装时间和版本。
     */
    @Test
    void shouldRequireInstalledMetadata() {
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SystemInstallationRecord(
                    (short) 1,
                    UUID.randomUUID(),
                    InstallationState
                        .INSTALLED,
                    1,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Instant.now(),
                    Instant.now()
                )
        );

        SystemInstallationRecord record =
            new SystemInstallationRecord(
                (short) 1,
                UUID.randomUUID(),
                InstallationState
                    .INSTALLED,
                1,
                null,
                null,
                Instant.now(),
                "0.0.1-dev",
                null,
                null,
                Instant.now(),
                Instant.now()
            );

        assertTrue(record.installed());
        assertFalse(
            record
                .canStartInitialization()
        );
    }

    /**
     * 系统实例单例主键只能为 1。
     */
    @Test
    void shouldRejectInvalidSingletonId() {
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SystemInstallationRecord(
                    (short) 2,
                    UUID.randomUUID(),
                    InstallationState
                        .UNINITIALIZED,
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Instant.now(),
                    Instant.now()
                )
        );
    }
}
