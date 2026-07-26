package com.aquafish.core.database.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Aquafish V4 系统安装状态迁移测试。
 */
class SystemInstallationMigrationTest {

    /**
     * MySQL / MariaDB 迁移必须创建安全单例状态表。
     *
     * @throws IOException 资源读取失败
     */
    @Test
    void shouldProvideMysqlSystemInstallationState()
        throws IOException {

        String sql = readMigration(
            "db/migration/core/mysql/"
                + "V4__system_installation_state.sql"
        );

        assertTrue(
            sql.contains(
                "${tablePrefix}system_instances"
            )
        );

        assertTrue(
            sql.contains(
                "PRIMARY KEY (singleton_id)"
            )
        );

        assertTrue(
            sql.contains(
                "CHECK (singleton_id = 1)"
            )
        );

        assertTrue(
            sql.contains(
                "UNIQUE KEY "
                    + "uk_system_instances_instance_id"
            )
        );

        assertTrue(
            sql.contains(
                "state_version BIGINT UNSIGNED"
            )
        );

        assertTrue(
            sql.contains(
                "'INITIALIZING'"
            )
        );

        assertTrue(
            sql.contains(
                "'INSTALLED'"
            )
        );

        assertTrue(
            sql.contains(
                "ENGINE = InnoDB"
            )
        );

        assertNoAutomaticStateRecord(sql);
    }

    /**
     * PostgreSQL 迁移必须创建相同语义的状态表。
     *
     * @throws IOException 资源读取失败
     */
    @Test
    void shouldProvidePostgresqlSystemInstallationState()
        throws IOException {

        String sql = readMigration(
            "db/migration/core/postgresql/"
                + "V4__system_installation_state.sql"
        );

        assertTrue(
            sql.contains(
                "${tablePrefix}system_instances"
            )
        );

        assertTrue(
            sql.contains(
                "PRIMARY KEY (singleton_id)"
            )
        );

        assertTrue(
            sql.contains(
                "CHECK (singleton_id = 1)"
            )
        );

        assertTrue(
            sql.contains(
                "UNIQUE (instance_id)"
            )
        );

        assertTrue(
            sql.contains(
                "state_version BIGINT"
            )
        );

        assertTrue(
            sql.contains(
                "'FAILED'"
            )
        );

        assertFalse(
            sql.contains(
                "ENGINE = InnoDB"
            )
        );

        assertNoAutomaticStateRecord(sql);
    }

    /**
     * V4 只能创建表，不能自动插入状态。
     *
     * <p>
     * 否则旧站点和新安装流程可能被错误标记。
     * </p>
     */
    private void assertNoAutomaticStateRecord(
        String sql
    ) {
        String normalized =
            sql.toLowerCase(
                Locale.ROOT
            );

        assertFalse(
            normalized.contains(
                "insert into"
            )
        );
    }

    /**
     * 从测试类路径读取迁移 SQL。
     */
    private String readMigration(
        String resource
    ) throws IOException {

        InputStream input =
            getClass()
                .getClassLoader()
                .getResourceAsStream(
                    resource
                );

        assertNotNull(
            input,
            "没有找到数据库迁移资源："
                + resource
        );

        try (input) {
            return new String(
                input.readAllBytes(),
                StandardCharsets.UTF_8
            );
        }
    }
}
