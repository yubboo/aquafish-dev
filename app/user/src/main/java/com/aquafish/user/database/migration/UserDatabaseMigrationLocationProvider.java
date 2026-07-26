package com.aquafish.user.database.migration;

import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.migration.DatabaseMigrationLocationProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * User 模块数据库迁移目录声明。
 */
@Component
public final class UserDatabaseMigrationLocationProvider
    implements DatabaseMigrationLocationProvider {

    @Override
    public String moduleKey() {
        return "user";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public List<String> locations(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL, MARIADB -> List.of(
                "classpath:db/migration/user/mysql"
            );
            case POSTGRESQL -> List.of(
                "classpath:db/migration/user/postgresql"
            );
        };
    }
}
