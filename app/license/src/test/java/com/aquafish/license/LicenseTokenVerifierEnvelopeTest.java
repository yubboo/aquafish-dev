package com.aquafish.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 对标 Halo 的 X.509 信封校验回归：
 * 新四段格式用预埋根证书验证 root→leaf 链并验签；旧三段格式仍按预埋公钥验签。
 */
class LicenseTokenVerifierEnvelopeTest {

    private ObjectMapper mapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Test
    void fourSegmentEnvelopeVerifiesAgainstRootChain() throws Exception {
        KeyPair ca = TestCertificateAuthority.ed25519();
        X509Certificate root = TestCertificateAuthority.root(ca, "Aquafish License CA", Duration.ofDays(3650));
        KeyPair signer = TestCertificateAuthority.ed25519();
        X509Certificate leaf = TestCertificateAuthority.leaf(
            ca, root, signer.getPublic(), "aquafish-signing", Duration.ofDays(1825)
        );

        LicensePayload payload = new LicensePayload(
            1, "AQF-x", "aquafish-platform", "professional", "鱼", "inst-1",
            Instant.parse("2026-07-20T00:00:00Z"), Instant.parse("2026-07-20T00:00:00Z"), null,
            List.of("reports"), List.of(), Map.of("include-all-apps", "true", "lxware.cn/username", "yubb")
        );
        byte[] payloadBytes = mapper().writeValueAsBytes(payload);

        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(signer.getPrivate());
        sig.update(payloadBytes);
        byte[] signature = sig.sign();

        String token = "AQF1."
            + base64Url(payloadBytes) + "."
            + base64Url(signature) + "."
            + base64Url(leaf.getEncoded());

        LicenseTokenVerifier verifier = new LicenseTokenVerifier(
            mapper(), "aquafish-platform", List.of(), List.of(root), Duration.ofMinutes(5), Clock.systemUTC()
        );
        LicenseVerification result = verifier.verify(token, "inst-1");

        assertTrue(result.valid(), "四段信封应校验通过");
        assertEquals(LicenseStatusCode.VALID, result.status());
        assertEquals("true", result.payload().annotations().get("include-all-apps"));
        assertEquals("yubb", result.payload().annotations().get("lxware.cn/username"));
    }

    @Test
    void fourSegmentEnvelopeWithTamperedPayloadFails() throws Exception {
        KeyPair ca = TestCertificateAuthority.ed25519();
        X509Certificate root = TestCertificateAuthority.root(ca, "Aquafish License CA", Duration.ofDays(3650));
        KeyPair signer = TestCertificateAuthority.ed25519();
        X509Certificate leaf = TestCertificateAuthority.leaf(
            ca, root, signer.getPublic(), "aquafish-signing", Duration.ofDays(1825)
        );

        LicensePayload payload = new LicensePayload(
            1, "AQF-x", "aquafish-platform", "professional", "鱼", "inst-1",
            Instant.parse("2026-07-20T00:00:00Z"), Instant.parse("2026-07-20T00:00:00Z"), null,
            List.of("reports"), List.of(), Map.of()
        );
        byte[] payloadBytes = mapper().writeValueAsBytes(payload);
        byte[] tampered = payloadBytes.clone();
        tampered[tampered.length - 1] ^= 0xFF;

        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(signer.getPrivate());
        sig.update(payloadBytes);
        byte[] signature = sig.sign();

        String token = "AQF1."
            + base64Url(payloadBytes) + "."
            + base64Url(signature) + "."
            + base64Url(leaf.getEncoded());

        LicenseTokenVerifier verifier = new LicenseTokenVerifier(
            mapper(), "aquafish-platform", List.of(), List.of(root), Duration.ofMinutes(5), Clock.systemUTC()
        );
        // 用篡改后的 payload 字节构造 token 文本（签名仍是原 payload 的）
        String badToken = "AQF1." + base64Url(tampered) + "." + base64Url(signature) + "." + base64Url(leaf.getEncoded());
        LicenseVerification result = verifier.verify(badToken, "inst-1");
        assertFalse(result.valid(), "篡改 payload 不应通过验签");
    }

    @Test
    void legacyThreeSegmentStillVerifiesWithPublicKey() throws Exception {
        KeyPair signer = TestCertificateAuthority.ed25519();
        LicensePayload payload = new LicensePayload(
            1, "AQF-x", "aquafish-platform", "professional", "鱼", "inst-1",
            Instant.parse("2026-07-20T00:00:00Z"), Instant.parse("2026-07-20T00:00:00Z"), null,
            List.of("reports"), List.of(), Map.of()
        );
        byte[] payloadBytes = mapper().writeValueAsBytes(payload);
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(signer.getPrivate());
        sig.update(payloadBytes);
        byte[] signature = sig.sign();

        String token = "AQF1." + base64Url(payloadBytes) + "." + base64Url(signature);

        LicenseTokenVerifier verifier = new LicenseTokenVerifier(
            mapper(), "aquafish-platform", List.of(signer.getPublic()), List.of(), Duration.ofMinutes(5), Clock.systemUTC()
        );
        LicenseVerification result = verifier.verify(token, "inst-1");
        assertTrue(result.valid(), "旧三段格式应仍按预埋公钥验签通过");
        assertEquals(LicenseStatusCode.VALID, result.status());
    }
}
