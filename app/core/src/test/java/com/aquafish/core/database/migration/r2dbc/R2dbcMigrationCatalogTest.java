package com.aquafish.core.database.migration.r2dbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.database.DatabaseSettings;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import name.nkonev.r2dbc.migrate.core.BunchOfResourcesEntry;
import name.nkonev.r2dbc.migrate.core.R2dbcMigrateProperties;
import name.nkonev.r2dbc.migrate.reader.MigrateResource;
import name.nkonev.r2dbc.migrate.reader.MigrateResourceReader;
import org.junit.jupiter.api.Test;

/**
 * Aquafish R2DBC 迁移版本目录测试。
 */
class R2dbcMigrationCatalogTest {

    @Test
    void shouldReadSortAndCountMigrationVersions() {
        R2dbcMigrationPlan plan =
            plan(
                Map.of(
                    "classpath*:/core/*.sql",
                    List.of(
                        resource(
                            "V4__system_installation.sql",
                            true
                        ),
                        resource(
                            "V1__core_identity.sql",
                            true
                        )
                    ),
                    "classpath*:/user/*.sql",
                    List.of(
                        resource(
                            "V3__user_security.sql",
                            true
                        ),
                        resource(
                            "V2__user_profile.sql",
                            true
                        )
                    )
                )
            );

        R2dbcMigrationCatalogSnapshot snapshot =
            new R2dbcMigrationCatalog()
                .read(plan);

        assertEquals(
            List.of(
                1L,
                2L,
                3L,
                4L
            ),
            snapshot
                .entries()
                .stream()
                .map(
                    R2dbcMigrationCatalogEntry
                        ::version
                )
                .toList()
        );

        assertEquals(
            4,
            snapshot.latestVersion()
        );

        assertEquals(
            3,
            snapshot.pendingAfter(1)
        );

        assertEquals(
            0,
            snapshot.pendingAfter(4)
        );

        assertTrue(
            snapshot
                .find(3)
                .isPresent()
        );

        assertFalse(
            snapshot.isEmpty()
        );
    }

    @Test
    void shouldIgnorePremigrationAndUnreadableResources() {
        R2dbcMigrationPlan plan =
            plan(
                Map.of(
                    "classpath*:/core/*.sql",
                    List.of(
                        resource(
                            "V0__prepare__premigration.sql",
                            true
                        ),
                        resource(
                            "V1__core.sql",
                            true
                        ),
                        resource(
                            "V2__unreadable.sql",
                            false
                        )
                    ),
                    "classpath*:/user/*.sql",
                    List.of()
                )
            );

        R2dbcMigrationCatalogSnapshot snapshot =
            new R2dbcMigrationCatalog()
                .read(plan);

        assertEquals(
            1,
            snapshot.entries().size()
        );

        assertEquals(
            1,
            snapshot.latestVersion()
        );
    }

    @Test
    void shouldRejectDuplicateMigrationVersions() {
        R2dbcMigrationPlan plan =
            plan(
                Map.of(
                    "classpath*:/core/*.sql",
                    List.of(
                        resource(
                            "V2__core.sql",
                            true
                        )
                    ),
                    "classpath*:/user/*.sql",
                    List.of(
                        resource(
                            "V2__user.sql",
                            true
                        )
                    )
                )
            );

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () ->
                    new R2dbcMigrationCatalog()
                        .read(plan)
            );

        assertTrue(
            error.getMessage()
                .contains(
                    "重复数据库迁移版本 V2"
                )
        );
    }

    @Test
    void shouldRejectCatalogWithoutFormalMigrations() {
        R2dbcMigrationPlan plan =
            plan(
                Map.of(
                    "classpath*:/core/*.sql",
                    List.of(
                        resource(
                            "V0__prepare__premigration.sql",
                            true
                        )
                    ),
                    "classpath*:/user/*.sql",
                    List.of()
                )
            );

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () ->
                    new R2dbcMigrationCatalog()
                        .read(plan)
            );

        assertTrue(
            error.getMessage()
                .contains(
                    "没有找到任何正式"
                )
        );
    }

    private R2dbcMigrationPlan plan(
        Map<String, List<MigrateResource>>
            resources
    ) {
        R2dbcMigrateProperties properties =
            new R2dbcMigrateProperties();

        properties.setResources(
            List.of(
                BunchOfResourcesEntry
                    .ofConventionallyNamedFiles(
                        "classpath*:/core/*.sql",
                        "classpath*:/user/*.sql"
                    )
            )
        );

        MigrateResourceReader reader =
            resourcePath ->
                resources.getOrDefault(
                    resourcePath,
                    List.of()
                );

        return new R2dbcMigrationPlan(
            DatabaseSettings.defaultMysql(),
            properties,
            reader
        );
    }

    private MigrateResource resource(
        String filename,
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
                    "select 1;"
                        .getBytes(
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
