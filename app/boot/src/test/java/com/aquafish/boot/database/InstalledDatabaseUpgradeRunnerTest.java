package com.aquafish.boot.database;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.migration.DatabaseMigrationResult;
import com.aquafish.core.database.migration.r2dbc.R2dbcDatabaseMigrationService;
import com.aquafish.core.install.AuthoritativeInstallStatus;
import com.aquafish.core.install.AuthoritativeInstallStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import reactor.core.publisher.Mono;

/**
 * 已安装实例自动升级编排测试。
 */
class InstalledDatabaseUpgradeRunnerTest {

    @Test
    void shouldUpgradeOnlyWhenDatabaseSaysInstalled() {
        AuthoritativeInstallStatusService statusService =
            mock(AuthoritativeInstallStatusService.class);
        R2dbcDatabaseMigrationService migrationService =
            mock(R2dbcDatabaseMigrationService.class);

        when(statusService.current()).thenReturn(
            Mono.just(
                new AuthoritativeInstallStatus(
                    true,
                    true,
                    false,
                    true,
                    "INSTALLED",
                    true,
                    "2026-07-17T00:00:00Z",
                    null
                )
            )
        );
        when(
            migrationService.upgradeInstalledDatabase()
        ).thenReturn(
            Mono.just(
                new DatabaseMigrationResult(
                    DatabaseType.MYSQL,
                    "6",
                    "16",
                    10,
                    0,
                    true,
                    "数据库迁移完成。"
                )
            )
        );

        new InstalledDatabaseUpgradeRunner(
            statusService,
            migrationService
        ).run(
            mock(ApplicationArguments.class)
        );

        verify(migrationService)
            .upgradeInstalledDatabase();
    }

    @Test
    void shouldSkipPureDistributionPackageBeforeInstallation() {
        AuthoritativeInstallStatusService statusService =
            mock(AuthoritativeInstallStatusService.class);
        R2dbcDatabaseMigrationService migrationService =
            mock(R2dbcDatabaseMigrationService.class);

        when(statusService.current()).thenReturn(
            Mono.just(
                new AuthoritativeInstallStatus(
                    false,
                    false,
                    true,
                    true,
                    "NOT_CONFIGURED",
                    false,
                    null,
                    null
                )
            )
        );

        new InstalledDatabaseUpgradeRunner(
            statusService,
            migrationService
        ).run(
            mock(ApplicationArguments.class)
        );

        verify(
            migrationService,
            never()
        ).upgradeInstalledDatabase();
    }
}
