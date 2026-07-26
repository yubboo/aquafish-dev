package com.aquafish.admin.security;

import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 从 HttpOnly Cookie 或兼容的 Bearer 请求头读取后台会话令牌。
 */
public final class AdminSessionTokenResolver {

    private AdminSessionTokenResolver() {
    }

    /**
     * 从后台请求中解析当前会话令牌。
     *
     * <p>优先读取安装/登录流程写入的 HttpOnly Cookie；同时兼容 API 客户端使用
     * {@code Authorization: Bearer ...}。本方法只提取令牌，签名、有效期及用户状态
     * 仍由 Spring Security 与后台认证服务验证。</p>
     *
     * @param request 当前后台 HTTP 请求
     * @return 去除前缀与空白后的令牌，不存在时返回 {@code null}
     */
    public static String resolve(ServerHttpRequest request) {
        if (request == null) {
            return null;
        }

        HttpCookie cookie = request.getCookies().getFirst(
            AdminSecurityConfiguration.SESSION_COOKIE
        );
        if (cookie != null && !cookie.getValue().isBlank()) {
            return cookie.getValue().trim();
        }

        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String value = authorization.trim();
        return value.regionMatches(true, 0, "Bearer ", 0, 7)
            ? blankToNull(value.substring(7).trim())
            : blankToNull(value);
    }

    /**
     * 把空字符串统一转换为 {@code null}，避免下游误把空令牌当成有效凭据。
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
