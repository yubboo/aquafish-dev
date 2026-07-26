package com.aquafish.core.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.migration.r2dbc.R2dbcMigrationDatabaseState;
import com.aquafish.core.database.migration.r2dbc.R2dbcMigrationStateInspector;
import com.aquafish.core.database.migration.r2dbc.R2dbcMigrationTableNames;
import com.aquafish.core.installation.InstallationState;
import com.aquafish.core.installation.InstallationStateService;
import com.aquafish.core.installation.InstallationStateSnapshot;
import com.aquafish.core.installation.SystemInstallationRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 数据库四状态识别测试。
 */
class SetupDatabaseInspectionServiceTest {

    private final SetupDatabaseInspectionService
        service =
        new SetupDatabaseInspectionService(
            mock(
                R2dbcMigrationStateInspector.class
            ),
            mock(
                InstallationStateService.class
            )
        );

    @Test
    void shouldAllowEmptyPrefixForNewInstall() {
        SetupDatabaseInspection result =
            service.classify(
                migration(
                    0,
                    false,
                    false,
                    0,
                    22,
                    22
                ),
                InstallationStateSnapshot
                    .tableMissing()
            );

        assertEquals(
            SetupDatabaseMode.NEW_INSTALL,
            result.mode()
        );

        assertTrue(
            result.newInstallAllowed()
        );
    }

    @Test
    void shouldRecognizeInstalledDatabase() {
        SetupDatabaseInspection result =
            service.classify(
                migration(
                    71,
                    true,
                    true,
                    22,
                    22,
                    0
                ),
                InstallationStateSnapshot
                    .found(
                        installedRecord()
                    )
            );

        assertEquals(
            SetupDatabaseMode
                .EXISTING_INSTALLED,
            result.mode()
        );

        assertTrue(
            result.recoveryAllowed()
        );

        assertFalse(
            result.newInstallAllowed()
        );
    }

    @Test
    void shouldBlockPartialInstallation() {
        SetupDatabaseInspection result =
            service.classify(
                migration(
                    8,
                    false,
                    false,
                    0,
                    22,
                    22
                ),
                InstallationStateSnapshot
                    .tableMissing()
            );

        assertEquals(
            SetupDatabaseMode
                .INCOMPLETE_INSTALLATION,
            result.mode()
        );

        assertTrue(
            result.residueCleanupAllowed()
        );
    }

    private R2dbcMigrationDatabaseState
        migration(
            long tables,
            boolean history,
            boolean lock,
            long current,
            long latest,
            int pending
        ) {

        DatabaseSettings settings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "aquafish",
                "",
                "aq_"
            );

        return new R2dbcMigrationDatabaseState(
            settings.type(),
            R2dbcMigrationTableNames
                .from(settings),
            tables,
            tables == 0,
            history,
            lock,
            history && current > 0
                ? List.of(current)
                : List.of(),
            current,
            latest,
            pending,
            List.of(),
            List.of(),
            List.of(),
            false,
            true,
            tables > 0
                && !history,
            tables == 0
                || history,
            "测试状态"
        );
    }

    private SystemInstallationRecord
        installedRecord() {

        Instant now =
            Instant.parse(
                "2026-07-20T00:00:00Z"
            );

        return new SystemInstallationRecord(
            (short) 1,
            UUID.randomUUID(),
            InstallationState.INSTALLED,
            2,
            UUID.randomUUID(),
            now.minusSeconds(60),
            now,
            "22",
            null,
            null,
            now.minusSeconds(60),
            now
        );
    }
}
