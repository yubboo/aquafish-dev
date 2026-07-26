package com.aquafish.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AQL1 在线状态租约的独立 Ed25519 验签边界。
 *
 * <p>本类只加载在线授权公钥，不加载离线根公钥。即使将来两套密钥分别轮换，在线状态
 * 也不能被误当成永久 AQF1 授权。keyId 允许发行版同时信任当前和上一把在线公钥，
 * 从而在不中断客户授权的情况下完成密钥轮换。</p>
 */
@Component
public final class OnlineLeaseVerifier {

    private static final String PREFIX = "AQL1";
    private static final String TYPE = "ONLINE_STATUS";
    private static final String BUNDLED_PUBLIC_KEY = "/aquafish-license-online-public-key.txt";
    private static final int MAX_TOKEN_LENGTH = 64 * 1024;
    private static final Duration MAX_LEASE_DURATION = Duration.ofDays(366);
    private static final Set<String> KNOWN_STATES = Set.of(
        "ACTIVE", "SUSPENDED", "REVOKED", "UNBOUND", "UNKNOWN",
        "INSTANCE_MISMATCH", "NOT_YET_VALID", "EXPIRED"
    );

    private final ObjectMapper objectMapper;
    private final Map<String, PublicKey> publicKeys;
    private final Duration clockSkew;
    private final Clock clock;
    private final String configurationError;

    @Autowired
    public OnlineLeaseVerifier(
        ObjectMapper objectMapper,
        @Value("${aquafish.license.online.key-id:online-2026-01}") String currentKeyId,
        @Value("${aquafish.license.online.public-key:}") String configuredCurrentPublicKey,
        @Value("${aquafish.license.online.previous-key-id:}") String previousKeyId,
        @Value("${aquafish.license.online.previous-public-key:}") String previousPublicKey,
        @Value("${aquafish.license.clock-skew-seconds:300}") long clockSkewSeconds
    ) {
        this.objectMapper = objectMapper;
        this.clockSkew = Duration.ofSeconds(Math.max(0, clockSkewSeconds));
        this.clock = Clock.systemUTC();

        Map<String, PublicKey> loaded = new LinkedHashMap<>();
        String error = null;
        try {
            String currentId = normalize(currentKeyId);
            if (currentId.isEmpty()) {
                throw new IllegalArgumentException("Missing current online key id");
            }
            String currentText = normalize(configuredCurrentPublicKey);
            if (currentText.isEmpty()) currentText = readBundledPublicKey();
            loaded.put(currentId, LicenseTokenVerifier.parsePublicKey(currentText));

            String oldId = normalize(previousKeyId);
            String oldText = normalize(previousPublicKey);
            if (!oldId.isEmpty() || !oldText.isEmpty()) {
                if (oldId.isEmpty() || oldText.isEmpty() || oldId.equals(currentId)) {
                    throw new IllegalArgumentException("Invalid previous online key pair");
                }
                loaded.put(oldId, LicenseTokenVerifier.parsePublicKey(oldText));
            }
        } catch (RuntimeException | IOException invalidKey) {
            error = "在线授权公钥配置无效，请联系 Aquafish 发行方。";
            loaded.clear();
        }
        this.publicKeys = Map.copyOf(loaded);
        this.configurationError = error;
    }

    /** 测试专用构造器，用临时公钥和可推进时钟验证篡改、重放、过期与轮换。 */
    OnlineLeaseVerifier(
        ObjectMapper objectMapper,
        Map<String, PublicKey> publicKeys,
        Duration clockSkew,
        Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.publicKeys = publicKeys == null ? Map.of() : Map.copyOf(publicKeys);
        this.clockSkew = clockSkew;
        this.clock = clock;
        this.configurationError = this.publicKeys.isEmpty() ? "在线授权公钥未配置。" : null;
    }

