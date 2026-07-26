package com.aquafish.content.database;

import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.migration.DatabaseMigrationLocationProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/** 注册 CMS / 博客内容域迁移目录。 */
@Component
public final class ContentDatabaseMigrationLocationProvider
    implements DatabaseMigrationLocationProvider {

    @Override
    public String moduleKey() {
        return "content";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public List<String> locations(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL, MARIADB ->
                List.of("classpath:db/migration/content/mysql");
            case POSTGRESQL ->
                List.of("classpath:db/migration/content/postgresql");
        };
    }
}
