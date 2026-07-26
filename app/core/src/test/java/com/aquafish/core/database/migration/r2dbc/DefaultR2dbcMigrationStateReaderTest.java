package com.aquafish.core.database.migration.r2dbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.database.DatabaseType;
import org.junit.jupiter.api.Test;

/**
 * R2DBC 数据库迁移状态查询 SQL 测试。
 */
class DefaultR2dbcMigrationStateReaderTest {

    @Test
    void shouldBuildMysqlMetadataQueries() {
        String namesSql =
            DefaultR2dbcMigrationStateReader
                .tableNamesSql(
                    DatabaseType.MYSQL
                );

        String existsSql =
            DefaultR2dbcMigrationStateReader
                .tableExistsSql(
                    DatabaseType.MYSQL
                );

        assertTrue(
            namesSql.contains(
                "DATABASE()"
            )
        );

        assertTrue(
            existsSql.contains(
                ":tableName"
            )
        );

        assertEquals(
            "`aq_migrations`",
            DefaultR2dbcMigrationStateReader
                .quoteIdentifier(
                    DatabaseType.MYSQL,
                    "aq_migrations"
                )
        );
    }

    @Test
    void shouldBuildPostgresqlMetadataQueries() {
        String namesSql =
            DefaultR2dbcMigrationStateReader
                .tableNamesSql(
                    DatabaseType.POSTGRESQL
                );

        assertTrue(
            namesSql.contains(
                "current_schema()"
            )
        );

        assertEquals(
            "\"aq_migrations\"",
            DefaultR2dbcMigrationStateReader
                .quoteIdentifier(
                    DatabaseType.POSTGRESQL,
                    "aq_migrations"
                )
        );
    }

    @Test
    void shouldRejectUnsafeIdentifier() {
        assertThrows(
            IllegalStateException.class,
            () ->
                DefaultR2dbcMigrationStateReader
                    .quoteIdentifier(
                        DatabaseType.MYSQL,
                        "aq_migrations;drop"
                    )
        );
    }
}
