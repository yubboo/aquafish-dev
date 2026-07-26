package com.aquafish.license.database;

import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.migration.DatabaseMigrationLocationProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/** 注册客户实例授权审计域迁移目录。 */
@Component
public final class LicenseDatabaseMigrationLocationProvider
    implements DatabaseMigrationLocationProvider {

    @Override
    public String moduleKey() {
        return "license";
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public List<String> locations(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL, MARIADB ->
                List.of("classpath:db/migration/license/mysql");
            case POSTGRESQL ->
                List.of("classpath:db/migration/license/postgresql");
        };
    }
}
