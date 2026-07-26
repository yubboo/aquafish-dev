package com.aquafish.boot.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 发行 JAR 的 Vue history 路由回退测试。
 *
 * <p>验证页面路由会进入 index.html，同时确保 API、写请求和没有前端产物的
 * 开发启动不会被错误改写。</p>
 */
class SpaFallbackWebFilterTest {

    @Test
    void setupRouteUsesEmbeddedSpaIndex() {
        assertForwardedPath(
            availableFilter(),
            HttpMethod.GET,
            "/setup",
            "/index.html"
        );
    }

    @Test
    void nestedAdminRouteUsesEmbeddedSpaIndex() {
        assertForwardedPath(
            availableFilter(),
            HttpMethod.GET,
            "/admin/users",
            "/index.html"
        );
    }

    @Test
    void publicRootIsOwnedByThemeController() {
        assertForwardedPath(
            availableFilter(),
            HttpMethod.GET,
            "/",
            "/"
        );
    }

    @Test
    void adminApiIsNeverRewritten() {
        assertForwardedPath(
            availableFilter(),
            HttpMethod.GET,
            "/api/admin/auth/me",
            "/api/admin/auth/me"
        );
    }

    @Test
    void pageWriteRequestIsNeverRewritten() {
        assertForwardedPath(
            availableFilter(),
            HttpMethod.POST,
            "/admin/users",
            "/admin/users"
        );
    }

    @Test
    void backendDevelopmentWithoutIndexKeepsOriginalPath() {
        SpaFallbackWebFilter filter = new SpaFallbackWebFilter(
            new FileSystemResource("missing-admin-index.html")
        );

        assertForwardedPath(
            filter,
            HttpMethod.GET,
            "/setup",
            "/setup"
        );
    }

    private SpaFallbackWebFilter availableFilter() {
        return new SpaFallbackWebFilter(
            new ByteArrayResource("<html></html>".getBytes())
        );
    }

    private void assertForwardedPath(
        SpaFallbackWebFilter filter,
        HttpMethod method,
        String requestPath,
        String expectedPath
    ) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.method(method, requestPath).build()
        );
        AtomicReference<String> actualPath = new AtomicReference<>();
        WebFilterChain chain = filteredExchange -> {
            actualPath.set(
                filteredExchange.getRequest().getURI().getPath()
            );
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        assertEquals(expectedPath, actualPath.get());
    }
}
