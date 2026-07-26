package com.aquafish.search.database;

import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.migration.DatabaseMigrationLocationProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/** 注册搜索文档与索引队列迁移目录。 */
@Component
public final class SearchDatabaseMigrationLocationProvider
    implements DatabaseMigrationLocationProvider {

    @Override
    public String moduleKey() {
        return "search";
    }

    @Override
    public int order() {
        return 900;
    }

    @Override
    public List<String> locations(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL, MARIADB ->
                List.of("classpath:db/migration/search/mysql");
            case POSTGRESQL ->
                List.of("classpath:db/migration/search/postgresql");
        };
    }
}