    /**
     * 验证签名、业务结构、授权/设备归属和可选的本次请求 nonce。
     *
     * <p>缓存加载时 expectedNonce 传 null，因为签名本身已经保护缓存内容；HTTP 响应
     * 必须传本次随机 nonce，防止代理、恶意网络或旧响应缓存重放另一轮结果。</p>
     */
    OnlineLeaseVerification verify(
        String token,
        String expectedLicenseId,
        String expectedInstanceId,
        String expectedNonce
    ) {
        if (configurationError != null) return failed(configurationError);
        String safeToken = normalize(token);
        if (safeToken.isEmpty() || safeToken.length() > MAX_TOKEN_LENGTH) {
            return failed("在线授权租约为空或超过允许大小。");
        }
        try {
            String[] parts = safeToken.split("\\.", -1);
            if (parts.length != 3 || !PREFIX.equals(parts[0])) {
                return failed("在线授权租约格式不正确。");
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);

            /*
             * keyId 在验签前只是“不可信选择器”，不能参与业务判断。选出候选公钥并成功
             * 验签以后，整个 JSON（包括 keyId 自身）才成为可信数据。
             */
            OnlineLeasePayload payload = objectMapper.readValue(payloadBytes, OnlineLeasePayload.class);
            PublicKey publicKey = payload == null ? null : publicKeys.get(normalize(payload.keyId()));
            if (publicKey == null || !verifySignature(publicKey, payloadBytes, signatureBytes)) {
                return failed("在线授权租约签名无效或内容已被修改。");
            }
            String structureError = validateStructure(
                payload, expectedLicenseId, expectedInstanceId, expectedNonce
            );
            return structureError == null
                ? new OnlineLeaseVerification(true, payload, "在线授权租约验签通过。")
                : failed(structureError);
        } catch (Exception invalidToken) {
            return failed("在线授权租约无法解析。");
        }
    }

    private String validateStructure(
        OnlineLeasePayload payload,
        String expectedLicenseId,
        String expectedInstanceId,
        String expectedNonce
    ) {
        if (payload.schemaVersion() != 1
            || !TYPE.equals(payload.type())
            || normalize(payload.licenseId()).isEmpty()
            || normalize(payload.instanceId()).isEmpty()
            || !KNOWN_STATES.contains(payload.status())
            || payload.issuedAt() == null
            || payload.validUntil() == null
            || payload.sequence() < 0
            || payload.refreshAfterSeconds() < 60
            || payload.refreshAfterSeconds() > 86_400) {
            return "在线授权租约缺少必需字段或版本不受支持。";
        }
        if (!normalize(expectedLicenseId).equals(normalize(payload.licenseId()))
            || !normalize(expectedInstanceId).equals(normalize(payload.instanceId()))) {
            return "在线授权租约不属于当前授权或设备。";
        }
        if (expectedNonce != null && !normalize(expectedNonce).equals(normalize(payload.nonce()))) {
            return "在线授权响应与本次请求不匹配，已拒绝可能的重放响应。";
        }
        if (!payload.validUntil().isAfter(payload.issuedAt())
            || Duration.between(payload.issuedAt(), payload.validUntil())
                .compareTo(MAX_LEASE_DURATION) > 0) {
            return "在线授权租约有效期字段不合法。";
        }
        Instant now = clock.instant();
        if (now.plus(clockSkew).isBefore(payload.issuedAt())) {
            return "在线授权租约签发时间晚于当前系统时间。";
        }
        return null;
    }

    private boolean verifySignature(PublicKey publicKey, byte[] payload, byte[] signatureBytes)
        throws Exception {
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(payload);
        return verifier.verify(signatureBytes);
    }

    private String readBundledPublicKey() throws IOException {
        try (InputStream input = OnlineLeaseVerifier.class.getResourceAsStream(BUNDLED_PUBLIC_KEY)) {
            if (input == null) throw new IOException("Bundled online public key not found");
            return new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        }
    }

    private OnlineLeaseVerification failed(String message) {
        return new OnlineLeaseVerification(false, null, message);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
