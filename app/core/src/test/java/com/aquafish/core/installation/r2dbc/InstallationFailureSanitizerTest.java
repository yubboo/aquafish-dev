package com.aquafish.core.installation.r2dbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 安装失败信息安全清洗测试。
 */
class InstallationFailureSanitizerTest {

    @Test
    void shouldNormalizeAndLimitErrorCode() {
        String code =
            InstallationFailureSanitizer.sanitizeCode(
                " migration failed / " + "x".repeat(150)
            );

        assertTrue(
            code.length()
                <= InstallationFailureSanitizer.MAX_CODE_LENGTH
        );
        assertFalse(code.contains(" "));
        assertFalse(code.contains("/"));
    }

    @Test
    void shouldRedactCredentialsAndSecrets() {
        String message =
            InstallationFailureSanitizer.sanitizeMessage(
                "jdbc:mysql://root:secret@localhost/aquafish "
                    + "password=123456 token=abcdef"
            );

        assertFalse(message.contains("secret"));
        assertFalse(message.contains("123456"));
        assertFalse(message.contains("abcdef"));
        assertTrue(message.contains("[REDACTED]"));
    }

    @Test
    void shouldProvideSafeDefaults() {
        assertEquals(
            "INSTALLATION_FAILED",
            InstallationFailureSanitizer.sanitizeCode(null)
        );

        assertEquals(
            "安装失败，未提供安全错误摘要。",
            InstallationFailureSanitizer.sanitizeMessage(" ")
        );
    }
}
