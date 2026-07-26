package com.aquafish.core.database.migration.r2dbc;

import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.migration.DatabaseMigrationLocationRegistry;
import java.util.List;
import java.util.Objects;
import name.nkonev.r2dbc.migrate.core.BunchOfResourcesEntry;
import name.nkonev.r2dbc.migrate.core.Dialect;
import name.nkonev.r2dbc.migrate.core.R2dbcMigrateProperties;
import org.springframework.stereotype.Component;

/**
 * Aquafish R2DBC 数据库迁移工厂。
 *
 * <p>
 * 该工厂只构造迁移计划，不会连接数据库，
 * 也不会在 Spring 启动阶段自动执行迁移。
 * </p>
 */
@Component
public final class R2dbcMigrationFactory {

    private final DatabaseMigrationLocationRegistry
        locationRegistry;

    public R2dbcMigrationFactory(
        DatabaseMigrationLocationRegistry
            locationRegistry
    ) {
        this.locationRegistry =
            Objects.requireNonNull(
                locationRegistry,
                "数据库迁移目录注册中心不能为空。"
            );
    }

    /**
     * 根据安装数据库配置构造迁移计划。
     */
    public R2dbcMigrationPlan create(
        DatabaseSettings settings
    ) {
        DatabaseSettings safeSettings =
            requireSettings(settings);

        String tablePrefix =
            TableNameResolver
                .normalizeConfiguredPrefix(
                    safeSettings.tablePrefix()
                );

        List<String> locations =
            locationRegistry.locations(
                safeSettings.type()
            );

        if (
            locations == null
            || locations.isEmpty()
        ) {
            throw new IllegalStateException(
                "当前数据库类型没有注册任何迁移目录："
                    + safeSettings.type()
            );
        }

        List<String> resourcePatterns =
            locations
                .stream()
                .map(
                    this::toResourcePattern
                )
                .toList();

        BunchOfResourcesEntry resources =
            BunchOfResourcesEntry
                .ofConventionallyNamedFiles(
                    resourcePatterns.toArray(
                        String[]::new
                    )
                );

        R2dbcMigrateProperties properties =
            new R2dbcMigrateProperties();

        /*
         * 这里构造的是安装流程显式调用的迁移参数，
         * 因此必须启用。
         *
         * application.yml 中的 enable=false
         * 只负责禁止 Spring 启动时自动执行。
         */
        properties.setEnable(true);

        /*
         * 数据库连接已经由安装向导单独验证。
         *
         * 禁止在迁移工具内部进行数百次长期等待，
         * 避免安装请求长时间挂起。
         */
        properties.setWaitForDatabase(false);

        properties.setDialect(
            dialect(
                safeSettings.type()
            )
        );

        properties.setResources(
            List.of(resources)
        );

        properties.setMigrationsTable(
            tablePrefix
                + R2dbcMigrationTableNames
                    .MIGRATIONS_LOGICAL_NAME
        );

        properties.setMigrationsLockTable(
            tablePrefix
                + R2dbcMigrationTableNames
                    .MIGRATIONS_LOCK_LOGICAL_NAME
        );

        /*
         * 优先使用数据库自身的并发锁能力。
         */
        properties.setPreferDbSpecificLock(
            true
        );

        /*
         * SQL 只允许 Aquafish 自己控制的占位符。
         *
         * 禁止从系统属性和环境变量向 SQL 注入内容。
         */
        properties.setUseEnvironmentSubstitutor(
            false
        );

        properties.setUseSystemPropertiesSubstitutor(
            false
        );

        return new R2dbcMigrationPlan(
            safeSettings,
            properties,
            new AquafishR2dbcMigrationResourceReader(
                tablePrefix,
                safeSettings.name()
            )
        );
    }

    private DatabaseSettings requireSettings(
        DatabaseSettings settings
    ) {
        if (settings == null) {
            throw new IllegalStateException(
                "数据库配置不能为空。"
            );
        }

        DatabaseSettings normalized =
            settings.normalized();

        if (!normalized.hasRequiredFields()) {
            throw new IllegalStateException(
                "数据库连接配置不完整。"
            );
        }

        return normalized;
    }

    private Dialect dialect(
        DatabaseType databaseType
    ) {
        return switch (
            Objects.requireNonNull(
                databaseType,
                "数据库类型不能为空。"
            )
        ) {
            case MYSQL, MARIADB ->
                Dialect.MYSQL;

            case POSTGRESQL ->
                Dialect.POSTGRESQL;
        };
    }

    /**
     * 把现有模块目录转换为 Spring Classpath SQL 通配符。
     *
     * <p>
     * 输入：
     * classpath:db/migration/core/mysql
     * </p>
     *
     * <p>
     * 输出：
     * classpath*:/db/migration/core/mysql/*.sql
     * </p>
     */
    private String toResourcePattern(
        String location
    ) {
        if (
            location == null
            || location.isBlank()
        ) {
            throw new IllegalStateException(
                "数据库迁移目录不能为空。"
            );
        }

        if (
            !location.startsWith(
                "classpath:"
            )
        ) {
            throw new IllegalStateException(
                "R2DBC 迁移目录必须以 classpath: 开头："
                    + location
            );
        }

        String classpathLocation =
            location.substring(
                "classpath:".length()
            );

        if (
            !classpathLocation.startsWith(
                "/"
            )
        ) {
            classpathLocation =
                "/" + classpathLocation;
        }

        while (
            classpathLocation.endsWith("/")
        ) {
            classpathLocation =
                classpathLocation.substring(
                    0,
                    classpathLocation.length() - 1
                );
        }

        return "classpath*:"
            + classpathLocation
            + "/*.sql";
    }
}
