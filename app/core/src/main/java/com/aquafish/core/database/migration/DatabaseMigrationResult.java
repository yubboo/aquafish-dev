package com.aquafish.core.database.migration;

import com.aquafish.core.database.DatabaseType;

/**
 * 一次受控 R2DBC 数据库迁移的安全结果。
 *
 * <p>结果只包含数据库类型和版本状态，不包含连接地址、账号或密码。</p>
 */
public record DatabaseMigrationResult(
    DatabaseType databaseType,
    String previousVersion,
    String currentVersion,
    int pendingBefore,
    int pendingAfter,
    boolean migrated,
    String message
) {

    public DatabaseMigrationResult {
        previousVersion = safeText(previousVersion);
        currentVersion = safeText(currentVersion);
        message = safeText(message);

        if (databaseType == null) {
            throw new IllegalArgumentException(
                "数据库类型不能为空。"
            );
        }

        if (
            pendingBefore < 0
            || pendingAfter < 0
        ) {
            throw new IllegalArgumentException(
                "待迁移数量不能小于零。"
            );
        }
    }

    private static String safeText(
        String value
    ) {
        return value == null
            ? ""
            : value;
    }
}
