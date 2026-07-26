package com.aquafish.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AQF1 授权码解析与 Ed25519 数字签名校验器，对标 Halo 的 X.509 证书信封。
 *
 * <p>两种信封格式并存：</p>
 * <ul>
 *   <li>三段 {@code AQF1.payload.sig}（旧）：直接拿预埋公钥验签，兼容已分发的旧授权。</li>
 *   <li>四段 {@code AQF1.payload.sig.cert}（新，对标 Halo）：先用预埋根证书验证
 *       {@code root→leaf} 信任链，再用叶子证书公钥验签。客户端因此只需信任一张根证书，
 *       无需任何在线接口即可离线验真。</li>
 * </ul>
 *
 * <p>签名覆盖 payload 的原始字节；客户修改版本、有效期、实例 ID、功能项或权益键值都会导致
 * 验签失败。私钥不在 Aquafish 程序中，程序只能验证，不能伪造或延长授权。</p>
 */
@Component
public final class LicenseTokenVerifier {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final String PREFIX = "AQF1";
    private static final String BUNDLED_PUBLIC_KEY = "/aquafish-license-public-key.txt";
    private static final String BUNDLED_ONLINE_PUBLIC_KEY = "/aquafish-license-online-public-key.txt";
    private static final String BUNDLED_CA_ROOT_CERT = "/aquafish-license-ca-root.crt";

    private final ObjectMapper objectMapper;
    private final String expectedProduct;
    private final Duration clockSkew;
    private final Clock clock;
    private final List<PublicKey> publicKeys;
    private final List<X509Certificate> caRoots;
    private final CertificateFactory certificateFactory;
    private final CertPathValidator certPathValidator;
    private final String configurationError;

    @Autowired
    public LicenseTokenVerifier(
        ObjectMapper objectMapper,
        @Value("${aquafish.license.product:aquafish-platform}") String expectedProduct,
        @Value("${aquafish.license.public-key:}") String configuredPublicKey,
        @Value("${aquafish.license.online.public-key:}") String configuredOnlinePublicKey,
        @Value("${aquafish.license.online.previous-public-key:}") String previousOnlinePublicKey,
        @Value("${aquafish.license.ca-root-cert:}") String configuredCaRootCert,
        @Value("${aquafish.license.clock-skew-seconds:300}") long clockSkewSeconds
    ) {
        List<PublicKey> loadedKeys = new ArrayList<>();
        List<X509Certificate> loadedRoots = new ArrayList<>();
        String loadError = null;
        try {
            String keyText = normalize(configuredPublicKey);
            if (keyText.isEmpty()) {
                keyText = readBundledPublicKey();
            }
            loadedKeys.add(parsePublicKey(keyText));
            String onlineKeyText = normalize(configuredOnlinePublicKey);
            if (onlineKeyText.isEmpty()) {
                onlineKeyText = readOptionalBundledPublicKey(BUNDLED_ONLINE_PUBLIC_KEY);
            }
            if (!onlineKeyText.isEmpty()) {
                PublicKey onlineKey = parsePublicKey(onlineKeyText);
                if (loadedKeys.stream().noneMatch(key -> key.equals(onlineKey))) {
                    loadedKeys.add(onlineKey);
                }
            }
            /*
             * AQF1 是可长期离线使用的文件。在线签名键轮换后，旧 AQF1 不能因授权中心切换
             * activeKeyId 而突然失效，所以与 AQL1 一样保留上一把在线公钥的验证窗口。
             * 私钥不随发行包分发；这里只增加公开验证材料。
             */
            String previousText = normalize(previousOnlinePublicKey);
            if (!previousText.isEmpty()) {
                PublicKey previousKey = parsePublicKey(previousText);
                if (loadedKeys.stream().noneMatch(key -> key.equals(previousKey))) {
                    loadedKeys.add(previousKey);
                }
            }

            String rootText = normalize(configuredCaRootCert);
            if (rootText.isEmpty()) {
                rootText = readOptionalBundledResource(BUNDLED_CA_ROOT_CERT);
            }
            if (!rootText.isEmpty()) {
                X509Certificate root = parseCertificatePem(rootText);
                if (loadedRoots.stream().noneMatch(cert -> cert.equals(root))) {
                    loadedRoots.add(root);
                }
            }
        } catch (RuntimeException | IOException error) {
            loadError = "许可证公钥配置无效，请联系 Aquafish 发行方。";
        }
        if (loadError == null && loadedKeys.isEmpty() && loadedRoots.isEmpty()) {
            loadError = "许可证公钥未配置。";
        }
        this.objectMapper = objectMapper;
        this.expectedProduct = normalize(expectedProduct);
        this.clockSkew = Duration.ofSeconds(Math.max(0, clockSkewSeconds));
        this.clock = Clock.systemUTC();
        this.publicKeys = List.copyOf(loadedKeys);
        this.caRoots = List.copyOf(loadedRoots);
        try {
            this.certificateFactory = CertificateFactory.getInstance("X.509", "BC");
            this.certPathValidator = CertPathValidator.getInstance("PKIX", "BC");
        } catch (Exception error) {
            throw new IllegalStateException("无法初始化证书校验器。", error);
        }
        this.configurationError = loadError;
    }

