package com.aquafish.core.database.migration.r2dbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import name.nkonev.r2dbc.migrate.reader.MigrateResource;
import name.nkonev.r2dbc.migrate.reader.MigrateResourceReader;
import org.junit.jupiter.api.Test;

/**
 * Aquafish R2DBC SQL 资源安全替换测试。
 */
class AquafishR2dbcMigrationResourceReaderTest {

    @Test
    void shouldReplaceOnlyAquafishMigrationPlaceholders()
        throws Exception {

        String sql =
            "CREATE TABLE ${tablePrefix}users (id BIGINT);"
                + System.lineSeparator()
                + "ALTER DATABASE `${flyway:database}` "
                + "CHARACTER SET utf8mb4;";

        MigrateResourceReader delegate =
            ignored ->
                List.of(
                    resource(
                        "V1__core.sql",
                        sql,
                        true
                    )
                );

        AquafishR2dbcMigrationResourceReader
            reader =
            new AquafishR2dbcMigrationResourceReader(
                delegate,
                "fish_",
                "aquafish"
            );

        List<MigrateResource> resources =
            reader.getResources(
                "ignored"
            );

        assertEquals(
            1,
            resources.size()
        );

        MigrateResource resource =
            resources.getFirst();

        assertTrue(resource.isReadable());

        assertEquals(
            "V1__core.sql",
            resource.getFilename()
        );

        final String actual;

        try (
            InputStream inputStream =
                resource.getInputStream()
        ) {
            actual =
                new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
                );
        }

        assertTrue(
            actual.contains(
                "CREATE TABLE fish_users"
            )
        );

        assertTrue(
            actual.contains(
                "ALTER DATABASE `aquafish`"
            )
        );

        assertFalse(
            actual.contains(
                "${tablePrefix}"
            )
        );

        assertFalse(
            actual.contains(
                "${flyway:database}"
            )
        );
    }

    @Test
    void shouldRejectUnsafeDatabaseIdentifier() {
        MigrateResourceReader delegate =
            ignored -> List.of();

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () ->
                    new AquafishR2dbcMigrationResourceReader(
                        delegate,
                        "aq_",
                        "aquafish`; DROP DATABASE mysql; --"
                    )
            );

        assertTrue(
            error.getMessage()
                .contains(
                    "数据库名称只能包含"
                )
        );
    }

    @Test
    void shouldPreserveUnreadableResourceState() {
        MigrateResourceReader delegate =
            ignored ->
                List.of(
                    resource(
                        "V2__user.sql",
                        "SELECT 1;",
                        false
                    )
                );

        AquafishR2dbcMigrationResourceReader
            reader =
            new AquafishR2dbcMigrationResourceReader(
                delegate,
                "aq_",
                "aquafish"
            );

        assertFalse(
            reader
                .getResources("ignored")
                .getFirst()
                .isReadable()
        );
    }

    private MigrateResource resource(
        String filename,
        String content,
        boolean readable
    ) {
        return new MigrateResource() {

            @Override
            public boolean isReadable() {
                return readable;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(
                    content.getBytes(
                        StandardCharsets.UTF_8
                    )
                );
            }

            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
