package com.aquafish.core.database.migration.r2dbc;

/**
 * Aquafish 单个 R2DBC 数据库迁移版本。
 *
 * @param version 迁移版本数字
 * @param description 迁移说明
 * @param filename SQL 文件名
 */
public record R2dbcMigrationCatalogEntry(
    long version,
    String description,
    String filename
) {

    public R2dbcMigrationCatalogEntry {
        if (version <= 0) {
            throw new IllegalArgumentException(
                "正式数据库迁移版本必须大于零。"
            );
        }

        description =
            requireText(
                description,
                "数据库迁移说明不能为空。"
            );

        filename =
            requireText(
                filename,
                "数据库迁移文件名不能为空。"
            );
    }

    private static String requireText(
        String value,
        String message
    ) {
        if (
            value == null
            || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                message
            );
        }

        return value.trim();
    }
}
