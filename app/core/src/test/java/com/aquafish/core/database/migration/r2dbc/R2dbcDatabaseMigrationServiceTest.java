package com.aquafish.core.database.migration.r2dbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.installation.InitializationClaim;
import com.aquafish.core.installation.InstallationAttemptSession;
import com.aquafish.core.installation.InstallationState;
import com.aquafish.core.installation.SystemInstallationRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 正式 R2DBC 数据库迁移编排服务测试。
 */
class R2dbcDatabaseMigrationServiceTest {

    @Test
    void shouldRemainLazyBeforeSubscription() {
        Fixture fixture =
            fixture();

        Mono<?> result =
            fixture.service.migrate(
                fixture.settings
            );

        assertNotNull(result);

        verifyNoInteractions(
            fixture.settingsService,
            fixture.inspector,
            fixture.executor,
            fixture.session
        );
    }

    @Test
    void shouldPreviewUnmanagedDatabaseAsUnsafe() {
        Fixture fixture =
            fixture();

        when(
            fixture.inspector.inspect(
                fixture.settings
            )
        ).thenReturn(
            Mono.just(
                fixture.unmanagedState()
            )
        );

        StepVerifier
            .create(
                fixture.service.preview(
                    fixture.settings
                )
            )
            .assertNext(
                preview -> {
                    assertTrue(
                        preview.connected()
                    );

                    assertFalse(
                        preview.canMigrate()
                    );

                    assertTrue(
                        preview.unmanagedDatabase()
                    );

                    assertTrue(
                        preview.requiresDatabaseReset()
                    );
                }
            )
            .verifyComplete();
    }

    @Test
    void shouldMigrateEmptyDatabaseWithR2dbc() {
        Fixture fixture =
            fixture();

        when(
            fixture.inspector.inspect(
                fixture.settings
            )
        ).thenReturn(
            Mono.just(
                fixture.emptyState()
            ),
            Mono.just(
                fixture.currentState()
            )
        );

        when(
            fixture.executor.migrate(
                fixture.settings
            )
        ).thenReturn(
            Mono.empty()
        );

        when(
            fixture.session
                .claimAfterMigration(
                    fixture.settings
                )
        ).thenReturn(
            Mono.just(
                fixture.acquiredClaim()
            )
        );

        StepVerifier
            .create(
                fixture.service.migrate(
                    fixture.settings
                )
            )
            .assertNext(
                result -> {
                    assertTrue(
                        result.migrated()
                    );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            "",
                            result.previousVersion()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            "4",
                            result.currentVersion()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            4,
                            result.pendingBefore()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            0,
                            result.pendingAfter()
                        );
                }
            )
            .verifyComplete();

        verify(fixture.executor)
            .migrate(
                fixture.settings
            );

