package com.aquafish.core.admin.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AdminLoginRequestTest {

    @Test
    void normalizesAndValidatesCredentials() {
        AdminLoginRequest request = new AdminLoginRequest("  admin  ", "12345678", true);

        assertEquals("admin", request.normalized().username());
        assertNull(request.normalized().validateMessage());
    }

    @Test
    void rejectsMissingCredentials() {
        assertEquals(
            "请输入用户名或邮箱。",
            new AdminLoginRequest("", "12345678", false).validateMessage()
        );
    }
}
