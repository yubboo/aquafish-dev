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
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Aquafish 响应式迁移执行器测试。
 */
class R2dbcMigrationExecutorTest {

    @Test
    void shouldRemainLazyBeforeSubscription() {
        DatabaseRuntimeSettingsService settingsService =
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

        R2dbcMigrationRunner migrationRunner =
            mock(
                R2dbcMigrationRunner.class
            );

        R2dbcMigrationExecutor executor =
            new R2dbcMigrationExecutor(
                settingsService,
                connectionFactory,
                migrationFactory,
                migrationRunner
            );

        Mono<Void> migration =
            executor.migrate(
                DatabaseSettings.defaultMysql()
            );

        /*
         * 只创建 Mono，不进行订阅。
         *
         * 因此数据库配置、连接池和迁移运行器
         * 都不能产生任何交互。
         */
        assertNotNull(migration);

        verifyNoInteractions(
            settingsService,
            connectionFactory,
            migrationFactory,
            migrationRunner
        );
    }

    @Test
    void shouldExecuteMigrationWithExactNormalizedSettings() {
        DatabaseRuntimeSettingsService settingsService =
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

        R2dbcMigrationRunner migrationRunner =
            mock(
                R2dbcMigrationRunner.class
            );

        DatabaseSettings submitted =
            DatabaseSettings.defaultMysql();

        DatabaseSettings normalized =
            submitted.normalized();

        R2dbcMigrationPlan plan =
            mock(
                R2dbcMigrationPlan.class
            );

        when(
            migrationFactory.create(
                normalized
            )
        ).thenReturn(plan);

        when(
            migrationRunner.migrate(
                connectionFactory,
                plan
            )
        ).thenReturn(
            Mono.empty()
        );

        R2dbcMigrationExecutor executor =
            new R2dbcMigrationExecutor(
                settingsService,
                connectionFactory,
                migrationFactory,
                migrationRunner
            );

        StepVerifier
            .create(
                executor.migrate(
                    submitted
                )
            )
            .verifyComplete();

        verify(settingsService)
            .useForInstallation(
                normalized
            );

        verify(
            connectionFactory,
            never()
        ).refresh();

        verify(migrationFactory)
            .create(
                normalized
            );

        verify(migrationRunner)
            .migrate(
                connectionFactory,
                plan
            );
    }

    @Test
    void shouldPropagateReactiveMigrationFailure() {
        DatabaseRuntimeSettingsService settingsService =
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

        R2dbcMigrationRunner migrationRunner =
            mock(
                R2dbcMigrationRunner.class
            );

        DatabaseSettings settings =
            DatabaseSettings
                .defaultMysql()
                .normalized();

        R2dbcMigrationPlan plan =
            mock(
                R2dbcMigrationPlan.class
            );

        IllegalStateException expected =
            new IllegalStateException(
                "模拟 R2DBC 迁移失败"
            );

        when(
            migrationFactory.create(
                settings
            )
        ).thenReturn(plan);

        when(
            migrationRunner.migrate(
                connectionFactory,
                plan
            )
        ).thenReturn(
            Mono.error(expected)
        );

        R2dbcMigrationExecutor executor =
            new R2dbcMigrationExecutor(
                settingsService,
                connectionFactory,
                migrationFactory,
                migrationRunner
            );

        StepVerifier
            .create(
                executor.migrate(
                    settings
                )
            )
            .expectErrorMatches(
                error ->
                    error == expected
            )
            .verify();

        verify(settingsService)
            .useForInstallation(
                settings
            );

        verify(
            connectionFactory,
            never()
        ).refresh();

        verify(migrationFactory)
            .create(
                settings
            );

        verify(migrationRunner)
            .migrate(
                connectionFactory,
                plan
            );
    }

    @Test
    void shouldRejectNullSettingsWithoutRunningMigration() {
        DatabaseRuntimeSettingsService settingsService =
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

        R2dbcMigrationRunner migrationRunner =
            mock(
                R2dbcMigrationRunner.class
            );

        R2dbcMigrationExecutor executor =
            new R2dbcMigrationExecutor(
                settingsService,
                connectionFactory,
                migrationFactory,
                migrationRunner
            );

        StepVerifier
            .create(
                executor.migrate(null)
            )
            .expectErrorMatches(
                error ->
                    error instanceof
                        IllegalStateException
                        && error
                            .getMessage()
                            .contains(
                                "不能为空"
                            )
            )
            .verify();

        verify(
            settingsService,
            never()
        ).useForInstallation(
            org.mockito.ArgumentMatchers.any()
        );

        verify(
            connectionFactory,
            never()
        ).refresh();

        verifyNoInteractions(
            migrationFactory,
            migrationRunner
        );
    }

}
