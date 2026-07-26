package com.aquafish.common.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 可信代理客户端 IP 解析器测试。
 */
class TrustedProxyClientIpResolverTest {

    /**
     * 非可信来源携带的转发头必须被忽略。
     */
    @Test
    void shouldIgnoreSpoofedHeadersFromUntrustedRemoteAddress() {
        TrustedProxyClientIpResolver resolver =
            new TrustedProxyClientIpResolver(
                List.of("127.0.0.1/32")
            );

        assertEquals(
            "198.51.100.9",
            resolver.resolve(
                "198.51.100.9",
                "203.0.113.10",
                "203.0.113.11"
            )
        );
    }

    /**
     * 只有直连来源可信时才读取 X-Real-IP。
     */
    @Test
    void shouldUseRealIpOnlyWhenDirectPeerIsTrusted() {
        TrustedProxyClientIpResolver resolver =
            new TrustedProxyClientIpResolver(
                List.of(
                    "127.0.0.0/8",
                    "::1/128"
                )
            );

        assertEquals(
            "203.0.113.10",
            resolver.resolve(
                "127.0.0.1",
                "",
                "203.0.113.10"
            )
        );

        assertEquals(
            "203.0.113.11",
            resolver.resolve(
                "0:0:0:0:0:0:0:1",
                null,
                "203.0.113.11"
            )
        );
    }

    /**
     * 多级代理链必须从右向左剥离可信代理。
     */
    @Test
    void shouldWalkForwardedChainFromRightToLeft() {
        TrustedProxyClientIpResolver resolver =
            new TrustedProxyClientIpResolver(
                List.of(
                    "10.0.0.0/8",
                    "172.16.0.0/12"
                )
            );

        assertEquals(
            "198.51.100.20",
            resolver.resolve(
                "172.18.0.5",
                "192.0.2.99, " +
                    "198.51.100.20, " +
                    "10.0.0.8",
                "203.0.113.50"
            )
        );
    }

    /**
     * 代理头无效时必须回退到真实 TCP 来源。
     */
    @Test
    void shouldFallbackToRemoteAddressWhenHeadersAreInvalid() {
        TrustedProxyClientIpResolver resolver =
            new TrustedProxyClientIpResolver(
                List.of("127.0.0.1")
            );

        assertEquals(
            "127.0.0.1",
            resolver.resolve(
                "127.0.0.1:8080",
                "unknown, invalid-host",
                "invalid-host"
            )
        );
    }

    /**
     * IPv4、IPv6 和端口必须被统一规范化。
     */
    @Test
    void shouldNormalizeIpv4AndIpv6Addresses() {
        TrustedProxyClientIpResolver resolver =
            new TrustedProxyClientIpResolver(
                List.of(
                    "127.0.0.0/8",
                    "::1/128"
                )
            );

        assertEquals(
            "127.0.0.1",
            resolver.resolve(
                "127.000.000.001:9000",
                null,
                null
            )
        );

        assertEquals(
            "::1",
            resolver.resolve(
                "[0:0:0:0:0:0:0:1]:8080",
                null,
                null
            )
        );
    }

    /**
     * IPv4 和 IPv6 CIDR 必须正确匹配边界。
     */
    @Test
    void shouldMatchConfiguredIpv4AndIpv6Networks() {
        TrustedProxyClientIpResolver resolver =
            new TrustedProxyClientIpResolver(
                List.of(
                    "172.16.0.0/12",
                    "fd00::/8"
                )
            );

        assertTrue(
            resolver.isTrustedProxy(
                "172.31.255.254"
            )
        );

        assertFalse(
            resolver.isTrustedProxy(
                "172.32.0.1"
            )
        );

        assertTrue(
            resolver.isTrustedProxy(
                "fd12:3456::1"
            )
        );

        assertFalse(
            resolver.isTrustedProxy(
                "fe80::1"
            )
        );
    }

    /**
     * 应用直接收到 HTTPS 时，不依赖代理配置。
     */
    @Test
    void shouldTrustDirectHttpsRequest() {
        TrustedProxyClientIpResolver resolver =
            new TrustedProxyClientIpResolver(
                List.of()
            );

        assertTrue(
            resolver.isSecureRequest(
                "https",
                "198.51.100.9",
                null
            )
        );
    }

    /**
     * 非可信来源伪造 HTTPS 代理头时必须忽略。
     */
    @Test
    void shouldIgnoreSpoofedForwardedProto() {
        TrustedProxyClientIpResolver resolver =
            new TrustedProxyClientIpResolver(
                List.of("127.0.0.1/32")
            );

        assertFalse(
            resolver.isSecureRequest(
                "http",
                "198.51.100.9",
                "https"
            )
        );
    }

    /**
     * 可信代理声明 HTTPS 时应启用 Secure Cookie。
     */
    @Test
    void shouldTrustHttpsFromConfiguredProxy() {
        TrustedProxyClientIpResolver resolver =
            new TrustedProxyClientIpResolver(
                List.of("127.0.0.1/32")
            );

        assertTrue(
            resolver.isSecureRequest(
                "http",
                "127.0.0.1",
                "https"
            )
        );
    }

    /**
     * 多级协议链必须使用最靠近应用的最右侧值。
     */
    @Test
    void shouldUseNearestForwardedProtocol() {
        TrustedProxyClientIpResolver resolver =
            new TrustedProxyClientIpResolver(
                List.of("127.0.0.1/32")
            );

        assertFalse(
            resolver.isSecureRequest(
                "http",
                "127.0.0.1",
                "https, http"
            )
        );

        assertTrue(
            resolver.isSecureRequest(
                "http",
                "127.0.0.1",
                "http, https"
            )
        );
    }

    /**
     * 非法可信代理配置必须让程序明确失败，不能静默放行。
     */
    @Test
    void shouldRejectInvalidTrustedProxyConfiguration() {
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new TrustedProxyClientIpResolver(
                    List.of("127.0.0.1/99")
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new TrustedProxyClientIpResolver(
                    List.of(
                        "proxy.example.com"
                    )
                )
        );
    }
}
