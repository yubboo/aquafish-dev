package com.aquafish.user.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 用户自主注册请求的纯单元测试。
 */
class MemberRegistrationRequestTest {

    @Test
    void shouldNormalizeIdentityWithoutChangingPassword() {
        MemberRegistrationRequest request = new MemberRegistrationRequest(
            "  测试_user  ",
            "  MEMBER@Example.COM ",
            "  测试用户  ",
            " password ",
            " password ",
            true
        ).normalized();

        assertEquals("测试_user", request.username());
        assertEquals("member@example.com", request.email());
        assertEquals("测试用户", request.displayName());
        assertEquals(" password ", request.password());
        assertNull(request.validateMessage());
    }

    @Test
    void shouldRejectInvalidRegistrationInput() {
        MemberRegistrationRequest request = new MemberRegistrationRequest(
            "x y",
            "invalid",
            "",
            "short",
            "different",
            false
        ).normalized();

        assertEquals(
            "用户名必须为 1 至 64 位中文、字母、数字、下划线或短横线。",
            request.validateMessage()
        );
    }
}
