package com.aquafish.core.install;

/**
 * 管理员账号创建结果。
 *
 * 当前阶段：
 * Step 17-22-5：初始化管理员账号。
 *
 * 注意：
 * 不返回密码，不返回 password_hash。
 */
public record SetupAdminCreateResult(
    boolean installed,
    boolean created,
    long userId,
    String username,
    String email,
    String displayName,
    String roleKey,
    String note
) {
}
