package com.aquafish.core.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aquafish.core.database.DatabaseType;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreDatabaseMigrationLocationProviderTest {

    private final CoreDatabaseMigrationLocationProvider provider =
        new CoreDatabaseMigrationLocationProvider();

    @Test
    void shouldDeclareCoreLocationsForBothDatabases() {
        assertEquals("core", provider.moduleKey());
        assertEquals(0, provider.order());
        assertEquals(
            List.of("classpath:db/migration/core/mysql"),
            provider.locations(DatabaseType.MYSQL)
        );
        assertEquals(
            List.of("classpath:db/migration/core/mysql"),
            provider.locations(DatabaseType.MARIADB)
        );
        assertEquals(
            List.of("classpath:db/migration/core/postgresql"),
            provider.locations(DatabaseType.POSTGRESQL)
        );
    }
}
