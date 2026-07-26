package com.aquafish.boot;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 前台会员 Spring Security、CSRF 和论坛写接口匿名拒绝集成测试。
 */
@SpringBootTest(
    classes = AquafishApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class MemberSecurityIntegrationTest {

    private final WebTestClient client;

    @Autowired
    MemberSecurityIntegrationTest(@LocalServerPort int port) {
        this.client = WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + port)
            .build();
    }

    @Test
    void memberCsrfEndpointShouldIssueDedicatedCookieAndHeader() {
        client.get().uri("/api/member/auth/csrf")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("Cache-Control", "no-store")
            .expectCookie().exists("AQUAFISH_MEMBER_XSRF")
            .expectBody()
            .jsonPath("$.data.token").isNotEmpty()
            .jsonPath("$.data.headerName").isEqualTo("X-AQUAFISH-CSRF");
    }

    @Test
    void memberMeShouldRejectAnonymousRequest() {
        client.get().uri("/api/member/auth/me")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("MEMBER_AUTH_UNAUTHORIZED");
    }

    @Test
    void memberLoginShouldRejectMissingCsrfToken() {
        client.post().uri("/api/member/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"loginName":"member","password":"password","rememberMe":false}
                """)
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.code").isEqualTo("MEMBER_CSRF_INVALID");
    }

    @Test
    void forumPublishShouldRequireAuthenticationAfterCsrfPasses() {
        var csrfResult = client.get().uri("/api/member/auth/csrf")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .returnResult();

        String body = new String(
            csrfResult.getResponseBody(),
            StandardCharsets.UTF_8
        );
        var matcher = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"")
            .matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("会员 CSRF 响应中没有 token：" + body);
        }
        String token = matcher.group(1);
        String cookie = csrfResult.getResponseCookies()
            .getFirst("AQUAFISH_MEMBER_XSRF")
            .getValue();

        client.post().uri("/api/forum/sections/3/threads")
            .cookie("AQUAFISH_MEMBER_XSRF", cookie)
            .header("X-AQUAFISH-CSRF", token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"title\":\"匿名主题\",\"contentText\":\"正文\"}")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("MEMBER_AUTH_UNAUTHORIZED");
    }
}
