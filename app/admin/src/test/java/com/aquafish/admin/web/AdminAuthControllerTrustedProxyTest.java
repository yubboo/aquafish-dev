package com.aquafish.admin.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquafish.admin.security.AdminLoginRateLimiter;
import com.aquafish.common.net.TrustedProxyClientIpResolver;
import com.aquafish.core.admin.auth.AdminAuthService;
import com.aquafish.core.admin.auth.AdminLoginMetadata;
import com.aquafish.core.admin.auth.AdminLoginRequest;
import com.aquafish.user.auth.MemberAuthService;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 后台登录可信代理接入测试。
 */
class AdminAuthControllerTrustedProxyTest {

    /**
     * 非可信直连来源伪造代理头时，必须继续使用 TCP 来源 IP。
     */
    @Test
    void shouldIgnoreForwardedHeadersFromUntrustedPeer() {
        AdminAuthController controller =
            controller(
                new TrustedProxyClientIpResolver(
                    List.of("127.0.0.1/32")
                ),
                mock(AdminLoginRateLimiter.class)
            );

        MockServerHttpRequest request =
            MockServerHttpRequest
                .get("/api/admin/auth/login")
                .remoteAddress(
                    new InetSocketAddress(
                        "198.51.100.9",
                        4567
                    )
                )
                .header(
                    "X-Forwarded-For",
                    "203.0.113.10"
                )
                .header(
                    "X-Real-IP",
                    "203.0.113.11"
                )
                .build();

        AdminLoginMetadata metadata =
            controller.loginMetadata(request);

        assertEquals(
            "198.51.100.9",
            metadata.clientIp()
        );
    }

    /**
     * 可信代理链解析出的真实客户端 IP 必须用于后台登录限流。
     */
    @Test
    void shouldUseResolvedClientIpForLoginRateLimit() {
        AdminAuthService authService =
            mock(AdminAuthService.class);

        MemberAuthService memberAuthService =
            mock(MemberAuthService.class);

        AdminLoginRateLimiter rateLimiter =
            mock(AdminLoginRateLimiter.class);

        when(
            authService.login(
                any(),
                any()
            )
        ).thenReturn(
            Mono.error(
                new IllegalStateException(
                    "测试登录拒绝"
                )
            )
        );

        AdminAuthController controller =
            new AdminAuthController(
                authService,
                memberAuthService,
                rateLimiter,
                new TrustedProxyClientIpResolver(
                    List.of("127.0.0.1/32")
                )
            );

        MockServerHttpRequest request =
            MockServerHttpRequest
                .post("/api/admin/auth/login")
                .remoteAddress(
                    new InetSocketAddress(
                        "127.0.0.1",
                        4567
                    )
                )
                .header(
                    "X-Forwarded-For",
                    "203.0.113.20, 127.0.0.1"
                )
                .build();

        controller.login(
            new AdminLoginRequest(
                "admin",
                "password",
                false
            ),
            MockServerWebExchange.from(
                request
            )
        ).block();

        verify(rateLimiter).requireAllowed(
            "203.0.113.20",
            "admin"
        );

        verify(rateLimiter).recordFailure(
            "203.0.113.20",
            "admin"
        );
    }

    private AdminAuthController controller(
        TrustedProxyClientIpResolver resolver,
        AdminLoginRateLimiter rateLimiter
    ) {
        return new AdminAuthController(
            mock(AdminAuthService.class),
            mock(MemberAuthService.class),
            rateLimiter,
            resolver
        );
    }
}
