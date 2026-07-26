package com.aquafish.core.admin.auth;

import java.util.List;

/**
 * 后台登录用户信息。
 *
 * 注意：
 * 这里不返回 password_hash。
 */
public record AdminAuthUser(
    long id,
    String username,
    String email,
    String displayName,
    String avatar,
    String status,
    List<String> roles,
    boolean superAdmin
) {

    public boolean hasAdminAccess() {
        if (roles == null || roles.isEmpty()) {
            return false;
        }

        return roles.contains("super_admin") || roles.contains("admin");
    }
}
