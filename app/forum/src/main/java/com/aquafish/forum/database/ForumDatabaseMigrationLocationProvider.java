package com.aquafish.forum.database;

import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.migration.DatabaseMigrationLocationProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 论坛模块数据库迁移目录声明。
 *
 * <p>这个类只负责把论坛迁移资源注册给全局迁移目录，
 * 不连接数据库，也不自行执行 SQL。真正的执行仍由 core 迁移底座统一完成。</p>
 *
 * <p>MySQL 和 MariaDB 使用经兼容性约束的同一目录；
 * PostgreSQL 使用独立方言目录。运行时只会选择用户安装时确定的那一种数据库。</p>
 */
@Component
public final class ForumDatabaseMigrationLocationProvider
    implements DatabaseMigrationLocationProvider {

    /**
     * 论坛模块的稳定标识，必须与迁移资源根目录同名。
     */
    @Override
    public String moduleKey() {
        return "forum";
    }

    /**
     * 论坛依赖 core 和 user 先创建用户、角色与权限表，
     * 因此排在 core(0) 和 user(100) 之后注册。
     */
    @Override
    public int order() {
        return 200;
    }

    /**
     * 根据当前实例唯一选定的数据库类型返回一个迁移目录。
     *
     * @param databaseType 当前实例的数据库类型
     * @return 论坛模块对应的唯一迁移目录
     */
    @Override
    public List<String> locations(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL, MARIADB -> List.of(
                "classpath:db/migration/forum/mysql"
            );
            case POSTGRESQL -> List.of(
                "classpath:db/migration/forum/postgresql"
            );
        };
    }
}
