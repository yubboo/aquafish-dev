package com.aquafish.user.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.aquafish.common.net.TrustedProxyClientIpResolver;
import com.aquafish.user.auth.MemberAuthService;
import com.aquafish.user.auth.MemberLoginMetadata;
import com.aquafish.user.auth.MemberRegistrationService;
import com.aquafish.user.security.IpBanLookupService;
import com.aquafish.user.security.MemberLoginRateLimiter;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

/**
 * 会员登录和注册可信代理接入测试。
 *
 * <p>测试只验证 Controller 生成的安全元数据；
 * 登录限流、注册限流、IP 封禁和数据库会话均复用该元数据。</p>
 */
class MemberTrustedProxyIntegrationTest {

    /**
     * 非可信来源伪造代理头时，登录必须使用真实 TCP 来源。
     */
    @Test
    void loginShouldIgnoreSpoofedForwardedHeaders() {
        MemberAuthController controller =
            memberAuthController(
                resolver("127.0.0.1/32")
            );

        MockServerHttpRequest request =
            MockServerHttpRequest
                .post("/api/member/auth/login")
                .remoteAddress(
                    new InetSocketAddress(
                        "198.51.100.9",
                        8520
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
                .header(
                    "User-Agent",
                    "Aquafish-Test"
                )
                .build();

        MemberLoginMetadata metadata =
            controller.metadata(request);

        assertEquals(
            "198.51.100.9",
            metadata.ipAddress()
        );

        assertEquals(
            "Aquafish-Test",
            metadata.userAgent()
        );
    }

    /**
     * 可信代理后的会员登录必须使用代理链中的真实客户端地址。
     */
    @Test
    void loginShouldUseClientIpBehindTrustedProxy() {
        MemberAuthController controller =
            memberAuthController(
                resolver(
                    "127.0.0.1/32",
                    "10.0.0.0/8"
                )
            );

        MockServerHttpRequest request =
            MockServerHttpRequest
                .post("/api/member/auth/login")
                .remoteAddress(
                    new InetSocketAddress(
                        "127.0.0.1",
                        8520
                    )
                )
                .header(
                    "X-Forwarded-For",
                    "203.0.113.20, 10.0.0.8"
                )
                .build();

        assertEquals(
            "203.0.113.20",
            controller
                .metadata(request)
                .ipAddress()
        );
    }

    /**
     * 可信代理后的注册必须使用同一个真实客户端地址。
     */
    @Test
    void registrationShouldUseClientIpBehindTrustedProxy() {
        MemberRegistrationController controller =
            memberRegistrationController(
                resolver(
                    "127.0.0.1/32",
                    "172.16.0.0/12"
                )
            );

        MockServerHttpRequest request =
            MockServerHttpRequest
                .post("/api/member/auth/register")
                .remoteAddress(
                    new InetSocketAddress(
                        "127.0.0.1",
                        8520
                    )
                )
                .header(
                    "X-Forwarded-For",
                    "198.51.100.30, 172.18.0.5"
                )
                .build();

        assertEquals(
            "198.51.100.30",
            controller
                .metadata(request)
                .ipAddress()
        );
    }

    /**
     * 非可信来源的注册请求同样不能伪造客户端地址。
     */
    @Test
    void registrationShouldIgnoreSpoofedForwardedHeaders() {
        MemberRegistrationController controller =
            memberRegistrationController(
                resolver("127.0.0.1/32")
            );

        MockServerHttpRequest request =
            MockServerHttpRequest
                .post("/api/member/auth/register")
                .remoteAddress(
                    new InetSocketAddress(
                        "192.0.2.50",
                        8520
                    )
                )
                .header(
                    "X-Forwarded-For",
                    "203.0.113.99"
                )
                .build();

        assertEquals(
            "192.0.2.50",
            controller
                .metadata(request)
                .ipAddress()
        );
    }

    private TrustedProxyClientIpResolver resolver(
        String... cidrs
    ) {
        return new TrustedProxyClientIpResolver(
            List.of(cidrs)
        );
    }

    private MemberAuthController memberAuthController(
        TrustedProxyClientIpResolver resolver
    ) {
        return new MemberAuthController(
            mock(MemberAuthService.class),
            mock(MemberLoginRateLimiter.class),
            mock(IpBanLookupService.class),
            resolver
        );
    }

    private MemberRegistrationController
        memberRegistrationController(
            TrustedProxyClientIpResolver resolver
        ) {
        return new MemberRegistrationController(
            mock(MemberRegistrationService.class),
            mock(MemberAuthService.class),
            mock(MemberLoginRateLimiter.class),
            mock(IpBanLookupService.class),
            resolver
        );
    }
}
