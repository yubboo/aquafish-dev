package com.aquafish.user.security;

import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 从前台会员 HttpOnly Cookie 或 Bearer 请求头提取原始会话令牌。
 *
 * <p>本类只提取，不信任令牌内容；有效期、撤销、用户状态和封禁仍由
 * {@code MemberAuthService} 查询数据库后决定。</p>
 */
public final class MemberSessionTokenResolver {

    private MemberSessionTokenResolver() {
    }

    public static String resolve(ServerHttpRequest request) {
        if (request == null) {
            return null;
        }

        HttpCookie cookie = request.getCookies().getFirst(
            MemberSecurityConfiguration.SESSION_COOKIE
        );
        if (cookie != null && !cookie.getValue().isBlank()) {
            return cookie.getValue().strip();
        }

        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String value = authorization.strip();
        return value.regionMatches(true, 0, "Bearer ", 0, 7)
            ? blankToNull(value.substring(7).strip())
            : blankToNull(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
