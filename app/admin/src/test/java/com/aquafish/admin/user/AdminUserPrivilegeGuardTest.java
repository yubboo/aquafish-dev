package com.aquafish.admin.user;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.admin.auth.AdminAuthUser;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminUserPrivilegeGuardTest {

    private final AdminUserPrivilegeGuard guard = new AdminUserPrivilegeGuard();

    @Test
    void shouldRequireSuperAdminForPrivilegeAssignment() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> guard.requireSuperAdmin(admin(1), "分配管理组")
        );

        assertTrue(error.getMessage().contains("超级管理员"));
        assertDoesNotThrow(
            () -> guard.requireSuperAdmin(superAdmin(1), "分配管理组")
        );
    }

    @Test
    void shouldPreventAdminFromManagingAnotherAdmin() {
        assertThrows(
            IllegalStateException.class,
            () -> guard.requireCanManageTarget(
                admin(1),
                2,
                List.of("admin"),
                "重置密码"
            )
        );
    }

    @Test
    void shouldPreventManagingAnotherSuperAdmin() {
        assertThrows(
            IllegalStateException.class,
            () -> guard.requireCanManageTarget(
                superAdmin(1),
                2,
                List.of("super_admin"),
                "禁用"
            )
        );
    }

    @Test
    void shouldAllowAdminToManageOrdinaryUser() {
        assertDoesNotThrow(
            () -> guard.requireCanManageTarget(
                admin(1),
                2,
                List.of("member"),
                "重置密码"
            )
        );
    }

    @Test
    void shouldAllowSuperAdminToManageOwnAccount() {
        assertDoesNotThrow(
            () -> guard.requireCanManageTarget(
                superAdmin(1),
                1,
                List.of("super_admin"),
                "重置密码"
            )
        );
    }

    private AdminAuthUser admin(long id) {
        return user(id, List.of("admin"), false);
    }

    private AdminAuthUser superAdmin(long id) {
        return user(id, List.of("super_admin"), true);
    }

    private AdminAuthUser user(
        long id,
        List<String> roles,
        boolean superAdmin
    ) {
        return new AdminAuthUser(
            id,
            "user" + id,
            "user" + id + "@example.com",
            "User " + id,
            "",
            "ACTIVE",
            roles,
            superAdmin
        );
    }
}
