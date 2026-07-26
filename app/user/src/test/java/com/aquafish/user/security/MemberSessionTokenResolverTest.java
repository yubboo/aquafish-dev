package com.aquafish.user.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

/**
 * 前台会员 Cookie/Bearer 会话令牌提取测试。
 */
class MemberSessionTokenResolverTest {

    @Test
    void cookieShouldTakePriorityOverBearerHeader() {
        var request = MockServerHttpRequest.get("/api/member/auth/me")
            .cookie(new org.springframework.http.HttpCookie(
                MemberSecurityConfiguration.SESSION_COOKIE,
                "cookie-token"
            ))
            .header("Authorization", "Bearer header-token")
            .build();

        assertEquals("cookie-token", MemberSessionTokenResolver.resolve(request));
    }

    @Test
    void shouldReadBearerTokenForApiClient() {
        var request = MockServerHttpRequest.get("/api/member/auth/me")
            .header("Authorization", "Bearer api-token")
            .build();

        assertEquals("api-token", MemberSessionTokenResolver.resolve(request));
    }

    @Test
    void shouldReturnNullWhenRequestHasNoToken() {
        assertNull(MemberSessionTokenResolver.resolve(
            MockServerHttpRequest.get("/api/member/auth/me").build()
        ));
    }
}
