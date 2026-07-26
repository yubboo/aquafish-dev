package com.aquafish.core.database.migration.r2dbc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.r2dbc.RuntimeR2dbcConnectionFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Aquafish R2DBC 数据库迁移状态检查器测试。
 */
class R2dbcMigrationStateInspectorTest {

    @Test
    void shouldRemainLazyBeforeSubscription() {
        Fixture fixture =
            fixture();

        Mono<R2dbcMigrationDatabaseState>
            result =
            fixture.inspector.inspect(
                fixture.settings
            );

        assertNotNull(result);

        verifyNoInteractions(
            fixture.settingsService,
            fixture.connectionFactory,
            fixture.migrationFactory,
            fixture.catalog,
            fixture.reader
        );
    }

    @Test
    void shouldAllowEmptyDatabaseMigration() {
        Fixture fixture =
            fixture();

        fixture.stub(
            new R2dbcMigrationDatabaseSnapshot(
                0,
                false,
                false,
                List.of()
            )
        );

        StepVerifier
            .create(
                fixture.inspector.inspect(
                    fixture.settings
                )
            )
            .assertNext(
                state -> {
                    org.junit.jupiter.api.Assertions
                        .assertTrue(
                            state.emptyDatabase()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertTrue(
                            state.canMigrate()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            4,
                            state.pendingMigrations()
                        );
                }
            )
            .verifyComplete();

        verify(fixture.settingsService)
            .useForInstallation(
                fixture.settings
            );

        verify(
            fixture.connectionFactory,
            never()
        ).refresh();
    }

    @Test
    void shouldReadManagedDatabaseVersion() {
        Fixture fixture =
            fixture();

        fixture.stub(
            new R2dbcMigrationDatabaseSnapshot(
                18,
                true,
                true,
                List.of(
                    1L,
                    2L,
                    3L
                )
            )
        );

        StepVerifier
            .create(
                fixture.inspector.inspect(
                    fixture.settings
                )
            )
            .assertNext(
                state -> {
                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            3,
                            state.currentVersion()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            1,
                            state.pendingMigrations()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertTrue(
                            state.historyConsistent()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertTrue(
                            state.canMigrate()
                        );
                }
            )
            .verifyComplete();
    }

    @Test
    void shouldRejectUnmanagedNonEmptyDatabase() {
        Fixture fixture =
            fixture();

        fixture.stub(
            new R2dbcMigrationDatabaseSnapshot(
                12,
                false,
                false,
                List.of()
            )
        );

        StepVerifier
            .create(
                fixture.inspector.inspect(
                    fixture.settings
                )
            )
            .assertNext(
                state -> {
                    org.junit.jupiter.api.Assertions
                        .assertTrue(
                            state.unmanagedDatabase()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertFalse(
                            state.canMigrate()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertTrue(
                            state.note()
                                .contains(
                                    "清空数据库"
                                )
                        );
                }
            )
            .verifyComplete();
    }

    @Test
    void shouldDetectHistoryGap() {
        Fixture fixture =
            fixture();

        fixture.stub(
            new R2dbcMigrationDatabaseSnapshot(
                18,
                true,
                true,
                List.of(
                    1L,
                    3L
                )
            )
        );

        StepVerifier
            .create(
                fixture.inspector.inspect(
                    fixture.settings
                )
            )
            .assertNext(
                state -> {
                    org.junit.jupiter.api.Assertions
                        .assertFalse(
                            state.historyConsistent()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            List.of(2L),
                            state.missingAppliedVersions()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertFalse(
                            state.canMigrate()
                        );
                }
            )
            .verifyComplete();
    }

    @Test
    void shouldDetectDatabaseAheadOfCode() {
        Fixture fixture =
            fixture();

        fixture.stub(
            new R2dbcMigrationDatabaseSnapshot(
                18,
                true,
                true,
                List.of(
                    1L,
                    2L,
                    3L,
                    4L,
                    5L
                )
            )
        );

        StepVerifier
            .create(
                fixture.inspector.inspect(
                    fixture.settings
                )
            )
            .assertNext(
                state -> {
                    org.junit.jupiter.api.Assertions
                        .assertTrue(
                            state.databaseAhead()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertFalse(
                            state.canMigrate()
                        );
                }
            )
            .verifyComplete();
    }

    private Fixture fixture() {
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

        R2dbcMigrationFactory migrationFactory =
            mock(
                R2dbcMigrationFactory.class
            );

        R2dbcMigrationCatalog catalog =
            mock(
                R2dbcMigrationCatalog.class
            );

        R2dbcMigrationStateReader reader =
            mock(
                R2dbcMigrationStateReader.class
            );

        DatabaseSettings settings =
            DatabaseSettings
                .defaultMysql()
                .normalized();

        R2dbcMigrationPlan plan =
            mock(
                R2dbcMigrationPlan.class
            );

        R2dbcMigrationCatalogSnapshot
            catalogSnapshot =
            new R2dbcMigrationCatalogSnapshot(
                List.of(
                    entry(1),
                    entry(2),
                    entry(3),
                    entry(4)
                ),
                4
            );

        when(
            migrationFactory.create(
                settings
            )
        ).thenReturn(plan);

        when(
            catalog.read(plan)
        ).thenReturn(
            catalogSnapshot
        );

        R2dbcMigrationStateInspector
            inspector =
            new R2dbcMigrationStateInspector(
                settingsService,
                connectionFactory,
                migrationFactory,
                catalog,
                reader
            );

        return new Fixture(
            settingsService,
            connectionFactory,
            migrationFactory,
            catalog,
            reader,
            settings,
            inspector
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
                + "__migration.sql"
        );
    }

    private record Fixture(
        DatabaseRuntimeSettingsService
            settingsService,
        RuntimeR2dbcConnectionFactory
            connectionFactory,
        R2dbcMigrationFactory migrationFactory,
        R2dbcMigrationCatalog catalog,
        R2dbcMigrationStateReader reader,
        DatabaseSettings settings,
        R2dbcMigrationStateInspector inspector
    ) {

        private void stub(
            R2dbcMigrationDatabaseSnapshot snapshot
        ) {
            R2dbcMigrationTableNames names =
                R2dbcMigrationTableNames.from(
                    settings
                );

            when(
                reader.read(
                    connectionFactory,
                    settings,
                    names
                )
            ).thenReturn(
                Mono.just(snapshot)
            );
        }
    }
}
