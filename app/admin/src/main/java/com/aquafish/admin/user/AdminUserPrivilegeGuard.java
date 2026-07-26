package com.aquafish.admin.user;

import com.aquafish.core.admin.auth.AdminAuthUser;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * RBAC 完整落地前的管理员高危操作安全边界。
 *
 * <p>该守卫不是最终权限系统，只负责阻止普通管理员提升后台权限，
 * 或操作超级管理员账号。后续由 Halo 风格 PolicyRule 授权器替代。</p>
 */
@Component
public class AdminUserPrivilegeGuard {

    /**
     * 要求当前操作者是超级管理员，用于分配后台角色等权限提升操作。
     *
     * @param operator 由后台会话解析出的当前管理员
     * @param operation 写入异常信息的业务操作名称
     */
    public void requireSuperAdmin(
        AdminAuthUser operator,
        String operation
    ) {
        requireAdmin(operator);

        if (!operator.superAdmin()) {
            throw new IllegalStateException(
                safeOperation(operation) + "只能由超级管理员执行。"
            );
        }
    }

    /**
     * 校验操作者是否可以修改目标账号，防止越权操作管理员或其他超级管理员。
     *
     * <p>超级管理员可以管理普通管理员，但不能修改其他超级管理员；普通管理员
     * 只能管理非管理员账号。用户领域写服务在执行禁用、封禁、改组等操作前调用。</p>
     */
    public void requireCanManageTarget(
        AdminAuthUser operator,
        long targetUserId,
        Collection<String> targetRoles,
        String operation
    ) {
        requireAdmin(operator);

        Set<String> roles = normalizeRoles(targetRoles);
        boolean targetIsSuperAdmin = roles.contains("super_admin");
        boolean targetIsAdmin = targetIsSuperAdmin || roles.contains("admin");

        if (targetIsSuperAdmin && operator.id() != targetUserId) {
            throw new IllegalStateException(
                "不能对其他超级管理员执行" + safeOperation(operation) + "。"
            );
        }

        if (targetIsAdmin && !operator.superAdmin()) {
            throw new IllegalStateException(
                safeOperation(operation) + "管理员账号需要超级管理员权限。"
            );
        }
    }

    /**
     * 所有用户管理动作的最低门槛：必须具有后台访问权限。
     */
    private void requireAdmin(AdminAuthUser operator) {
        if (operator == null || !operator.hasAdminAccess()) {
            throw new IllegalStateException("当前登录账号没有后台管理权限。");
        }
    }

    /**
     * 规范化目标角色集合，确保权限判断不受大小写和空白差异影响。
     */
    private Set<String> normalizeRoles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }

        return roles.stream()
            .filter(role -> role != null && !role.isBlank())
            .map(role -> role.trim().toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 为越权异常提供稳定、可读的操作名称。
     */
    private String safeOperation(String operation) {
        return operation == null || operation.isBlank()
            ? "当前操作"
            : operation.trim();
    }
}
