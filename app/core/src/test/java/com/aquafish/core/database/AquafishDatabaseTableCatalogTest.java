package com.aquafish.core.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Aquafish 正式业务表白名单测试。
 */
class AquafishDatabaseTableCatalogTest {

    @Test
    void shouldReadAllCurrentTableNames() {
        DatabaseSettings settings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "aquafish",
                "",
                "site01_"
            );

        assertEquals(
            71,
            AquafishDatabaseTableCatalog
                .expectedTableCount()
        );

        assertTrue(
            AquafishDatabaseTableCatalog
                .physicalTableNames(settings)
                .contains(
                    "site01_system_instances"
                )
        );

        assertTrue(
            AquafishDatabaseTableCatalog
                .physicalTableNames(settings)
                .contains(
                    "site01_plugin_dependencies"
                )
        );
    }
}
