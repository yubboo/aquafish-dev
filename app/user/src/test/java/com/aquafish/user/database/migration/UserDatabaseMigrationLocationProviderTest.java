package com.aquafish.user.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aquafish.core.database.DatabaseType;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserDatabaseMigrationLocationProviderTest {

    private final UserDatabaseMigrationLocationProvider provider =
        new UserDatabaseMigrationLocationProvider();

    @Test
    void shouldDeclareUserLocationsForAllSupportedDatabases() {
        assertEquals("user", provider.moduleKey());
        assertEquals(100, provider.order());
        assertEquals(
            List.of("classpath:db/migration/user/mysql"),
            provider.locations(DatabaseType.MYSQL)
        );
        assertEquals(
            List.of("classpath:db/migration/user/mysql"),
            provider.locations(DatabaseType.MARIADB)
        );
        assertEquals(
            List.of("classpath:db/migration/user/postgresql"),
            provider.locations(DatabaseType.POSTGRESQL)
        );
    }
}
