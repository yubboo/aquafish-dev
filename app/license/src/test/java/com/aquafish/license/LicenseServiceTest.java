package com.aquafish.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
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
import org.junit.jupiter.api.io.TempDir;

/**
 * 覆盖“先验签、后保存、再用于全局放行”的完整激活用例。
 */
class LicenseServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private KeyPair keyPair;
    private LicenseFileStore store;
    private LicenseService service;
    private String instanceId;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        LicenseInstanceIdentityService identity = new LicenseInstanceIdentityService(
            tempDir.resolve("instance.id")
        );
        instanceId = identity.instanceId();
        store = new LicenseFileStore(tempDir.resolve("licenses/platform.license"));
        service = new LicenseService(
            identity,
            store,
            new LicenseTokenVerifier(
                objectMapper,
                "aquafish-platform",
                keyPair.getPublic(),
                Duration.ZERO,
                Clock.fixed(NOW, ZoneOffset.UTC)
            ),
            true
        );
    }

    @Test
    void activatesVerifiedLicenseAndMakesPlatformUsable() throws Exception {
        LicenseStatusView activated = service.activate(sign(instanceId));

        assertTrue(activated.valid());
        assertTrue(activated.usable());
        assertEquals(LicenseStatusCode.VALID, service.status().status());
        assertTrue(store.read().isPresent());
    }

    @Test
    void invalidInstanceNeverOverwritesStoredLicense() throws Exception {
        LicenseActivationException error = assertThrows(
            LicenseActivationException.class,
            () -> service.activate(sign("another-instance"))
        );

        assertEquals("LICENSE_INSTANCE_MISMATCH", error.code());
        assertFalse(store.read().isPresent());
    }

    @Test
    void officialThemeRequiresBothThemeModuleAndExactAssetEntitlement() throws Exception {
        LicenseStatusView fullyLicensed = service.activate(signPayload(new LicensePayload(
            1,
            "license-theme-double-auth",
            "aquafish-platform",
            "professional",
            "Aquafish Theme Customer",
            instanceId,
            NOW.minusSeconds(60),
            NOW.minusSeconds(60),
            NOW.plus(Duration.ofDays(365)),
            List.of("platform", "theme"),
            List.of(new LicenseEntitlement("theme", "official-default"))
        )));

        assertTrue(service.isAssetUsable(
            fullyLicensed, LicenseFeature.THEME, "theme", "official-default"
        ));
        assertFalse(service.isAssetUsable(
            fullyLicensed, LicenseFeature.THEME, "theme", "official-premium"
        ));

        LicenseStatusView missingThemeModule = service.activate(signPayload(new LicensePayload(
            1,
            "license-theme-asset-only",
            "aquafish-platform",
            "professional",
            "Aquafish Theme Customer",
            instanceId,
            NOW.minusSeconds(60),
            NOW.minusSeconds(60),
            NOW.plus(Duration.ofDays(365)),
            List.of("platform"),
            List.of(new LicenseEntitlement("theme", "official-default"))
        )));
        assertFalse(service.isAssetUsable(
            missingThemeModule, LicenseFeature.THEME, "theme", "official-default"
        ));
    }

    private String sign(String boundInstanceId) throws Exception {
        return signPayload(new LicensePayload(
            1,
            "license-service-test",
            "aquafish-platform",
            "professional",
            "Aquafish Test",
            boundInstanceId,
            NOW.minusSeconds(60),
            NOW.minusSeconds(60),
            NOW.plus(Duration.ofDays(365)),
            List.of("cms", "forum", "market")
        ));
    }

    /** 使用测试密钥签名任意载荷，便于覆盖模块与具体商品双授权组合。 */
    private String signPayload(LicensePayload payload) throws Exception {
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
