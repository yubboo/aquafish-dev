package com.aquafish.theme.database;

import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.migration.DatabaseMigrationLocationProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/** 注册主题安装状态与设置迁移目录。 */
@Component
public final class ThemeDatabaseMigrationLocationProvider
    implements DatabaseMigrationLocationProvider {

    @Override
    public String moduleKey() {
        return "theme";
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    public List<String> locations(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL, MARIADB ->
                List.of("classpath:db/migration/theme/mysql");
            case POSTGRESQL ->
                List.of("classpath:db/migration/theme/postgresql");
        };
    }
}
