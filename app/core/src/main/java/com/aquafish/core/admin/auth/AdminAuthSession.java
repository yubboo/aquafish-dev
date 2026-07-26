package com.aquafish.core.admin.auth;

import java.time.LocalDateTime;

/**
 * 后台登录会话。
 *
 * 当前阶段：
 * 使用内存保存。
 */
public record AdminAuthSession(
    String token,
    AdminAuthUser user,
    LocalDateTime expiresAt
) {

    public boolean expired() {
        return expiresAt == null || LocalDateTime.now().isAfter(expiresAt);
    }
}
