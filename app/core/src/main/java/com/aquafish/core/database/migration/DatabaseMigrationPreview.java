package com.aquafish.core.database.migration;

import com.aquafish.core.database.DatabaseSchemaTableStatus;
import java.util.List;

/**
 * Aquafish 数据库迁移预览结果。
 *
 * <p>该对象只承载响应式迁移检查结果，不执行数据库操作。</p>
 *
 * @param connected 数据库是否连接成功
 * @param canMigrate 当前状态是否允许执行正式迁移
 * @param migrationsTableExists R2DBC 迁移历史表是否存在
 * @param unmanagedDatabase 是否为非空且没有 R2DBC 迁移历史的数据库
 * @param migrationsTable 带表前缀的 R2DBC 迁移历史表名称
 * @param currentVersion 当前已执行的最高迁移版本
 * @param pendingMigrations 尚未执行的迁移数量
 * @param tables 数据库表检查状态
 * @param note 迁移预览说明
 * @param errorMessage 检查失败时的安全错误信息
 */
public record DatabaseMigrationPreview(
    boolean connected,
    boolean canMigrate,
    boolean migrationsTableExists,
    boolean unmanagedDatabase,
    String migrationsTable,
    String currentVersion,
    int pendingMigrations,
    List<DatabaseSchemaTableStatus> tables,
    String note,
    String errorMessage
) {

    public DatabaseMigrationPreview {
        if (pendingMigrations < 0) {
            throw new IllegalArgumentException(
                "待执行迁移数量不能小于零。"
            );
        }

        tables =
            tables == null
                ? List.of()
                : List.copyOf(tables);

        migrationsTable =
            safeText(migrationsTable);

        currentVersion =
            safeText(currentVersion);

        note =
            safeText(note);

        errorMessage =
            safeText(errorMessage);
    }

    public boolean hasError() {
        return !errorMessage.isBlank();
    }

    public boolean hasPendingMigrations() {
        return pendingMigrations > 0;
    }

    public boolean upToDate() {
        return connected
            && migrationsTableExists
            && pendingMigrations == 0
            && !hasError();
    }

    /**
     * 开发阶段不接管非空未受管数据库，
     * 应清空数据库后重新初始化。
     */
    public boolean requiresDatabaseReset() {
        return connected
            && unmanagedDatabase
            && !migrationsTableExists;
    }

    private static String safeText(
        String value
    ) {
        return value == null
            ? ""
            : value;
    }
}
