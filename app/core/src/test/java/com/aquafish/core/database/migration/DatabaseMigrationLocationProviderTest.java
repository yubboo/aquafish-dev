package com.aquafish.core.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.database.DatabaseType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DatabaseMigrationLocationProvider 接口契约测试。
 *
 * <p>该测试不会连接真实数据库，也不会执行数据库迁移。</p>
 *
 * <p>测试范围：</p>
 *
 * <ol>
 *     <li>验证默认模块加载顺序；</li>
 *     <li>验证模块可以自定义加载顺序；</li>
 *     <li>验证模块可以根据数据库类型返回不同迁移目录；</li>
 *     <li>验证暂不支持某种数据库时可以返回空列表。</li>
 * </ol>
 */
class DatabaseMigrationLocationProviderTest {

    /**
     * 未重写 order() 时，
     * 应使用接口定义的统一默认加载顺序。
     */
    @Test
    void shouldUseDefaultOrder() {
        DatabaseMigrationLocationProvider provider =
            new DatabaseMigrationLocationProvider() {

                @Override
                public String moduleKey() {
                    return "user";
                }

                @Override
                public List<String> locations(
                    DatabaseType databaseType
                ) {
                    return List.of();
                }
            };

        assertEquals(
            DatabaseMigrationLocationProvider.DEFAULT_ORDER,
            provider.order()
        );

        assertEquals(
            1000,
            provider.order()
        );
    }

    /**
     * 模块可以重写默认加载顺序。
     */
    @Test
    void shouldAllowCustomOrder() {
        DatabaseMigrationLocationProvider provider =
            new DatabaseMigrationLocationProvider() {

                @Override
                public String moduleKey() {
                    return "core";
                }

                @Override
                public int order() {
                    return 0;
                }

                @Override
                public List<String> locations(
                    DatabaseType databaseType
                ) {
                    return List.of();
                }
            };

        assertEquals(
            "core",
            provider.moduleKey()
        );

        assertEquals(
            0,
            provider.order()
        );
    }

    /**
     * 模块可以根据数据库类型，
     * 返回不同的迁移资源目录。
     */
    @Test
    void shouldProvideLocationsByDatabaseType() {
        DatabaseMigrationLocationProvider provider =
            new DatabaseMigrationLocationProvider() {

                @Override
                public String moduleKey() {
                    return "user";
                }

                @Override
                public int order() {
                    return 100;
                }

                @Override
                public List<String> locations(
                    DatabaseType databaseType
                ) {
                    if (
                        databaseType
                            == DatabaseType.MYSQL
                    ) {
                        return List.of(
                            "classpath:db/migration/user/mysql"
                        );
                    }

                    if (
                        databaseType
                            == DatabaseType.POSTGRESQL
                    ) {
                        return List.of(
                            "classpath:db/migration/user/postgresql"
                        );
                    }

                    return List.of();
                }
            };

        assertEquals(
            List.of(
                "classpath:db/migration/user/mysql"
            ),
            provider.locations(
                DatabaseType.MYSQL
            )
        );

        assertEquals(
            List.of(
                "classpath:db/migration/user/postgresql"
            ),
            provider.locations(
                DatabaseType.POSTGRESQL
            )
        );
    }

    /**
     * 模块暂时不支持某种数据库时，
     * 可以返回不可修改的空列表。
     */
    @Test
    void shouldAllowEmptyLocationList() {
        DatabaseMigrationLocationProvider provider =
            new DatabaseMigrationLocationProvider() {

                @Override
                public String moduleKey() {
                    return "cms";
                }

                @Override
                public List<String> locations(
                    DatabaseType databaseType
                ) {
                    return List.of();
                }
            };

        assertTrue(
            provider.locations(
                DatabaseType.MYSQL
            ).isEmpty()
        );

        assertTrue(
            provider.locations(
                DatabaseType.POSTGRESQL
            ).isEmpty()
        );
    }
}