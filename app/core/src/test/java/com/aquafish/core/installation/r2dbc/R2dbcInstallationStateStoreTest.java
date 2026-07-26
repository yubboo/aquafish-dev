package com.aquafish.core.installation.r2dbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.r2dbc.RuntimeR2dbcConnectionFactory;
import com.aquafish.core.installation.InitializationClaim;
import com.aquafish.core.installation.InitializationClaimStatus;
import com.aquafish.core.installation.InstallationState;
import com.aquafish.core.installation.SystemInstallationRecord;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * R2DBC 安装状态仓库单元测试。
 */
class R2dbcInstallationStateStoreTest {

    @Test
    void shouldRemainLazyBeforeSubscription() {
        DatabaseRuntimeSettingsService
            settingsService =
            mock(
                DatabaseRuntimeSettingsService.class
            );

        RuntimeR2dbcConnectionFactory
            connectionFactory =
            mock(
                RuntimeR2dbcConnectionFactory.class
            );

        R2dbcInstallationStateStore store =
            new R2dbcInstallationStateStore(
                settingsService,
                connectionFactory
            );

        DatabaseSettings settings =
            DatabaseSettings
                .defaultMysql()
                .normalized();

        Mono<?> read =
            store.read(settings);

        Mono<?> claim =
            store.tryStartInitialization(
                settings,
                UUID.randomUUID(),
                Instant.now()
            );

        assertNotNull(read);
        assertNotNull(claim);

        verifyNoInteractions(
            settingsService,
            connectionFactory
        );
    }

    @Test
    void shouldNotRefreshSharedPoolWhenStateReadsOverlap() {
        DatabaseRuntimeSettingsService
            settingsService =
            mock(
                DatabaseRuntimeSettingsService.class
            );

        RuntimeR2dbcConnectionFactory
            connectionFactory =
            mock(
                RuntimeR2dbcConnectionFactory.class
            );

        when(
            connectionFactory.getMetadata()
        ).thenReturn(
            () -> "MySQL"
        );

        when(
            connectionFactory.create()
        ).thenReturn(
            Mono.error(
                new IllegalStateException(
                    "测试主动停止在数据库连接阶段。"
                )
            )
        );

        R2dbcInstallationStateStore store =
            new R2dbcInstallationStateStore(
                settingsService,
                connectionFactory
            );

        DatabaseSettings settings =
            DatabaseSettings
                .defaultMysql()
                .normalized();

        StepVerifier
            .create(
                Mono.zip(
                    store.read(settings),
                    store.read(settings)
                )
            )
            .assertNext(
                snapshots -> {
                    assertFalse(
                        snapshots
                            .getT1()
                            .databaseReachable()
                    );

                    assertFalse(
                        snapshots
                            .getT2()
                            .databaseReachable()
                    );
                }
            )
            .verifyComplete();

        verify(
            settingsService,
            times(2)
        ).useForInstallation(
            settings
        );

        verify(
            connectionFactory,
            never()
        ).refresh();
    }

    @Test
    void shouldCreateFirstInitializingRecord() {
        UUID attemptId =
            UUID.randomUUID();

        Instant startedAt =
            Instant.parse(
                "2026-07-15T00:00:00Z"
            );

        SystemInstallationRecord record =
            R2dbcInstallationStateStore
                .newInitializingRecord(
                    attemptId,
                    startedAt
                );

        assertEquals(
            InstallationState.INITIALIZING,
            record.state()
        );

        assertEquals(
            1,
            record.stateVersion()
        );

        assertEquals(
            attemptId,
            record.initializationAttemptId()
        );

        assertEquals(
            startedAt,
            record.initializationStartedAt()
        );

        assertEquals(
            startedAt,
            record.createdAt()
        );

        assertEquals(
            startedAt,
            record.updatedAt()
        );

        assertNull(
            record.installedAt()
        );

        assertNull(
            record.lastErrorCode()
        );
    }

    @Test
    void shouldRestartFailedRecordWithNewFence() {
        UUID previousAttempt =
            UUID.randomUUID();

        UUID nextAttempt =
            UUID.randomUUID();

        Instant createdAt =
            Instant.parse(
                "2026-07-15T00:00:00Z"
            );

        Instant restartedAt =
            Instant.parse(
                "2026-07-15T00:10:00Z"
            );

        SystemInstallationRecord failed =
            new SystemInstallationRecord(
                (short) 1,
                UUID.randomUUID(),
                InstallationState.FAILED,
                7,
                previousAttempt,
                createdAt,
                null,
                null,
                "INSTALLATION_FAILED",
                "旧尝试失败",
                createdAt,
                createdAt.plusSeconds(60)
            );

        SystemInstallationRecord restarted =
            R2dbcInstallationStateStore
                .restartInitializingRecord(
                    failed,
                    nextAttempt,
                    restartedAt
                );

        assertEquals(
            InstallationState.INITIALIZING,
            restarted.state()
        );

        assertEquals(
            8,
            restarted.stateVersion()
        );

        assertEquals(
            nextAttempt,
            restarted.initializationAttemptId()
        );

        assertEquals(
            restartedAt,
            restarted.initializationStartedAt()
        );

        assertSame(
            failed.instanceId(),
            restarted.instanceId()
        );

        assertEquals(
            failed.createdAt(),
            restarted.createdAt()
        );

        assertNull(
            restarted.lastErrorCode()
        );

        assertNull(
            restarted.lastErrorMessage()
        );
    }

