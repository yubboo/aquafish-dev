package com.aquafish.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 覆盖授权功能代码兼容规则与 API 路径边界，防止后续新增接口绕过模块授权。 */
class LicenseFeatureTest {

    @Test
    void cmsActsAsCompatibilityBundleForContentAndThemeOnly() {
        List<String> features = List.of("platform", "CMS");

        assertTrue(LicenseFeature.CONTENT.grantedBy(features));
        assertTrue(LicenseFeature.THEME.grantedBy(features));
        assertFalse(LicenseFeature.PLUGIN.grantedBy(features));
    }

    @Test
    void matchesOnlyCompleteApiPathSegments() {
        assertEquals(
            LicenseFeature.AI,
            LicenseFeature.requiredForApiPath("/api/admin/ai/providers").orElseThrow()
        );
        assertTrue(LicenseFeature.requiredForApiPath("/api/admin/aired").isEmpty());
    }

    @Test
    void protectsUpdateEndpointBeforeGenericLicenseBootstrapRules() {
        assertEquals(
            LicenseFeature.UPDATES,
            LicenseFeature.requiredForApiPath("/api/admin/license/updates/check").orElseThrow()
        );
    }
}
