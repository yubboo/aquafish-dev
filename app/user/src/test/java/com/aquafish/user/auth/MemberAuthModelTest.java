package com.aquafish.user.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 前台会员认证模型和令牌摘要纯单元测试。
 */
class MemberAuthModelTest {

    @Test
    void shouldNormalizeLoginWithoutChangingPassword() {
        MemberLoginRequest request = new MemberLoginRequest(
            "  member@example.com  ",
            " 密码两端空格必须保留 ",
            true
        ).normalized();

        assertEquals("member@example.com", request.loginName());
        assertEquals(" 密码两端空格必须保留 ", request.password());
    }

    @Test
    void shouldCopyPermissionSnapshot() {
        Set<String> permissions = new HashSet<>();
        permissions.add("forum.thread.create");
        MemberAuthUser user = new MemberAuthUser(
            9L,
            9L,
            "AQUA_9",
            "member",
            "会员",
            "",
            1L,
            "member",
            Set.of("member"),
            permissions,
            false
        );
        permissions.clear();

        assertEquals(Set.of("forum.thread.create"), user.permissions());
        assertEquals(Set.of("member"), user.roles());
    }

    @Test
    void tokenHashShouldBeStableAndNeverEqualRawToken() {
        String rawToken = "raw-member-session-token";

        assertEquals(
            MemberAuthService.tokenHash(rawToken),
            MemberAuthService.tokenHash(rawToken)
        );
        org.junit.jupiter.api.Assertions.assertNotEquals(
            rawToken,
            MemberAuthService.tokenHash(rawToken)
        );
        assertEquals(64, MemberAuthService.tokenHash(rawToken).length());
    }

    @Test
    void shouldRejectInvalidMemberIdentity() {
        assertThrows(IllegalStateException.class, () -> new MemberAuthUser(
            0L,
            0L,
            "",
            "",
            "",
            "",
            null,
            "",
            Set.of(),
            Set.of(),
            false
        ));
    }

    @Test
    void shouldExposeAdminAccessOnlyForServerRoles() {
        MemberAuthUser administrator = new MemberAuthUser(
            9L,
            9L,
            "AQUA_9",
            "administrator",
            "管理员",
            "",
            1L,
            "member",
            Set.of("admin"),
            Set.of(),
            false
        );
        MemberAuthUser member = new MemberAuthUser(
            10L,
            10L,
            "AQUA_10",
            "member",
            "会员",
            "",
            1L,
            "member",
            Set.of("member"),
            Set.of("admin"),
            false
        );

        org.junit.jupiter.api.Assertions.assertTrue(administrator.hasAdminAccess());
        org.junit.jupiter.api.Assertions.assertFalse(member.hasAdminAccess());
    }
}
