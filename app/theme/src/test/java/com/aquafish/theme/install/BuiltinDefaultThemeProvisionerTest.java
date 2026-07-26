package com.aquafish.theme.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BuiltinDefaultThemeProvisionerTest {

    @Test
    void shouldCompareSemanticThemeVersions() {
        assertTrue(BuiltinDefaultThemeProvisioner.compareVersions("0.2.0", "0.4.0") < 0);
        assertTrue(BuiltinDefaultThemeProvisioner.compareVersions("1.10.0", "1.9.9") > 0);
        assertEquals(0, BuiltinDefaultThemeProvisioner.compareVersions("0.4", "0.4.0"));
    }
}