    /** 测试和密钥轮换使用的多公钥构造器（不含 CA 根，仅旧三段格式）。 */
    LicenseTokenVerifier(
        ObjectMapper objectMapper,
        String expectedProduct,
        PublicKey publicKey,
        Duration clockSkew,
        Clock clock
    ) {
        this(
            objectMapper,
            expectedProduct,
            publicKey == null ? List.of() : List.of(publicKey),
            List.of(),
            clockSkew,
            clock
        );
    }

    /** 测试和密钥轮换使用的多公钥构造器（不含 CA 根，仅旧三段格式）。 */
    LicenseTokenVerifier(
        ObjectMapper objectMapper,
        String expectedProduct,
        List<PublicKey> publicKeys,
        Duration clockSkew,
        Clock clock
    ) {
        this(objectMapper, expectedProduct, publicKeys, List.of(), clockSkew, clock);
    }

    /** 测试与证书信封校验使用的构造器：显式传入信任根与旧公钥。 */
    LicenseTokenVerifier(
        ObjectMapper objectMapper,
        String expectedProduct,
        List<PublicKey> publicKeys,
        List<X509Certificate> caRoots,
        Duration clockSkew,
        Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.expectedProduct = normalize(expectedProduct);
        this.publicKeys = publicKeys == null ? List.of() : List.copyOf(publicKeys);
        this.caRoots = caRoots == null ? List.of() : List.copyOf(caRoots);
        this.clockSkew = clockSkew;
        this.clock = clock;
        try {
            this.certificateFactory = CertificateFactory.getInstance("X.509", "BC");
            this.certPathValidator = CertPathValidator.getInstance("PKIX", "BC");
        } catch (Exception error) {
            throw new IllegalStateException("无法初始化证书校验器。", error);
        }
        this.configurationError = (this.publicKeys.isEmpty() && this.caRoots.isEmpty())
            ? "许可证公钥未配置。"
            : null;
    }

    /**
     * 对授权码执行结构、签名、产品、实例和有效期的完整校验。
     */
    public LicenseVerification verify(String licenseCode, String currentInstanceId) {
        if (configurationError != null) {
            return failed(LicenseStatusCode.CONFIGURATION_ERROR, configurationError);
        }

        String safeCode = normalize(licenseCode);
        if (safeCode.isEmpty() || safeCode.length() > LicenseFileStore.MAX_LICENSE_CODE_LENGTH) {
            return failed(LicenseStatusCode.INVALID, "授权码为空或超过允许大小。");
        }

        try {
            String[] parts = safeCode.split("\\.", -1);
            if (parts.length == 4 && PREFIX.equals(parts[0])) {
                return verifyEnvelope(parts, currentInstanceId);
            }
            if (parts.length == 3 && PREFIX.equals(parts[0])) {
                return verifyLegacy(parts, currentInstanceId);
            }
            return failed(LicenseStatusCode.INVALID, "授权码格式不正确。");
        } catch (IllegalArgumentException | IOException error) {
            return failed(LicenseStatusCode.INVALID, "授权码无法解析，请确认复制完整。");
        } catch (Exception error) {
            return failed(LicenseStatusCode.INVALID, "授权码校验失败。");
        }
    }

    /** 旧三段格式：直接以预埋公钥验签。 */
    private LicenseVerification verifyLegacy(String[] parts, String currentInstanceId) throws Exception {
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
        if (!verifySignature(payloadBytes, signatureBytes)) {
            return failed(LicenseStatusCode.INVALID, "授权码签名无效或内容已被修改。");
        }
        return validatePayload(payloadBytes, currentInstanceId);
    }

    /**
     * 新四段信封（对标 Halo）：先验证 root→leaf 信任链，再用叶子证书公钥验签。
     */
    private LicenseVerification verifyEnvelope(String[] parts, String currentInstanceId) throws Exception {
        if (caRoots.isEmpty()) {
            return failed(LicenseStatusCode.CONFIGURATION_ERROR, "未配置证书信任根，无法校验授权信封。");
        }
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
        byte[] leafDer = Base64.getUrlDecoder().decode(parts[3]);

        X509Certificate leaf = (X509Certificate) certificateFactory.generateCertificate(
            new java.io.ByteArrayInputStream(leafDer)
        );
        if (leaf.getBasicConstraints() != -1) {
            return failed(LicenseStatusCode.INVALID, "授权信封中的证书不是终端实体证书。");
        }
        if (!verifyCertificateChain(leaf)) {
            return failed(LicenseStatusCode.INVALID, "授权信封证书链校验失败（根证书不受信任或已过期）。");
        }
        if (!verifySignatureWithLeaf(leaf, payloadBytes, signatureBytes)) {
            return failed(LicenseStatusCode.INVALID, "授权码签名无效或内容已被修改。");
        }
        return validatePayload(payloadBytes, currentInstanceId);
    }

