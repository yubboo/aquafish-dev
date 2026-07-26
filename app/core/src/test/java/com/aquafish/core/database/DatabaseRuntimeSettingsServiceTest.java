package com.aquafish.core.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DatabaseRuntimeSettingsServiceTest {

    @Test
    void installationOverrideIsUsedImmediately() {
        DatabaseRuntimeSettingsService service = new DatabaseRuntimeSettingsService(
            "mysql", "127.0.0.1", 3306, "old_db", "old_user", "old_password", "old_"
        );

        DatabaseSettings replacement = new DatabaseSettings(
            DatabaseType.POSTGRESQL,
            "db.internal",
            5432,
            "aquafish_new",
            "aquafish",
            "secret",
            "site_"
        );

        service.useForInstallation(replacement);

        assertEquals(replacement.normalized(), service.current());
    }
}
