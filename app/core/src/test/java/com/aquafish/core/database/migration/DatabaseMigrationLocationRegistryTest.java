package com.aquafish.core.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.database.DatabaseType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DatabaseMigrationLocationRegistry 单元测试。
 *
 * <p>该测试不会连接真实数据库，
 * 也不会执行任何 Flyway 数据库迁移。</p>
 *
 * <p>测试范围：</p>
 *
 * <ol>
 *     <li>空提供者列表；</li>
 *     <li>模块排序；</li>
 *     <li>数据库类型目录解析；</li>
 *     <li>重复模块检查；</li>
 *     <li>非法模块名称检查；</li>
 *     <li>非法迁移目录检查；</li>
 *     <li>重复迁移目录检查；</li>
 *     <li>结果不可修改检查。</li>
 * </ol>
 */
class DatabaseMigrationLocationRegistryTest {

    /**
     * 没有任何模块提供者时，
     * 注册中心应该正常返回空结果。
     */
    @Test
    void shouldAllowEmptyProviderList() {
        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of()
            );

        assertEquals(
            0,
            registry.providerCount()
        );

        assertTrue(
            registry.registeredModuleKeys()
                .isEmpty()
        );

        assertTrue(
            registry.locations(
                DatabaseType.MYSQL
            ).isEmpty()
        );

