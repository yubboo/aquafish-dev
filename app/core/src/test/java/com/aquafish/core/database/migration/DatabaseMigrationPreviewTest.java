package com.aquafish.core.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 数据库迁移预览 DTO 测试。
 */
class DatabaseMigrationPreviewTest {

    @Test
    void shouldNormalizeNullableValues() {
        DatabaseMigrationPreview preview =
            new DatabaseMigrationPreview(
                true,
                true,
                false,
                false,
                null,
                null,
                2,
                null,
                null,
                null
            );

        assertEquals(
            "",
            preview.migrationsTable()
        );

        assertEquals(
            "",
            preview.currentVersion()
        );

        assertEquals(
            List.of(),
            preview.tables()
        );

        assertTrue(
            preview.hasPendingMigrations()
        );

        assertFalse(
            preview.hasError()
        );
    }

    @Test
    void shouldRecognizeCurrentDatabase() {
        DatabaseMigrationPreview preview =
            new DatabaseMigrationPreview(
                true,
                true,
                true,
                false,
                "aq_migrations",
                "4",
                0,
                List.of(),
                "数据库已经是最新版本。",
                ""
            );

        assertTrue(
            preview.upToDate()
        );

        assertFalse(
            preview.requiresDatabaseReset()
        );
    }

    @Test
    void shouldRequireResetForUnmanagedDatabase() {
        DatabaseMigrationPreview preview =
            new DatabaseMigrationPreview(
                true,
                false,
                false,
                true,
                "aq_migrations",
                "",
                4,
                List.of(),
                "请清空数据库。",
                ""
            );

        assertTrue(
            preview.requiresDatabaseReset()
        );

        assertFalse(
            preview.upToDate()
        );
    }

    @Test
    void shouldRejectNegativePendingCount() {
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new DatabaseMigrationPreview(
                    true,
                    true,
                    false,
                    false,
                    "aq_migrations",
                    "",
                    -1,
                    List.of(),
                    "",
                    ""
                )
        );
    }
}
