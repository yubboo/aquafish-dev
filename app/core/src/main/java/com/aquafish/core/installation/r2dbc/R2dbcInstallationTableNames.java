package com.aquafish.core.installation.r2dbc;

import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import java.util.Objects;

/**
 * Aquafish 响应式安装状态仓库使用的真实表名。
 *
 * @param systemInstancesTable system_instances 真实表名
 */
public record R2dbcInstallationTableNames(
    String systemInstancesTable
) {

    public R2dbcInstallationTableNames {
        systemInstancesTable =
            requireIdentifier(
                systemInstancesTable
            );
    }

    /**
     * 根据数据库配置中的表前缀解析真实表名。
     */
    public static R2dbcInstallationTableNames from(
        DatabaseSettings settings
    ) {
        DatabaseSettings safeSettings =
            Objects.requireNonNull(
                settings,
                "数据库配置不能为空。"
            ).normalized();

        if (!safeSettings.hasRequiredFields()) {
            throw new IllegalStateException(
                "数据库安装配置不完整。"
            );
        }

        return new R2dbcInstallationTableNames(
            TableNameResolver.tableName(
                safeSettings.tablePrefix(),
                "system_instances"
            )
        );
    }

    private static String requireIdentifier(
        String value
    ) {
        if (
            value == null
            || value.isBlank()
            || value.length() > 64
        ) {
            throw new IllegalArgumentException(
                "安装状态表名非法。"
            );
        }

        String normalized =
            value.trim();

        if (!normalized.equals(value)) {
            throw new IllegalArgumentException(
                "安装状态表名不能包含首尾空格。"
            );
        }

        for (
            int index = 0;
            index < normalized.length();
            index++
        ) {
            char current =
                normalized.charAt(index);

            boolean valid =
                current == '_'
                    || Character.isLetterOrDigit(
                        current
                    );

            if (!valid) {
                throw new IllegalArgumentException(
                    "安装状态表名只能包含字母、数字和下划线。"
                );
            }
        }

        return normalized;
    }
}
