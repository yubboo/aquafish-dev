package com.aquafish.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.security.Signature;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class LicenseEnforcementWebFilterTest {

    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");

    @TempDir
    Path tempDir;

    private LicenseEnforcementWebFilter filter;
    private ObjectMapper objectMapper;
    private KeyPair keyPair;
    private LicenseService service;
    private String instanceId;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        LicenseTokenVerifier verifier = new LicenseTokenVerifier(
            objectMapper,
            "aquafish-platform",
            keyPair.getPublic(),
            Duration.ZERO,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        LicenseInstanceIdentityService identity = new LicenseInstanceIdentityService(
            tempDir.resolve("instance.id")
        );
        instanceId = identity.instanceId();
        service = new LicenseService(
            identity,
            new LicenseFileStore(tempDir.resolve("licenses/platform.license")),
            verifier,
            true
        );
        filter = new LicenseEnforcementWebFilter(service, objectMapper);
    }

    @Test
    void blocksPremiumApiWhenLicenseIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/admin/ai/providers").build()
        );
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.filter(exchange, ignored -> {
            chained.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chained.get());
        assertEquals(HttpStatus.LOCKED, exchange.getResponse().getStatusCode());
    }

    @Test
    void allowsBasicAdministrationWhenLicenseIsMissing() {
        assertTrue(requestIsAllowed("/api/admin/users"));
        assertTrue(requestIsAllowed("/api/admin/system/settings"));
    }

    @Test
    void allowsActivationApiWithoutExistingLicense() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/admin/license/status").build()
        );
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.filter(exchange, ignored -> {
            chained.set(true);
            return Mono.empty();
        }).block();

        assertTrue(chained.get());
    }

    @Test
    void allowsOnlineActivationAndRefreshRecoveryApis() {
        assertTrue(requestIsAllowed("/api/admin/license/online/activation"));
        assertTrue(requestIsAllowed("/api/admin/license/online/refresh"));
    }

    @Test
    void allowsLicensedModuleButBlocksUnlicensedModule() throws Exception {
        service.activate(sign(List.of("platform", "forum")));

        assertTrue(requestIsAllowed("/api/admin/forum/posts"));

        MockServerWebExchange exchange = request("/api/admin/ai/providers");
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body.contains("LICENSE_FEATURE_REQUIRED"));
        assertTrue(body.contains("\"requiredFeature\":\"ai\""));
    }

    @Test
    void cmsCompatibilityFeatureGrantsContentAndTheme() throws Exception {
        service.activate(sign(List.of("platform", "cms")));

        assertTrue(requestIsAllowed("/api/admin/content/articles"));
        assertTrue(requestIsAllowed("/api/admin/themes/current"));
        assertFalse(requestIsAllowed("/api/admin/plugins"));
    }

    @Test
    void updateApiIsNotPartOfLicenseBootstrapWhitelist() throws Exception {
        service.activate(sign(List.of("platform")));

        MockServerWebExchange exchange = request("/api/admin/license/updates/check");
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    /** 执行一次模拟请求并返回是否进入了后续业务过滤链。 */
    private boolean requestIsAllowed(String path) {
        AtomicBoolean chained = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get(path).build()
        );
        filter.filter(exchange, ignored -> {
            chained.set(true);
            return Mono.empty();
        }).block();
        return chained.get();
    }

    /** 执行一次被拒绝的模拟请求，供测试同时检查 HTTP 状态和响应体。 */
    private MockServerWebExchange request(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get(path).build()
        );
        filter.filter(exchange, ignored -> Mono.empty()).block();
        return exchange;
    }

    /** 使用测试私钥生成只绑定当前临时实例的 AQF1 授权码。 */
    private String sign(List<String> features) throws Exception {
        LicensePayload payload = new LicensePayload(
            1,
            "license-filter-test",
            "aquafish-platform",
            "professional",
            "Aquafish Filter Test",
            instanceId,
            NOW.minusSeconds(60),
            NOW.minusSeconds(60),
            NOW.plus(Duration.ofDays(365)),
            features
        );
        byte[] bytes = objectMapper.writeValueAsBytes(payload);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(bytes);
        return "AQF1."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }
}
