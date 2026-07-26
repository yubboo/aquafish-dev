package com.aquafish.core.installation.r2dbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquafish.core.database.DatabaseSettings;
import org.junit.jupiter.api.Test;

/**
 * 响应式安装状态表名测试。
 */
class R2dbcInstallationTableNamesTest {

    @Test
    void shouldResolvePhysicalSystemInstancesTable() {
        DatabaseSettings settings =
            DatabaseSettings
                .defaultMysql()
                .normalized();

        R2dbcInstallationTableNames names =
            R2dbcInstallationTableNames.from(
                settings
            );

        assertEquals(
            settings.tablePrefix()
                + "system_instances",
            names.systemInstancesTable()
        );
    }

    @Test
    void shouldRejectUnsafePhysicalTableName() {
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new R2dbcInstallationTableNames(
                    "aq_system_instances;drop"
                )
        );
    }

    @Test
    void shouldRejectNullDatabaseSettings() {
        assertThrows(
            NullPointerException.class,
            () ->
                R2dbcInstallationTableNames.from(
                    null
                )
        );
    }
}
