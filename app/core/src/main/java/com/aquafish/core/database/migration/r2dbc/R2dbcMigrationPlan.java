package com.aquafish.core.database.migration.r2dbc;

import com.aquafish.core.database.DatabaseSettings;
import java.util.Objects;
import name.nkonev.r2dbc.migrate.core.R2dbcMigrateProperties;
import name.nkonev.r2dbc.migrate.reader.MigrateResourceReader;

/**
 * Aquafish 单次 R2DBC 数据库迁移计划。
 *
 * <p>
 * 迁移计划把以下内容绑定在一起：
 * </p>
 *
 * <ul>
 *     <li>已经严格校验的数据库配置；</li>
 *     <li>r2dbc-migrate 执行参数；</li>
 *     <li>只允许 Aquafish 安全占位符的资源读取器。</li>
 * </ul>
 */
public record R2dbcMigrationPlan(
    DatabaseSettings settings,
    R2dbcMigrateProperties properties,
    MigrateResourceReader resourceReader
) {

    public R2dbcMigrationPlan {
        Objects.requireNonNull(
            settings,
            "数据库配置不能为空。"
        );

        Objects.requireNonNull(
            properties,
            "R2DBC 迁移配置不能为空。"
        );

        Objects.requireNonNull(
            resourceReader,
            "R2DBC 迁移资源读取器不能为空。"
        );
    }
}