        verify(fixture.session)
            .claimAfterMigration(
                fixture.settings
            );
    }

    @Test
    void shouldSkipSqlWhenDatabaseIsAlreadyCurrent() {
        Fixture fixture =
            fixture();

        when(
            fixture.inspector.inspect(
                fixture.settings
            )
        ).thenReturn(
            Mono.just(
                fixture.currentState()
            )
        );

        when(
            fixture.session
                .claimAfterMigration(
                    fixture.settings
                )
        ).thenReturn(
            Mono.just(
                fixture.acquiredClaim()
            )
        );

        StepVerifier
            .create(
                fixture.service.migrate(
                    fixture.settings
                )
            )
            .assertNext(
                result -> {
                    assertFalse(
                        result.migrated()
                    );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            "4",
                            result.previousVersion()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            "4",
                            result.currentVersion()
                        );
                }
            )
            .verifyComplete();

        verify(
            fixture.executor,
            never()
        ).migrate(
            any()
        );
    }

    @Test
    void shouldRejectUnmanagedNonEmptyDatabase() {
        Fixture fixture =
            fixture();

        when(
            fixture.inspector.inspect(
                fixture.settings
            )
        ).thenReturn(
            Mono.just(
                fixture.unmanagedState()
            )
        );

        StepVerifier
            .create(
                fixture.service.migrate(
                    fixture.settings
                )
            )
            .expectErrorMatches(
                error ->
                    error
                        .getMessage()
                        .contains(
                            "清空数据库"
                        )
            )
            .verify();

        verify(
            fixture.executor,
            never()
        ).migrate(
            any()
        );

        verify(
            fixture.session,
            never()
        ).claimAfterMigration(
            any()
        );
    }

    @Test
    void shouldRejectAlreadyInstalledSystem() {
        Fixture fixture =
            fixture();

        when(
            fixture.inspector.inspect(
                fixture.settings
            )
        ).thenReturn(
            Mono.just(
                fixture.currentState()
            )
        );

        when(
            fixture.session
                .claimAfterMigration(
                    fixture.settings
                )
        ).thenReturn(
            Mono.just(
                InitializationClaim
                    .alreadyInstalled(
                        fixture.installedRecord()
                    )
                )
        );

        StepVerifier
            .create(
                fixture.service.migrate(
                    fixture.settings
                )
            )
            .expectErrorMatches(
                error ->
                    error
                        .getMessage()
                        .contains(
                            "已经完成首次安装"
                        )
            )
            .verify();
    }

    @Test
    void shouldUpgradeInstalledDatabaseWithoutClaimingInstallation() {
        Fixture fixture =
            fixture();

        when(
            fixture.inspector.inspect(
                fixture.settings
            )
        ).thenReturn(
            Mono.just(
                fixture.emptyState()
            ),
            Mono.just(
                fixture.currentState()
            )
        );

        when(
            fixture.executor.migrate(
                fixture.settings
            )
        ).thenReturn(
            Mono.empty()
        );

        StepVerifier
            .create(
                fixture.service
                    .upgradeInstalledDatabase(
                        fixture.settings
                    )
            )
            .assertNext(
                result -> {
                    assertTrue(
                        result.migrated()
                    );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            "4",
                            result.currentVersion()
                        );
                }
            )
            .verifyComplete();

        verify(fixture.executor)
            .migrate(
                fixture.settings
            );

        verifyNoInteractions(
            fixture.session
        );
    }

    private Fixture fixture() {
        DatabaseRuntimeSettingsService settingsService =
            mock(
                DatabaseRuntimeSettingsService.class
            );

        R2dbcMigrationStateInspector inspector =
            mock(
                R2dbcMigrationStateInspector.class
            );

        R2dbcMigrationExecutor executor =
            mock(
                R2dbcMigrationExecutor.class
            );

        InstallationAttemptSession session =
            mock(
                InstallationAttemptSession.class
            );

        DatabaseSettings settings =
            DatabaseSettings
                .defaultMysql()
                .normalized();

        R2dbcMigrationTableNames names =
            R2dbcMigrationTableNames.from(
                settings
            );

        R2dbcMigrationCatalogSnapshot catalog =
            new R2dbcMigrationCatalogSnapshot(
                List.of(
                    entry(1),
                    entry(2),
                    entry(3),
                    entry(4)
                ),
                4
            );

        R2dbcDatabaseMigrationService service =
            new R2dbcDatabaseMigrationService(
                settingsService,
                inspector,
                executor,
                session
            );

        return new Fixture(
            settingsService,
            inspector,
            executor,
            session,
            settings,
            names,
            catalog,
            service
        );
    }

    private R2dbcMigrationCatalogEntry entry(
        long version
    ) {
        return new R2dbcMigrationCatalogEntry(
            version,
            "migration " + version,
            "V"
                + version
                + "__migration_"
                + version
                + ".sql"
        );
    }

    private record Fixture(
        DatabaseRuntimeSettingsService
            settingsService,
        R2dbcMigrationStateInspector inspector,
        R2dbcMigrationExecutor executor,
        InstallationAttemptSession session,
        DatabaseSettings settings,
        R2dbcMigrationTableNames names,
        R2dbcMigrationCatalogSnapshot catalog,
        R2dbcDatabaseMigrationService service
    ) {

        private R2dbcMigrationDatabaseState
            emptyState() {

            return state(
                0,
                true,
                false,
                List.of(),
                0,
                true,
                false,
                "空数据库"
            );
        }

        private R2dbcMigrationDatabaseState
            currentState() {

            return state(
                20,
                false,
                true,
                List.of(
                    1L,
                    2L,
                    3L,
                    4L
                ),
                4,
                true,
                false,
                "数据库已经是最新版本"
            );
        }

        private R2dbcMigrationDatabaseState
            unmanagedState() {

            return state(
                12,
                false,
                false,
                List.of(),
                0,
                false,
                true,
                "检测到非空且没有迁移历史的数据库"
            );
        }

        private R2dbcMigrationDatabaseState state(
            long totalTables,
            boolean emptyDatabase,
            boolean migrationsExists,
            List<Long> appliedVersions,
            long currentVersion,
            boolean canMigrate,
            boolean unmanagedDatabase,
            String note
        ) {
            List<R2dbcMigrationCatalogEntry>
                pendingEntries =
                catalog.entriesAfter(
                    currentVersion
                );

            return new R2dbcMigrationDatabaseState(
                DatabaseType.MYSQL,
                names,
                totalTables,
                emptyDatabase,
                migrationsExists,
                migrationsExists,
                appliedVersions,
                currentVersion,
                catalog.latestVersion(),
                pendingEntries.size(),
                pendingEntries,
                List.of(),
                List.of(),
                false,
                true,
                unmanagedDatabase,
                canMigrate,
                note
            );
        }

        private InitializationClaim acquiredClaim() {
            UUID attemptId =
                UUID.randomUUID();

            Instant now =
                Instant.now();

            return InitializationClaim.acquired(
                attemptId,
                new SystemInstallationRecord(
                    (short) 1,
                    UUID.randomUUID(),
                    InstallationState.INITIALIZING,
                    1,
                    attemptId,
                    now,
                    null,
                    null,
                    null,
                    null,
                    now,
                    now
                )
            );
        }

        private SystemInstallationRecord
            installedRecord() {

            Instant now =
                Instant.now();

            return new SystemInstallationRecord(
                (short) 1,
                UUID.randomUUID(),
                InstallationState.INSTALLED,
                2,
                UUID.randomUUID(),
                now.minusSeconds(60),
                now,
                "0.0.1-dev",
                null,
                null,
                now.minusSeconds(60),
                now
            );
        }
    }
}