    @Test
    void shouldReturnAlreadyInitializingClaim() {
        UUID attemptId =
            UUID.randomUUID();

        Instant now =
            Instant.now();

        SystemInstallationRecord current =
            new SystemInstallationRecord(
                (short) 1,
                UUID.randomUUID(),
                InstallationState.INITIALIZING,
                2,
                attemptId,
                now,
                null,
                null,
                null,
                null,
                now,
                now
            );

        InitializationClaim claim =
            R2dbcInstallationStateStore
                .stableClaim(
                    current
                );

        assertNotNull(claim);

        assertEquals(
            InitializationClaimStatus
                .ALREADY_INITIALIZING,
            claim.status()
        );

        assertEquals(
            attemptId,
            claim.attemptId()
        );
    }

    @Test
    void shouldReturnAlreadyInstalledClaim() {
        UUID attemptId =
            UUID.randomUUID();

        Instant now =
            Instant.now();

        SystemInstallationRecord installed =
            new SystemInstallationRecord(
                (short) 1,
                UUID.randomUUID(),
                InstallationState.INSTALLED,
                3,
                attemptId,
                now.minusSeconds(60),
                now,
                "0.0.1-dev",
                null,
                null,
                now.minusSeconds(60),
                now
            );

        InitializationClaim claim =
            R2dbcInstallationStateStore
                .stableClaim(
                    installed
                );

        assertNotNull(claim);

        assertEquals(
            InitializationClaimStatus
                .ALREADY_INSTALLED,
            claim.status()
        );
    }

    @Test
    void shouldRequireDatabaseWriteForFailedState() {
        Instant now =
            Instant.now();

        SystemInstallationRecord failed =
            new SystemInstallationRecord(
                (short) 1,
                UUID.randomUUID(),
                InstallationState.FAILED,
                2,
                UUID.randomUUID(),
                now,
                null,
                null,
                "FAILED",
                "失败",
                now,
                now
            );

        assertNull(
            R2dbcInstallationStateStore
                .stableClaim(
                    failed
                )
        );
    }

    @Test
    void shouldBuildMysqlAndPostgresqlTableChecks() {
        String mysql =
            R2dbcInstallationStateStore
                .tableExistsSql(
                    DatabaseType.MYSQL
                );

        String postgresql =
            R2dbcInstallationStateStore
                .tableExistsSql(
                    DatabaseType.POSTGRESQL
                );

        assertTrue(
            mysql.contains(
                "DATABASE()"
            )
        );

        assertTrue(
            postgresql.contains(
                "current_schema()"
            )
        );

        assertTrue(
            mysql.contains(
                ":tableName"
            )
        );
    }

    @Test
    void shouldDetectDuplicateKeyException() {
        assertTrue(
            R2dbcInstallationStateStore
                .isDuplicateKey(
                    new DuplicateKeyException(
                        "duplicate"
                    )
                )
        );

        assertTrue(
            R2dbcInstallationStateStore
                .isDuplicateKey(
                    new IllegalStateException(
                        "Duplicate entry for key PRIMARY"
                    )
                )
        );

        assertFalse(
            R2dbcInstallationStateStore
                .isDuplicateKey(
                    new IllegalStateException(
                        "connection timeout"
                    )
                )
        );
    }

    @Test
    void shouldConvertDatabaseTimeWithoutLocalTimezone() {
        Instant expected =
            Instant.parse(
                "2026-07-15T00:00:00Z"
            );

        LocalDateTime databaseValue =
            LocalDateTime.ofInstant(
                expected,
                ZoneOffset.UTC
            );

        assertEquals(
            expected,
            R2dbcInstallationStateStore
                .instantValue(
                    databaseValue,
                    "created_at"
                )
        );

        assertEquals(
            databaseValue,
            R2dbcInstallationStateStore
                .databaseTime(
                    expected
                )
        );
    }

    @Test
    void shouldConvertUuidStoredAsVarchar() {
        UUID expected =
            UUID.randomUUID();

        assertEquals(
            expected,
            R2dbcInstallationStateStore
                .uuidValue(
                    expected.toString(),
                    "instance_id"
                )
        );
    }

    @Test
    void shouldKeepCompletedTransitionsLazyBeforeSubscription() {
        DatabaseRuntimeSettingsService
            settingsService =
            mock(
                DatabaseRuntimeSettingsService.class
            );

        RuntimeR2dbcConnectionFactory
            connectionFactory =
            mock(
                RuntimeR2dbcConnectionFactory.class
            );

        R2dbcInstallationStateStore store =
            new R2dbcInstallationStateStore(
                settingsService,
                connectionFactory
            );

        Mono<?> installed =
            store.markInstalled(
                DatabaseSettings.defaultMysql(),
                UUID.randomUUID(),
                Instant.now(),
                "0.0.1-dev"
            );

        Mono<?> failed =
            store.markFailed(
                DatabaseSettings.defaultMysql(),
                UUID.randomUUID(),
                Instant.now(),
                "TEST_FAILURE",
                "测试失败摘要"
            );

        assertNotNull(installed);
        assertNotNull(failed);

        verifyNoInteractions(
            settingsService,
            connectionFactory
        );
    }
}
