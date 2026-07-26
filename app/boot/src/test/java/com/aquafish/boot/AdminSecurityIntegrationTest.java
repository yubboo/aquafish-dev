package com.aquafish.boot;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    classes = AquafishApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class AdminSecurityIntegrationTest {

    private final WebTestClient client;
    @Autowired
    AdminSecurityIntegrationTest(@LocalServerPort int port) {
        this.client = WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + port)
            .build();
    }

    @Test
    void protectedAdminApiShouldRejectAnonymousRequest() {
        client.get().uri("/api/admin/users")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("ADMIN_AUTH_UNAUTHORIZED");
    }

    @Test
    void csrfEndpointShouldIssueTokenAndCookie() {
        client.get().uri("/api/admin/auth/csrf")
            .exchange()
            .expectStatus().isOk()
            .expectCookie().exists("XSRF-TOKEN")
            .expectBody()
            .jsonPath("$.data.token").isNotEmpty()
            .jsonPath("$.data.headerName").isEqualTo("X-XSRF-TOKEN");
    }

    @Test
    void csrfEndpointShouldIgnoreStaleAdminSessionCookie() {
        client.get().uri("/api/admin/auth/csrf")
            .cookie("AQUAFISH_ADMIN_SESSION", "stale-session-token")
            .exchange()
            .expectStatus().isOk()
            .expectCookie().exists("XSRF-TOKEN")
            .expectBody()
            .jsonPath("$.data.token").isNotEmpty();
    }

    @Test
    void protectedApiShouldTurnStaleSessionIntoUnauthorizedAndClearCookie() {
        client.get().uri("/api/admin/auth/me")
            .cookie("AQUAFISH_ADMIN_SESSION", "stale-session-token")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectCookie().maxAge("AQUAFISH_ADMIN_SESSION", Duration.ZERO)
            .expectBody()
            .jsonPath("$.code").isEqualTo("ADMIN_AUTH_UNAUTHORIZED");
    }

    @Test
    void loginShouldRejectRequestWithoutCsrfToken() {
        client.post().uri("/api/admin/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"username\":\"admin\",\"password\":\"password\",\"rememberMe\":false}")
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.code").isEqualTo("ADMIN_CSRF_INVALID");
    }

    @Test
    void loginRequestWithMatchingCsrfTokenShouldReachAuthenticationService() throws Exception {
        var csrfResult = client.get().uri("/api/admin/auth/csrf")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .returnResult();

        String body = new String(csrfResult.getResponseBody(), StandardCharsets.UTF_8);
        var matcher = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("CSRF 响应中没有 token。" + body);
        }
        String token = matcher.group(1);
        String cookie = csrfResult.getResponseCookies().getFirst("XSRF-TOKEN").getValue();

        client.post().uri("/api/admin/auth/login")
            .cookie("XSRF-TOKEN", cookie)
            .header("X-XSRF-TOKEN", token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"username\":\"missing-user\",\"password\":\"password\",\"rememberMe\":false}")
            .exchange()
            .expectStatus().value(status -> assertNotEquals(403, status));
    }
}
