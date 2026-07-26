package com.aquafish.core.database;

/**
 * 数据库表初始化状态。
 *
 * 当前阶段：
 * Step 17-22-4：数据库初始化表结构第一版。
 */
public record DatabaseSchemaTableStatus(
    String logicalName,
    String tableName,
    boolean exists,
    String action
) {
}
