package com.aquafish.core.admin.auth;

/**
 * 后台登录 Token。
 *
 * 当前阶段使用内存 Token。
 * 后续再升级为数据库 Token / Redis Token / JWT。
 */
public record AdminAuthToken(
    String tokenType,
    String accessToken,
    String expiresAt,
    long expiresInSeconds,
    AdminAuthUser user
) {
}
