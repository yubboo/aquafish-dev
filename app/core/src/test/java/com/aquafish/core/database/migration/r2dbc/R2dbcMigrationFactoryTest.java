package com.aquafish.core.database.migration.r2dbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.migration.DatabaseMigrationLocationRegistry;
import java.util.List;
import name.nkonev.r2dbc.migrate.core.BunchOfResourcesEntry;
import name.nkonev.r2dbc.migrate.core.Dialect;
import name.nkonev.r2dbc.migrate.core.R2dbcMigrateProperties;
import org.junit.jupiter.api.Test;

/**
 * Aquafish R2DBC 迁移计划工厂测试。
 */
class R2dbcMigrationFactoryTest {

    @Test
    void shouldCreateMysqlMigrationPlan() {
        DatabaseMigrationLocationRegistry registry =
            mock(
                DatabaseMigrationLocationRegistry.class
            );

        when(
            registry.locations(
                DatabaseType.MYSQL
            )
        ).thenReturn(
            List.of(
                "classpath:db/migration/core/mysql",
                "classpath:db/migration/user/mysql"
            )
        );

        R2dbcMigrationFactory factory =
            new R2dbcMigrationFactory(
                registry
            );

        R2dbcMigrationPlan plan =
            factory.create(
                DatabaseSettings.defaultMysql()
            );

        R2dbcMigrateProperties properties =
            plan.properties();

        assertEquals(
            Dialect.MYSQL,
            properties.getDialect()
        );

        assertEquals(
            "aq_migrations",
            properties.getMigrationsTable()
        );

        assertEquals(
            "aq_migrations_lock",
            properties.getMigrationsLockTable()
        );

        assertTrue(properties.isEnable());

        assertFalse(
            properties.isWaitForDatabase()
        );

        assertTrue(
            properties.isPreferDbSpecificLock()
        );

        assertFalse(
            properties
                .isUseEnvironmentSubstitutor()
        );

        assertFalse(
            properties
                .isUseSystemPropertiesSubstitutor()
        );

        assertEquals(
            1,
            properties.getResources().size()
        );

        BunchOfResourcesEntry entry =
            properties
                .getResources()
                .getFirst();

        assertEquals(
            List.of(
                "classpath*:/db/migration/core/mysql/*.sql",
                "classpath*:/db/migration/user/mysql/*.sql"
            ),
            entry.getResourcesPaths()
        );

        assertInstanceOf(
            AquafishR2dbcMigrationResourceReader.class,
            plan.resourceReader()
        );
    }

    @Test
    void shouldCreatePostgresqlMigrationPlan() {
        DatabaseMigrationLocationRegistry registry =
            mock(
                DatabaseMigrationLocationRegistry.class
            );

        when(
            registry.locations(
                DatabaseType.POSTGRESQL
            )
        ).thenReturn(
            List.of(
                "classpath:db/migration/core/postgresql",
                "classpath:db/migration/user/postgresql"
            )
        );

        DatabaseSettings settings =
            new DatabaseSettings(
                DatabaseType.POSTGRESQL,
                "127.0.0.1",
                5432,
                "aquafish",
                "aquafish",
                "secret",
                "fish_"
            );

        R2dbcMigrationPlan plan =
            new R2dbcMigrationFactory(
                registry
            ).create(settings);

        assertEquals(
            Dialect.POSTGRESQL,
            plan.properties()
                .getDialect()
        );

        assertEquals(
            "fish_migrations",
            plan.properties()
                .getMigrationsTable()
        );

        assertEquals(
            "fish_migrations_lock",
            plan.properties()
                .getMigrationsLockTable()
        );
    }

    @Test
    void shouldRejectDatabaseWithoutMigrationLocations() {
        DatabaseMigrationLocationRegistry registry =
            mock(
                DatabaseMigrationLocationRegistry.class
            );

        when(
            registry.locations(
                DatabaseType.MYSQL
            )
        ).thenReturn(
            List.of()
        );

        R2dbcMigrationFactory factory =
            new R2dbcMigrationFactory(
                registry
            );

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () ->
                    factory.create(
                        DatabaseSettings
                            .defaultMysql()
                    )
            );

        assertTrue(
            error.getMessage()
                .contains(
                    "没有注册任何迁移目录"
                )
        );
    }
}