        assertTrue(
            registry.locations(
                DatabaseType.POSTGRESQL
            ).isEmpty()
        );
    }

    /**
     * 提供者应该按照 order 从小到大排序。
     */
    @Test
    void shouldSortProvidersByOrder() {
        DatabaseMigrationLocationProvider user =
            provider(
                "user",
                100,
                "classpath:db/migration/user/mysql",
                "classpath:db/migration/user/postgresql"
            );

        DatabaseMigrationLocationProvider core =
            provider(
                "core",
                0,
                "classpath:db/migration/core/mysql",
                "classpath:db/migration/core/postgresql"
            );

        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    user,
                    core
                )
            );

        assertEquals(
            List.of(
                "core",
                "user"
            ),
            registry.registeredModuleKeys()
        );

        assertEquals(
            List.of(
                "classpath:db/migration/core/mysql",
                "classpath:db/migration/user/mysql"
            ),
            registry.locations(
                DatabaseType.MYSQL
            )
        );
    }

    /**
     * order 相同时，
     * 应按照 moduleKey 字母顺序排列。
     */
    @Test
    void shouldSortEqualOrderByModuleKey() {
        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider(
                        "user",
                        100,
                        "classpath:db/migration/user/mysql",
                        "classpath:db/migration/user/postgresql"
                    ),
                    provider(
                        "bbs",
                        100,
                        "classpath:db/migration/bbs/mysql",
                        "classpath:db/migration/bbs/postgresql"
                    ),
                    provider(
                        "cms",
                        100,
                        "classpath:db/migration/cms/mysql",
                        "classpath:db/migration/cms/postgresql"
                    )
                )
            );

        assertEquals(
            List.of(
                "bbs",
                "cms",
                "user"
            ),
            registry.registeredModuleKeys()
        );
    }

    /**
     * MySQL 和 PostgreSQL
     * 应该返回各自对应的迁移目录。
     */
    @Test
    void shouldResolveLocationsByDatabaseType() {
        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider(
                        "core",
                        0,
                        "classpath:db/migration/core/mysql",
                        "classpath:db/migration/core/postgresql"
                    )
                )
            );

        assertEquals(
            List.of(
                "classpath:db/migration/core/mysql"
            ),
            registry.locations(
                DatabaseType.MYSQL
            )
        );

        assertEquals(
            List.of(
                "classpath:db/migration/core/postgresql"
            ),
            registry.locations(
                DatabaseType.POSTGRESQL
            )
        );
    }

    /**
     * 同一个模块标识不能注册两次。
     */
    @Test
    void shouldRejectDuplicateModuleKeys() {
        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () ->
                    new DatabaseMigrationLocationRegistry(
                        List.of(
                            provider(
                                "user",
                                100,
                                "classpath:db/migration/user/mysql",
                                "classpath:db/migration/user/postgresql"
                            ),
                            provider(
                                "user",
                                200,
                                "classpath:db/migration/user/mysql/extra",
                                "classpath:db/migration/user/postgresql/extra"
                            )
                        )
                    )
            );

        assertTrue(
            error.getMessage()
                .contains(
                    "重复注册"
                )
        );
    }

    /**
     * 模块标识不能包含大写字母。
     */
    @Test
    void shouldRejectUppercaseModuleKey() {
        assertThrows(
            IllegalStateException.class,
            () ->
                new DatabaseMigrationLocationRegistry(
                    List.of(
                        provider(
                            "User",
                            100,
                            "classpath:db/migration/User/mysql",
                            "classpath:db/migration/User/postgresql"
                        )
                    )
                )
        );
    }

    /**
     * 模块标识不能包含下划线。
     */
    @Test
    void shouldRejectUnderscoreModuleKey() {
        assertThrows(
            IllegalStateException.class,
            () ->
                new DatabaseMigrationLocationRegistry(
                    List.of(
                        provider(
                            "user_center",
                            100,
                            "classpath:db/migration/user_center/mysql",
                            "classpath:db/migration/user_center/postgresql"
                        )
                    )
                )
        );
    }

    /**
     * 模块标识不能包含首尾空格。
     */
    @Test
    void shouldRejectModuleKeyWithOuterSpaces() {
        assertThrows(
            IllegalStateException.class,
            () ->
                new DatabaseMigrationLocationRegistry(
                    List.of(
                        provider(
                            " user",
                            100,
                            "classpath:db/migration/user/mysql",
                            "classpath:db/migration/user/postgresql"
                        )
                    )
                )
        );
    }

    /**
     * 提供者列表中不能出现 null。
     */
    @Test
    void shouldRejectNullProvider() {
        assertThrows(
            IllegalStateException.class,
            () ->
                new DatabaseMigrationLocationRegistry(
                    Arrays.asList(
                        provider(
                            "core",
                            0,
                            "classpath:db/migration/core/mysql",
                            "classpath:db/migration/core/postgresql"
                        ),
                        null
                    )
                )
        );
    }

    /**
     * 数据库类型不能为 null。
     */
    @Test
    void shouldRejectNullDatabaseType() {
        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of()
            );

        assertThrows(
            NullPointerException.class,
            () ->
                registry.locations(
                    null
                )
        );
    }

    /**
     * 模块不能返回 null 迁移目录列表。
     */
    @Test
    void shouldRejectNullLocationList() {
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
                    return null;
                }
            };

        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                registry.locations(
                    DatabaseType.MYSQL
                )
        );
    }

    /**
     * 单个迁移目录不能为空。
     */
    @Test
    void shouldRejectBlankLocation() {
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
                    return List.of(
                        " "
                    );
                }
            };

        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                registry.locations(
                    DatabaseType.MYSQL
                )
        );
    }

    /**
     * 迁移目录不能包含首尾空格。
     */
    @Test
    void shouldRejectLocationWithOuterSpaces() {
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
                    return List.of(
                        " classpath:db/migration/user/mysql"
                    );
                }
            };

        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                registry.locations(
                    DatabaseType.MYSQL
                )
        );
    }

    /**
     * 模块不能声明其他模块的迁移目录。
     */
    @Test
    void shouldRejectLocationOwnedByAnotherModule() {
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
                    return List.of(
                        "classpath:db/migration/core/mysql"
                    );
                }
            };

        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                registry.locations(
                    DatabaseType.MYSQL
                )
        );
    }

    /**
     * MySQL 模块不能返回 PostgreSQL 迁移目录。
     */
    @Test
    void shouldRejectWrongDatabaseDirectory() {
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
                    return List.of(
                        "classpath:db/migration/user/postgresql"
                    );
                }
            };

        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                registry.locations(
                    DatabaseType.MYSQL
                )
        );
    }

    /**
     * 目录不能包含路径穿越字符。
     */
    @Test
    void shouldRejectPathTraversal() {
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
                    return List.of(
                        "classpath:db/migration/user/mysql/../core"
                    );
                }
            };

        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                registry.locations(
                    DatabaseType.MYSQL
                )
        );
    }

    /**
     * 目录不能使用 Windows 反斜杠。
     */
    @Test
    void shouldRejectWindowsBackslash() {
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
                    return List.of(
                        "classpath:db\\migration\\user\\mysql"
                    );
                }
            };

        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                registry.locations(
                    DatabaseType.MYSQL
                )
        );
    }

    /**
     * 目录不能包含连续斜杠。
     */
    @Test
    void shouldRejectDoubleSlash() {
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
                    return List.of(
                        "classpath:db/migration/user/mysql//extra"
                    );
                }
            };

        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                registry.locations(
                    DatabaseType.MYSQL
                )
        );
    }

    /**
     * 目录末尾不能带斜杠。
     */
    @Test
    void shouldRejectTrailingSlash() {
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
                    return List.of(
                        "classpath:db/migration/user/mysql/"
                    );
                }
            };

        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                registry.locations(
                    DatabaseType.MYSQL
                )
        );
    }

    /**
     * 同一个迁移目录不能重复声明。
     */
    @Test
    void shouldRejectDuplicateLocations() {
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
                    return List.of(
                        "classpath:db/migration/user/mysql",
                        "classpath:db/migration/user/mysql"
                    );
                }
            };

        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                registry.locations(
                    DatabaseType.MYSQL
                )
        );
    }

    /**
     * 同一个模块可以声明合法的子目录。
     */
    @Test
    void shouldAllowModuleSubdirectories() {
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
                    return List.of(
                        "classpath:db/migration/user/mysql/base",
                        "classpath:db/migration/user/mysql/feature"
                    );
                }
            };

        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider
                )
            );

        assertEquals(
            List.of(
                "classpath:db/migration/user/mysql/base",
                "classpath:db/migration/user/mysql/feature"
            ),
            registry.locations(
                DatabaseType.MYSQL
            )
        );
    }

    /**
     * 返回的迁移目录列表必须不可修改。
     */
    @Test
    void shouldReturnImmutableLocationList() {
        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider(
                        "core",
                        0,
                        "classpath:db/migration/core/mysql",
                        "classpath:db/migration/core/postgresql"
                    )
                )
            );

        List<String> locations =
            registry.locations(
                DatabaseType.MYSQL
            );

        assertThrows(
            UnsupportedOperationException.class,
            () ->
                locations.add(
                    "classpath:db/migration/core/mysql/extra"
                )
        );
    }

    /**
     * 返回的模块标识列表必须不可修改。
     */
    @Test
    void shouldReturnImmutableModuleKeyList() {
        DatabaseMigrationLocationRegistry registry =
            new DatabaseMigrationLocationRegistry(
                List.of(
                    provider(
                        "core",
                        0,
                        "classpath:db/migration/core/mysql",
                        "classpath:db/migration/core/postgresql"
                    )
                )
            );

        List<String> moduleKeys =
            registry.registeredModuleKeys();

        assertThrows(
            UnsupportedOperationException.class,
            () ->
                moduleKeys.add(
                    "user"
                )
        );
    }

    /**
     * 创建测试用迁移目录提供者。
     */
    private DatabaseMigrationLocationProvider provider(
        String moduleKey,
        int order,
        String mysqlLocation,
        String postgresqlLocation
    ) {
        return new DatabaseMigrationLocationProvider() {

            @Override
            public String moduleKey() {
                return moduleKey;
            }

            @Override
            public int order() {
                return order;
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
                        mysqlLocation
                    );
                }

                if (
                    databaseType
                        == DatabaseType.POSTGRESQL
                ) {
                    return List.of(
                        postgresqlLocation
                    );
                }

                return List.of();
            }
        };
    }
}