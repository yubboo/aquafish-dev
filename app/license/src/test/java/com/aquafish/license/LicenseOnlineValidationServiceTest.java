package com.aquafish.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 覆盖 AQL1 在线授权的签名刷新、吊销即时生效、停机租约和缓存防篡改边界。
 *
 * <p>测试服务器使用每次生成的 Ed25519 临时密钥，不读取任何真实授权中心密钥。</p>
 */
class LicenseOnlineValidationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");
    private static final String KEY_ID = "test-online-current";
    private static final String INSTANCE_ID = "4335069a-e7a4-4416-bf8d-96d488260a67";

    @TempDir
    Path tempDir;

    private final AtomicReference<String> remoteState = new AtomicReference<>("ACTIVE");
    private final AtomicReference<String> forcedNonce = new AtomicReference<>();
    private final AtomicLong sequence = new AtomicLong(1);
    private HttpServer server;
    private MutableClock clock;
    private LicenseOnlineValidationService service;
    private Path cacheFile;
    private ObjectMapper mapper;
    private PrivateKey privateKey;
    private OnlineLeaseVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        clock = new MutableClock(NOW);
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        privateKey = keyPair.getPrivate();
        verifier = new OnlineLeaseVerifier(
            mapper,
            Map.of(KEY_ID, keyPair.getPublic()),
            Duration.ofMinutes(5),
            clock
        );

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/licenses/check", exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            JsonNode requestJson = mapper.readTree(request);
            assertEquals("license-online-test", requestJson.path("licenseId").asText());
            String requestNonce = requestJson.path("nonce").asText();
            String responseNonce = forcedNonce.get() == null ? requestNonce : forcedNonce.get();
            String state = remoteState.get();
            OnlineLeasePayload payload = new OnlineLeasePayload(
                1,
                "ONLINE_STATUS",
                KEY_ID,
                "license-online-test",
                INSTANCE_ID,
                state,
                "remote " + state,
                clock.instant(),
                clock.instant().plus(Duration.ofHours(1)),
                sequence.get(),
                responseNonce,
                60
            );
            String lease = signLease(payload);
            String response = mapper.writeValueAsString(Map.of(
                "success", true,
                "data", Map.of(
                    "status", state,
                    "message", "remote " + state,
                    "checkedAt", clock.instant().toString(),
                    "refreshAfterSeconds", 60,
                    "lease", lease
                )
            ));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        cacheFile = tempDir.resolve("online-status.json");
        service = new LicenseOnlineValidationService(
            true,
            "http://127.0.0.1:" + server.getAddress().getPort()
                + "/api/v1/licenses/check",
            Duration.ofMinutes(1),
            Duration.ofHours(1),
            Duration.ofSeconds(2),
            mapper,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
            verifier,
            cacheFile,
            clock
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void firstObservationIsLockedUntilSignedLeaseArrives() {
        LicenseOnlineDecision pending = service.evaluate(payload());
        assertFalse(pending.usable());
        assertEquals("PENDING", pending.view().state());

        LicenseOnlineStatusView refreshed = service.refreshNow(payload()).join();
        assertEquals("ACTIVE", refreshed.state());
        assertTrue(service.evaluate(payload()).usable());
        assertTrue(Files.isRegularFile(cacheFile));
    }

    @Test
    void remoteRevocationDeniesImmediatelyWithoutOfflineGrace() {
        service.refreshNow(payload()).join();
        remoteState.set("REVOKED");
        sequence.incrementAndGet();
        clock.advance(Duration.ofMinutes(2));
        service.refreshNow(payload()).join();

        LicenseOnlineDecision decision = service.evaluate(payload());
        assertFalse(decision.usable());
        assertEquals(LicenseStatusCode.REVOKED, decision.deniedStatus());
    }

    @Test
    void remoteSuspensionDeniesImmediatelyButUsesDistinctRecoverableStatus() {
        service.refreshNow(payload()).join();
        remoteState.set("SUSPENDED");
        sequence.incrementAndGet();
        clock.advance(Duration.ofMinutes(2));
        service.refreshNow(payload()).join();

        LicenseOnlineDecision decision = service.evaluate(payload());
        assertFalse(decision.usable());
        assertEquals(LicenseStatusCode.SUSPENDED, decision.deniedStatus());
    }

    @Test
    void centerOutageUsesSignedLeaseOnlyUntilItsDeadline() {
        service.refreshNow(payload()).join();
        server.stop(0);
        server = null;
        clock.advance(Duration.ofMinutes(30));
        service.refreshNow(payload()).join();
        assertTrue(service.evaluate(payload()).usable());

        clock.advance(Duration.ofMinutes(31));
        LicenseOnlineDecision decision = service.evaluate(payload());
        assertFalse(decision.usable());
        assertEquals(LicenseStatusCode.ONLINE_CHECK_REQUIRED, decision.deniedStatus());
    }

    @Test
    void rejectsNonceMismatchAndKeepsPreviousTrustedLease() {
        service.refreshNow(payload()).join();
        forcedNonce.set("attacker-replayed-nonce-0000000000");
        remoteState.set("REVOKED");
        sequence.incrementAndGet();
        clock.advance(Duration.ofMinutes(2));
        service.refreshNow(payload()).join();

        LicenseOnlineDecision decision = service.evaluate(payload());
        assertTrue(decision.usable());
        assertTrue(decision.view().message().contains("重放"));
    }

    @Test
    void rejectsLowerSequenceAndKeepsNewerTrustedState() {
        sequence.set(10);
        service.refreshNow(payload()).join();
        sequence.set(9);
        remoteState.set("REVOKED");
        clock.advance(Duration.ofMinutes(2));
        service.refreshNow(payload()).join();

        LicenseOnlineDecision decision = service.evaluate(payload());
        assertTrue(decision.usable());
        assertTrue(decision.view().message().contains("旧版本"));
    }

    @Test
    void editedCacheCannotCreateActiveAuthorization() throws Exception {
        Files.writeString(
            cacheFile,
            "{\"schemaVersion\":2,\"lease\":\"AQL1.fake.signature\"}",
            StandardCharsets.UTF_8
        );
        LicenseOnlineValidationService restarted = new LicenseOnlineValidationService(
            true,
            "http://127.0.0.1:" + server.getAddress().getPort()
                + "/api/v1/licenses/check",
            Duration.ofMinutes(1),
            Duration.ofHours(1),
            Duration.ofSeconds(2),
            mapper,
            HttpClient.newHttpClient(),
            verifier,
            cacheFile,
            clock
        );

        LicenseOnlineDecision decision = restarted.evaluate(payload());
        assertFalse(decision.usable());
        assertEquals("PENDING", decision.view().state());
    }

    @Test
    void cacheContainsOnlySignedLeaseAndNoAuthorityCredentials() throws Exception {
        service.refreshNow(payload()).join();
        String cache = Files.readString(cacheFile, StandardCharsets.UTF_8);
        assertTrue(cache.contains("AQL1."));
        assertFalse(cache.contains("AQF1."));
        assertFalse(cache.toLowerCase().contains("authorization"));
        assertFalse(cache.toLowerCase().contains("private"));
    }

    @Test
    void refusesInsecureOrCredentialBearingAuthorityUrls() {
        for (String endpoint : List.of(
            "http://license.example.com/api/v1/licenses/check",
            "https://user:secret@license.example.com/api/v1/licenses/check",
            "https://license.example.com/api/v1/licenses/check?token=secret",
            "https://license.example.com/wrong-path"
        )) {
            LicenseOnlineValidationService invalid = new LicenseOnlineValidationService(
                true,
                endpoint,
                Duration.ofMinutes(1),
                Duration.ofHours(1),
                Duration.ofSeconds(2),
                mapper,
                HttpClient.newHttpClient(),
                verifier,
                tempDir.resolve(Integer.toHexString(endpoint.hashCode()) + ".json"),
                clock
            );
            LicenseOnlineDecision decision = invalid.evaluate(payload());
            assertFalse(decision.usable(), endpoint);
            assertEquals(LicenseStatusCode.CONFIGURATION_ERROR, decision.deniedStatus());
        }
    }

    private String signLease(OnlineLeasePayload payload) {
        try {
            byte[] payloadBytes = mapper.writeValueAsBytes(payload);
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(payloadBytes);
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return "AQL1." + encoder.encodeToString(payloadBytes) + "."
                + encoder.encodeToString(signature.sign());
        } catch (Exception signingFailure) {
            throw new IllegalStateException("测试租约签名失败", signingFailure);
        }
    }

    private LicensePayload payload() {
        return new LicensePayload(
            1,
            "license-online-test",
            "aquafish-platform",
            "business",
            "Online Test",
            INSTANCE_ID,
            NOW.minusSeconds(60),
            NOW.minusSeconds(60),
            NOW.plus(Duration.ofDays(365)),
            List.of("platform", "forum")
        );
    }

    /** 测试专用可推进时钟，避免使用 Thread.sleep 等不稳定等待。 */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
