package com.aquafish.core.install;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.redis.RedisRuntimeSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * 已有实例恢复安全门槛测试。
 */
class SetupExistingInstallationRecoveryServiceTest {

    private final SetupExistingInstallationRecoveryService
        service =
        new SetupExistingInstallationRecoveryService(
            mock(SetupDatabaseInspectionService.class),
            mock(SetupDeploymentContextService.class),
            mock(DatabaseRuntimeSettingsService.class),
            mock(RedisRuntimeSettingsService.class),
            mock(ApplicationConfigWriterService.class),
            mock(InstallLockService.class),
            mock(DatabaseClient.class)
        );

    @Test
    void onlyExistingInstalledCanRecover() {
        SetupDatabaseInspection installed =
            inspection(
                SetupDatabaseMode.EXISTING_INSTALLED,
                true
            );

        assertSame(
            installed,
            service.requireRecoverable(installed)
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.requireRecoverable(
                    inspection(
                        SetupDatabaseMode.NEW_INSTALL,
                        false
                    )
                )
        );
    }

    private SetupDatabaseInspection inspection(
        SetupDatabaseMode mode,
        boolean recoveryAllowed
    ) {
        return new SetupDatabaseInspection(
            mode,
            mode == SetupDatabaseMode.NEW_INSTALL,
            recoveryAllowed,
            mode == SetupDatabaseMode.INCOMPLETE_INSTALLATION,
            mode == SetupDatabaseMode.EXISTING_INSTALLED,
            mode.name(),
            "V22",
            "V22",
            0,
            71,
            71,
            true,
            true,
            "2026-07-20T00:00:00Z",
            "22",
            "测试"
        );
    }
}
