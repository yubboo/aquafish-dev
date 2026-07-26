package com.aquafish.admin.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.aquafish.common.net.TrustedProxyClientIpResolver;
import com.aquafish.core.admin.auth.AdminAuthService;
import com.aquafish.core.admin.auth.AdminAuthToken;
import com.aquafish.core.admin.auth.AdminAuthUser;
import com.aquafish.admin.security.AdminLoginRateLimiter;
import com.aquafish.user.auth.MemberAuthService;
import com.aquafish.user.auth.MemberAuthToken;
import com.aquafish.user.auth.MemberAuthUser;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class AdminAuthControllerCookieTest {

    @Test
    void loginShouldUseHttpOnlyCookieWithoutExposingTokenInJson() {
        AdminAuthService authService = org.mockito.Mockito.mock(AdminAuthService.class);
        AdminAuthUser user = new AdminAuthUser(
            1L,
            "admin",
            "admin@example.com",
            "管理员",
            "",
            "ACTIVE",
            List.of("super_admin"),
            true
        );
        when(authService.login(any(), any())).thenReturn(Mono.just(
            new AdminAuthToken("Bearer", "secret-session-token", "2099-01-01T00:00:00", 3600, user)
        ));

        MemberAuthService memberAuthService = org.mockito.Mockito.mock(MemberAuthService.class);
        MemberAuthUser memberUser = new MemberAuthUser(
            1L, 1L, "AQUA_ADMIN", "admin", "管理员", "", 1L, "member",
            Set.of("super_admin"), Set.of(), false
        );
        when(memberAuthService.issueTrustedWebSession(anyLong(), any(), any(Duration.class)))
            .thenReturn(Mono.just(new MemberAuthToken(
                "member-session-token", "2099-01-01T00:00:00", 3600, memberUser
            )));

        WebTestClient.bindToController(new AdminAuthController(
                authService,
                memberAuthService,
                new AdminLoginRateLimiter(),
                new TrustedProxyClientIpResolver(
                    List.of("127.0.0.1/32", "::1/128")
                )
            ))
            .build()
            .post().uri("/api/admin/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"username\":\"admin\",\"password\":\"password\",\"rememberMe\":false}")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueMatches(
                "Set-Cookie",
                ".*AQUAFISH_ADMIN_SESSION=secret-session-token.*HttpOnly.*SameSite=Lax.*"
            )
            .expectCookie().valueEquals("AQUAFISH_MEMBER_SESSION", "member-session-token")
            .expectBody()
            .jsonPath("$.data.user.username").isEqualTo("admin")
            .jsonPath("$.data.accessToken").doesNotExist();
    }
}
