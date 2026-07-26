package com.aquafish.core.database.migration.r2dbc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import name.nkonev.r2dbc.migrate.core.MigrationMetadata;
import name.nkonev.r2dbc.migrate.core.MigrationMetadataFactory;
import name.nkonev.r2dbc.migrate.reader.MigrateResource;
import org.springframework.stereotype.Component;

/**
 * Aquafish R2DBC 数据库迁移版本目录。
 *
 * <p>
 * 本组件只读取 classpath 中的迁移资源，
 * 不连接数据库，也不执行任何 SQL。
 * </p>
 *
 * <p>
 * 主要用途：
 * </p>
 *
 * <ul>
 *     <li>确定当前代码的最高数据库版本；</li>
 *     <li>计算数据库还有多少待执行迁移；</li>
 *     <li>检测模块之间是否出现重复版本号；</li>
 *     <li>为旧 Flyway 历史转换提供版本白名单。</li>
 * </ul>
 */
@Component
public final class R2dbcMigrationCatalog {

    /**
     * 读取一份迁移计划中的全部正式迁移。
     */
    public R2dbcMigrationCatalogSnapshot read(
        R2dbcMigrationPlan plan
    ) {
        R2dbcMigrationPlan safePlan =
            Objects.requireNonNull(
                plan,
                "R2DBC 迁移计划不能为空。"
            );

        Map<Long, R2dbcMigrationCatalogEntry>
            entriesByVersion =
            new LinkedHashMap<>();

        for (
            var resourceEntry
                : safePlan
                    .properties()
                    .getResources()
        ) {
            if (resourceEntry == null) {
                throw new IllegalStateException(
                    "R2DBC 迁移资源配置不能为空。"
                );
            }

            for (
                String resourcePath
                    : resourceEntry
                        .getResourcesPaths()
            ) {
                List<MigrateResource> resources =
                    safePlan
                        .resourceReader()
                        .getResources(
                            resourcePath
                        );

                if (resources == null) {
                    throw new IllegalStateException(
                        "迁移资源读取器返回了 null："
                            + resourcePath
                    );
                }

                for (
                    MigrateResource resource
                        : resources
                ) {
                    addResource(
                        entriesByVersion,
                        resource
                    );
                }
            }
        }

        List<R2dbcMigrationCatalogEntry>
            sortedEntries =
            new ArrayList<>(
                entriesByVersion.values()
            );

        sortedEntries.sort(
            Comparator.comparingLong(
                R2dbcMigrationCatalogEntry
                    ::version
            )
        );

        if (sortedEntries.isEmpty()) {
            throw new IllegalStateException(
                "没有找到任何正式 R2DBC 数据库迁移脚本。"
            );
        }

        long latestVersion =
            sortedEntries
                .getLast()
                .version();

        return new R2dbcMigrationCatalogSnapshot(
            sortedEntries,
            latestVersion
        );
    }

    private void addResource(
        Map<Long, R2dbcMigrationCatalogEntry>
            entriesByVersion,
        MigrateResource resource
    ) {
        if (
            resource == null
            || !resource.isReadable()
        ) {
            return;
        }

        String filename =
            resource.getFilename();

        MigrationMetadata metadata =
            MigrationMetadataFactory
                .getMigrationMetadata(
                    filename
                );

        /*
         * premigration 脚本用于迁移前准备，
         * 不属于数据库正式版本。
         */
        if (metadata.isPremigration()) {
            return;
        }

        R2dbcMigrationCatalogEntry entry =
            new R2dbcMigrationCatalogEntry(
                metadata.getVersion(),
                metadata.getDescription(),
                filename
            );

        R2dbcMigrationCatalogEntry previous =
            entriesByVersion.putIfAbsent(
                entry.version(),
                entry
            );

        if (previous != null) {
            throw new IllegalStateException(
                "检测到重复数据库迁移版本 V"
                    + entry.version()
                    + "："
                    + previous.filename()
                    + " 与 "
                    + entry.filename()
            );
        }
    }
}
