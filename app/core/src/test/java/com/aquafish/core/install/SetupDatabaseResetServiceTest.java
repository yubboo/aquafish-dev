package com.aquafish.core.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import io.r2dbc.spi.ConnectionFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 数据库安全重装测试。
 */
class SetupDatabaseResetServiceTest {

    private final SetupDatabaseResetService service =
        new SetupDatabaseResetService(
            mock(SetupDatabaseInspectionService.class),
            mock(SetupDeploymentContextService.class),
            mock(DatabaseRuntimeSettingsService.class),
            mock(ConnectionFactory.class),
            mock(InstallLockService.class)
        );

    @Test
    void requiresExplicitDataLossConfirmation() {
        assertThrows(
            IllegalStateException.class,
            () ->
                service.requireConfirmed(
                    new SetupDatabaseResetRequest(
                        settings(DatabaseType.MYSQL),
                        SetupDatabaseMode.EXISTING_INSTALLED,
                        false,
                        "重新安装"
                    )
                )
        );
    }

    @Test
    void acceptsDatabaseNameOrFixedConfirmationText() {
        SetupDatabaseResetRequest databaseName =
            service.requireConfirmed(
                new SetupDatabaseResetRequest(
                    settings(DatabaseType.MYSQL),
                    SetupDatabaseMode.EXISTING_INSTALLED,
                    true,
                    "erfish"
                )
            );

        service.requireConfirmationText(
            databaseName,
            settings(DatabaseType.MYSQL)
        );

        SetupDatabaseResetRequest fixedText =
            service.requireConfirmed(
                new SetupDatabaseResetRequest(
                    settings(DatabaseType.MYSQL),
                    SetupDatabaseMode.INCOMPLETE_INSTALLATION,
                    true,
                    "重新安装"
                )
            );

        service.requireConfirmationText(
            fixedText,
            settings(DatabaseType.MYSQL)
        );
    }

    @Test
    void onlyBuildsExactAquafishWhitelistDrops() {
        List<String> statements =
            service.dropStatements(
                settings(DatabaseType.MYSQL)
            );

        assertEquals(73, statements.size());

        assertTrue(
            statements.contains(
                "DROP TABLE IF EXISTS `aq_system_instances`"
            )
        );

        assertTrue(
            statements.contains(
                "DROP TABLE IF EXISTS `aq_migrations`"
            )
        );

        assertTrue(
            statements.stream().noneMatch(
                sql -> sql.contains("DROP DATABASE")
            )
        );
    }

    @Test
    void postgresqlUsesQuotedCascadeDrops() {
        List<String> statements =
            service.dropStatements(
                settings(DatabaseType.POSTGRESQL)
            );

        assertTrue(
            statements.contains(
                "DROP TABLE IF EXISTS \"aq_system_instances\" CASCADE"
            )
        );
    }

    private DatabaseSettings settings(
        DatabaseType type
    ) {
        return new DatabaseSettings(
            type,
            "127.0.0.1",
            type == DatabaseType.POSTGRESQL
                ? 5432
                : 3306,
            "erfish",
            "erfish",
            "",
            "aq_"
        );
    }
}