    private LicenseVerification validatePayload(byte[] payloadBytes, String currentInstanceId) throws Exception {
        LicensePayload payload = objectMapper.readValue(payloadBytes, LicensePayload.class);
        LicenseVerification structureFailure = validateStructure(payload);
        if (structureFailure != null) {
            return structureFailure;
        }
        if (!expectedProduct.equals(normalize(payload.product()))) {
            return new LicenseVerification(
                LicenseStatusCode.PRODUCT_MISMATCH,
                payload,
                "该授权码不属于 Aquafish 系统平台。"
            );
        }
        if (!normalize(currentInstanceId).equals(normalize(payload.instanceId()))) {
            return new LicenseVerification(
                LicenseStatusCode.INSTANCE_MISMATCH,
                payload,
                "授权码绑定的设备码与当前实例不一致。"
            );
        }

        Instant now = clock.instant();
        if (payload.notBefore() != null && now.plus(clockSkew).isBefore(payload.notBefore())) {
            return new LicenseVerification(
                LicenseStatusCode.NOT_YET_VALID,
                payload,
                "授权码尚未到生效时间。"
            );
        }
        if (payload.expiresAt() != null && now.minus(clockSkew).isAfter(payload.expiresAt())) {
            return new LicenseVerification(
                LicenseStatusCode.EXPIRED,
                payload,
                "授权码已经过期，请续期或更换授权码。"
            );
        }

        return new LicenseVerification(
            LicenseStatusCode.VALID,
            payload,
            "授权有效，可以使用 Aquafish 系统平台。"
        );
    }

    private LicenseVerification validateStructure(LicensePayload payload) {
        if (payload == null
            || payload.schemaVersion() != 1
            || normalize(payload.licenseId()).isEmpty()
            || normalize(payload.product()).isEmpty()
            || normalize(payload.edition()).isEmpty()
            || normalize(payload.instanceId()).isEmpty()
            || payload.issuedAt() == null) {
            return failed(LicenseStatusCode.INVALID, "授权码缺少必需字段或版本不受支持。");
        }
        if (payload.expiresAt() != null && payload.expiresAt().isBefore(payload.issuedAt())) {
            return failed(LicenseStatusCode.INVALID, "授权码有效期字段不合法。");
        }
        return null;
    }

    /** 旧格式：逐个尝试预埋公钥验签。 */
    private boolean verifySignature(byte[] payload, byte[] signatureBytes) throws Exception {
        for (PublicKey publicKey : publicKeys) {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(payload);
            if (verifier.verify(signatureBytes)) {
                return true;
            }
        }
        return false;
    }

    /** 新信封：用叶子证书中的 Ed25519 公钥验签。 */
    private boolean verifySignatureWithLeaf(X509Certificate leaf, byte[] payload, byte[] signatureBytes) {
        try {
            PublicKey leafPublicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(leaf.getPublicKey().getEncoded()));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(leafPublicKey);
            verifier.update(payload);
            return verifier.verify(signatureBytes);
        } catch (Exception error) {
            return false;
        }
    }

    /** 用预埋根证书验证 root→leaf 信任链（PKIX，关闭吊销检查）。 */
    private boolean verifyCertificateChain(X509Certificate leaf) {
        try {
            CertPath path = certificateFactory.generateCertPath(List.of(leaf));
            Set<TrustAnchor> anchors = new HashSet<>();
            for (X509Certificate root : caRoots) {
                anchors.add(new TrustAnchor(root, null));
            }
            PKIXParameters parameters = new PKIXParameters(anchors);
            parameters.setRevocationEnabled(false);
            certPathValidator.validate(path, parameters);
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private String readBundledPublicKey() throws IOException {
        try (InputStream input = LicenseTokenVerifier.class.getResourceAsStream(BUNDLED_PUBLIC_KEY)) {
            if (input == null) {
                throw new IOException("Bundled license public key not found");
            }
            return new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        }
    }

    /** 在线专用公钥是可选的；未配置时仍兼容原离线根公钥授权。 */
    private String readOptionalBundledPublicKey(String resource) throws IOException {
        return readOptionalBundledResource(resource);
    }

    private String readOptionalBundledResource(String resource) throws IOException {
        try (InputStream input = LicenseTokenVerifier.class.getResourceAsStream(resource)) {
            return input == null ? "" : new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        }
    }

    static PublicKey parsePublicKey(String pemOrBase64) {
        try {
            String base64 = normalize(pemOrBase64)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
            byte[] encoded = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid Ed25519 public key", error);
        }
    }

    static X509Certificate parseCertificatePem(String pem) {
        try {
            String base64 = normalize(pem)
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(base64);
            CertificateFactory factory = CertificateFactory.getInstance("X.509", "BC");
            return (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(der));
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid X.509 certificate", error);
        }
    }

    private LicenseVerification failed(LicenseStatusCode status, String message) {
        return new LicenseVerification(status, null, message);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
