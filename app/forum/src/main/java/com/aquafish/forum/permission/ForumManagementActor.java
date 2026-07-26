package com.aquafish.forum.permission;

import java.util.Set;

/**
 * 论坛后台操作人的最小安全上下文。
 *
 * <p>该结构不保存密码、Cookie 或会话令牌，只保留业务授权所需的
 * 用户 ID、超级管理员标记和已解析权限键。</p>
 */
public record ForumManagementActor(
    long userId,
    boolean superAdmin,
    Set<String> permissions
) {

    /**
     * 创建不可变的安全上下文，防止调用方在校验后修改权限集。
     */
    public ForumManagementActor {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /**
     * 校验当前操作人是否拥有指定论坛权限。
     *
     * <p>超级管理员可跳过权限键配置，但普通后台角色必须明确拥有权限。
     * 后续的版主操作还要额外校验板块授权范围，不能只依赖该方法。</p>
     *
     * @param permission 要求的稳定权限键
     */
    public void require(String permission) {
        if (userId <= 0) {
            throw new IllegalStateException("论坛管理操作缺少有效操作人。");
        }
        if (!superAdmin && !permissions.contains(permission)) {
            throw new IllegalStateException("当前用户没有论坛管理权限：" + permission);
        }
    }
}
