package com.aquafish.core.installation.r2dbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquafish.core.installation.InstallationState;
import com.aquafish.core.installation.InstallationStateConflictException;
import com.aquafish.core.installation.SystemInstallationRecord;
import com.aquafish.core.installation.SystemInstallationSchema;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * R2DBC 安装状态推进规则测试。
 */
class R2dbcInstallationStateTransitionTest {

    private static final Instant STARTED_AT =
        Instant.parse("2026-07-15T10:00:00Z");

    @Test
    void shouldAdvanceMatchingAttemptToInstalled() {
        UUID attemptId = UUID.randomUUID();

        SystemInstallationRecord installed =
            R2dbcInstallationStateStore.installedRecord(
                initializing(attemptId),
                attemptId,
                STARTED_AT.plusSeconds(30),
                " 0.1.0 "
            );

        assertEquals(
            InstallationState.INSTALLED,
            installed.state()
        );
        assertEquals(4L, installed.stateVersion());
        assertEquals("0.1.0", installed.installedVersion());
        assertNull(installed.lastErrorCode());
        assertNull(installed.lastErrorMessage());
    }

    @Test
    void shouldKeepInstalledRetryIdempotent() {
        UUID attemptId = UUID.randomUUID();

        SystemInstallationRecord installed =
            R2dbcInstallationStateStore.installedRecord(
                initializing(attemptId),
                attemptId,
                STARTED_AT.plusSeconds(30),
                "0.1.0"
            );

        assertSame(
            installed,
            R2dbcInstallationStateStore.installedRecord(
                installed,
                attemptId,
                STARTED_AT.plusSeconds(60),
                "0.2.0"
            )
        );
    }

    @Test
    void shouldRejectDifferentAttempt() {
        assertThrows(
            InstallationStateConflictException.class,
            () ->
                R2dbcInstallationStateStore.installedRecord(
                    initializing(UUID.randomUUID()),
                    UUID.randomUUID(),
                    STARTED_AT.plusSeconds(30),
                    "0.1.0"
                )
        );
    }

    @Test
    void shouldAdvanceMatchingAttemptToFailedSafely() {
        UUID attemptId = UUID.randomUUID();

        SystemInstallationRecord failed =
            R2dbcInstallationStateStore.failedRecord(
                initializing(attemptId),
                attemptId,
                STARTED_AT.plusSeconds(20),
                " migration failed ",
                "jdbc:mysql://root:secret@localhost/aquafish "
                    + "password=123456 token=abcdef"
            );

        assertEquals(
            InstallationState.FAILED,
            failed.state()
        );
        assertEquals(4L, failed.stateVersion());
        assertNull(failed.installedAt());
        assertNull(failed.installedVersion());
        assertEquals(
            "MIGRATION_FAILED",
            failed.lastErrorCode()
        );
        assertEquals(
            false,
            failed.lastErrorMessage().contains("secret")
        );
        assertEquals(
            false,
            failed.lastErrorMessage().contains("123456")
        );
        assertEquals(
            false,
            failed.lastErrorMessage().contains("abcdef")
        );
    }

    @Test
    void shouldRejectCompletionBeforeInitializationStart() {
        UUID attemptId = UUID.randomUUID();

        assertThrows(
            IllegalArgumentException.class,
            () ->
                R2dbcInstallationStateStore.failedRecord(
                    initializing(attemptId),
                    attemptId,
                    STARTED_AT.minusSeconds(1),
                    "FAILED",
                    "failure"
                )
        );
    }

    private static SystemInstallationRecord initializing(
        UUID attemptId
    ) {
        return new SystemInstallationRecord(
            SystemInstallationSchema.PRIMARY_SINGLETON_ID,
            UUID.randomUUID(),
            InstallationState.INITIALIZING,
            3L,
            attemptId,
            STARTED_AT,
            null,
            null,
            null,
            null,
            STARTED_AT,
            STARTED_AT
        );
    }
}
