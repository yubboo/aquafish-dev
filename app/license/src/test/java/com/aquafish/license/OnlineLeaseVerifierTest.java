package com.aquafish.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 验证在线公钥轮换与信任域隔离。
 *
 * <p>发行版在轮换窗口内同时内置 current/previous 公钥，因此旧客户租约不会因换钥
 * 立即失效；未知 keyId、内容篡改和错误 nonce 必须被拒绝。</p>
 */
class OnlineLeaseVerifierTest {

    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");
    private static final String LICENSE_ID = "license-rotation-test";
    private static final String INSTANCE_ID = "instance-rotation-test";
    private static final String NONCE = "rotation-request-nonce-00000001";

    private ObjectMapper mapper;
    private KeyPair currentKey;
    private KeyPair previousKey;
    private OnlineLeaseVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        currentKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        previousKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        verifier = new OnlineLeaseVerifier(
            mapper,
            Map.of(
                "online-current", currentKey.getPublic(),
                "online-previous", previousKey.getPublic()
            ),
            Duration.ofMinutes(5),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void acceptsCurrentAndPreviousKeyDuringRotationWindow() {
        assertTrue(verify(sign("online-current", currentKey.getPrivate())).valid());
        assertTrue(verify(sign("online-previous", previousKey.getPrivate())).valid());
    }

    @Test
    void rejectsUnknownKeyIdEvenWhenSignatureUsesKnownPrivateKey() {
        OnlineLeaseVerification result = verify(sign("online-unknown", currentKey.getPrivate()));
        assertFalse(result.valid());
        assertTrue(result.message().contains("签名无效"));
    }

    @Test
    void rejectsModifiedPayloadAndWrongRequestNonce() {
        String valid = sign("online-current", currentKey.getPrivate());
        String[] parts = valid.split("\\.");
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        String modifiedJson = new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8)
            .replace("\"ACTIVE\"", "\"REVOKED\"");
        String modified = "AQL1."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(
                modifiedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
            + "." + parts[2];
        assertFalse(verify(modified).valid());

        OnlineLeaseVerification wrongNonce = verifier.verify(
            valid,
            LICENSE_ID,
            INSTANCE_ID,
            "different-request-nonce-0000000"
        );
        assertFalse(wrongNonce.valid());
        assertEquals("在线授权响应与本次请求不匹配，已拒绝可能的重放响应。", wrongNonce.message());
    }

    private OnlineLeaseVerification verify(String token) {
        return verifier.verify(token, LICENSE_ID, INSTANCE_ID, NONCE);
    }

    private String sign(String keyId, PrivateKey privateKey) {
        try {
            OnlineLeasePayload payload = new OnlineLeasePayload(
                1,
                "ONLINE_STATUS",
                keyId,
                LICENSE_ID,
                INSTANCE_ID,
                "ACTIVE",
                "rotation test",
                NOW,
                NOW.plus(Duration.ofDays(30)),
                7,
                NONCE,
                3600
            );
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
}
