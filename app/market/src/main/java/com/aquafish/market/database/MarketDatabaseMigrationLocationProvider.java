package com.aquafish.market.database;

import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.migration.DatabaseMigrationLocationProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/** 注册应用市场目录缓存与安装记录迁移目录。 */
@Component
public final class MarketDatabaseMigrationLocationProvider
    implements DatabaseMigrationLocationProvider {

    @Override
    public String moduleKey() {
        return "market";
    }

    @Override
    public int order() {
        return 700;
    }

    @Override
    public List<String> locations(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL, MARIADB ->
                List.of("classpath:db/migration/market/mysql");
            case POSTGRESQL ->
                List.of("classpath:db/migration/market/postgresql");
        };
    }
}
