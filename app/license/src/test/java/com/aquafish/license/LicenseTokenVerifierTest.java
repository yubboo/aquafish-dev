package com.aquafish.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 验证 AQF1 授权码与 Node 授权端采用相同的 Ed25519 签名规则。
 */
class LicenseTokenVerifierTest {

    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

    private ObjectMapper objectMapper;
    private KeyPair keyPair;
    private LicenseTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        verifier = new LicenseTokenVerifier(
            objectMapper,
            "aquafish-platform",
            keyPair.getPublic(),
            Duration.ofMinutes(5),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void acceptsValidLicenseForCurrentInstance() throws Exception {
        LicenseVerification result = verifier.verify(
            sign(payload("instance-a", NOW.plus(Duration.ofDays(30)))),
            "instance-a"
        );

        assertEquals(LicenseStatusCode.VALID, result.status());
        assertTrue(result.valid());
        assertEquals("professional", result.payload().edition());
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        String code = sign(payload("instance-a", NOW.plus(Duration.ofDays(30))));
        String tampered = code.replace("AQF1.", "AQF1.eA");

        assertEquals(
            LicenseStatusCode.INVALID,
            verifier.verify(tampered, "instance-a").status()
        );
    }

    @Test
    void rejectsLicenseBoundToAnotherInstance() throws Exception {
        LicenseVerification result = verifier.verify(
            sign(payload("instance-a", NOW.plus(Duration.ofDays(30)))),
            "instance-b"
        );

        assertEquals(LicenseStatusCode.INSTANCE_MISMATCH, result.status());
    }

    @Test
    void rejectsExpiredLicense() throws Exception {
        LicenseVerification result = verifier.verify(
            sign(payload("instance-a", NOW.minus(Duration.ofDays(1)))),
            "instance-a"
        );

        assertEquals(LicenseStatusCode.EXPIRED, result.status());
    }

    @Test
    void acceptsDedicatedOnlineSigningKeyDuringKeySeparation() throws Exception {
        KeyPair onlineKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        verifier = new LicenseTokenVerifier(
            objectMapper,
            "aquafish-platform",
            List.of(keyPair.getPublic(), onlineKeyPair.getPublic()),
            Duration.ofMinutes(5),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        LicenseVerification result = verifier.verify(
            sign(payload("instance-online", NOW.plus(Duration.ofDays(30))), onlineKeyPair),
            "instance-online"
        );

        assertEquals(LicenseStatusCode.VALID, result.status());
        assertTrue(result.valid());
    }

    private LicensePayload payload(String instanceId, Instant expiresAt) {
        Instant issuedAt = expiresAt.isBefore(NOW)
            ? expiresAt.minus(Duration.ofDays(30))
            : NOW.minus(Duration.ofMinutes(1));
        return new LicensePayload(
            1,
            "license-001",
            "aquafish-platform",
            "professional",
            "Aquafish Test",
            instanceId,
            issuedAt,
            issuedAt,
            expiresAt,
            List.of("cms", "forum")
        );
    }

    private String sign(LicensePayload payload) throws Exception {
        return sign(payload, keyPair);
    }

    private String sign(LicensePayload payload, KeyPair signingKeyPair) throws Exception {
        byte[] payloadBytes = objectMapper.writeValueAsBytes(payload);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(signingKeyPair.getPrivate());
        signer.update(payloadBytes);
        byte[] signature = signer.sign();

        return "AQF1."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes)
            + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }
}
