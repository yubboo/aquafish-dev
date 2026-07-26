package com.aquafish.plugin.database;

import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.migration.DatabaseMigrationLocationProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/** 注册插件安装、设置和权限声明迁移目录。 */
@Component
public final class PluginDatabaseMigrationLocationProvider
    implements DatabaseMigrationLocationProvider {

    @Override
    public String moduleKey() {
        return "plugin";
    }

    @Override
    public int order() {
        return 600;
    }

    @Override
    public List<String> locations(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL, MARIADB ->
                List.of("classpath:db/migration/plugin/mysql");
            case POSTGRESQL ->
                List.of("classpath:db/migration/plugin/postgresql");
        };
    }
}
