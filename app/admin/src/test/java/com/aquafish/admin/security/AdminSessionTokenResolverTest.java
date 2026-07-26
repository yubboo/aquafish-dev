package com.aquafish.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class AdminSessionTokenResolverTest {

    @Test
    void shouldPreferHttpOnlySessionCookie() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/users")
            .cookie(ResponseCookie.from(AdminSecurityConfiguration.SESSION_COOKIE, "cookie-token").build())
            .header("Authorization", "Bearer header-token")
            .build();

        assertEquals("cookie-token", AdminSessionTokenResolver.resolve(request));
    }

    @Test
    void shouldKeepBearerHeaderAsApiCompatibility() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/users")
            .header("Authorization", "Bearer header-token")
            .build();

        assertEquals("header-token", AdminSessionTokenResolver.resolve(request));
    }

    @Test
    void shouldReturnNullWhenSessionIsMissing() {
        assertNull(AdminSessionTokenResolver.resolve(
            MockServerHttpRequest.get("/api/admin/users").build()
        ));
    }
}
