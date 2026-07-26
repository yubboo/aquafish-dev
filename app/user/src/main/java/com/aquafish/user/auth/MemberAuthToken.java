package com.aquafish.user.auth;

/**
 * 前台登录成功后的一次性会话签发结果。
 *
 * <p>accessToken 只交给 Controller 写入 HttpOnly Cookie；
 * API 响应不得把该值放进 JSON，数据库只保存其 SHA-256 摘要。</p>
 */
public record MemberAuthToken(
    String accessToken,
    String expiresAt,
    long expiresInSeconds,
    MemberAuthUser user
) {
}
