package com.aquafish.core.install;

/**
 * 管理员账号创建预览。
 *
 * 当前阶段：
 * Step 17-22-5：初始化管理员账号。
 *
 * 注意：
 * 不返回密码。
 */
public record SetupAdminPreview(
    boolean installed,
    boolean connected,
    boolean coreTablesReady,
    boolean adminExists,
    boolean canCreate,
    String username,
    String email,
    String usersTable,
    String rolesTable,
    String userRolesTable,
    String note,
    String errorMessage
) {
}
