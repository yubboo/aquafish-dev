package com.aquafish.forum.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aquafish.core.database.DatabaseType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证论坛迁移目录只会跟随当前选定的一种数据库。
 */
class ForumDatabaseMigrationLocationProviderTest {

    private final ForumDatabaseMigrationLocationProvider provider =
        new ForumDatabaseMigrationLocationProvider();

    @Test
    void shouldDeclareForumLocationsForAllSupportedDatabases() {
        assertEquals("forum", provider.moduleKey());
        assertEquals(200, provider.order());
        assertEquals(
            List.of("classpath:db/migration/forum/mysql"),
            provider.locations(DatabaseType.MYSQL)
        );
        assertEquals(
            List.of("classpath:db/migration/forum/mysql"),
            provider.locations(DatabaseType.MARIADB)
        );
        assertEquals(
            List.of("classpath:db/migration/forum/postgresql"),
            provider.locations(DatabaseType.POSTGRESQL)
        );
    }
}
