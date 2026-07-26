package com.aquafish.ai.database;

import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.migration.DatabaseMigrationLocationProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/** 注册 AI 提供商、模型、提示词、任务和审核迁移目录。 */
@Component
public final class AiDatabaseMigrationLocationProvider
    implements DatabaseMigrationLocationProvider {

    @Override
    public String moduleKey() {
        return "ai";
    }

    @Override
    public int order() {
        return 800;
    }

    @Override
    public List<String> locations(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL, MARIADB ->
                List.of("classpath:db/migration/ai/mysql");
            case POSTGRESQL ->
                List.of("classpath:db/migration/ai/postgresql");
        };
    }
}
