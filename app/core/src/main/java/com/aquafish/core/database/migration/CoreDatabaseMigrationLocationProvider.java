package com.aquafish.core.database.migration;

import com.aquafish.core.database.DatabaseType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Core 模块数据库迁移目录声明。
 */
@Component
public final class CoreDatabaseMigrationLocationProvider
    implements DatabaseMigrationLocationProvider {

    @Override
    public String moduleKey() {
        return "core";
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public List<String> locations(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL, MARIADB -> List.of(
                "classpath:db/migration/core/mysql"
            );
            case POSTGRESQL -> List.of(
                "classpath:db/migration/core/postgresql"
            );
        };
    }
}
