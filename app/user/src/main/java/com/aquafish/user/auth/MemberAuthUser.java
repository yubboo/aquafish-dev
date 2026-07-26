package com.aquafish.user.auth;

import java.util.Set;

/**
 * 已通过服务端数据库会话校验的前台会员主体。
 *
 * <p>该对象只由 {@link MemberAuthService} 创建。浏览器提交的用户 ID、用户组、
 * 权限和封禁状态均不可信，Controller 不得自行构造认证主体。</p>
 *
 * @param id 用户数据库主键
 * @param uid 用户可见且可复用的正整数编号
 * @param publicId 对外公开稳定编号
 * @param username 用户名
 * @param displayName 展示名称
 * @param avatar 头像地址
 * @param groupId 前台用户组 ID
 * @param groupKey 前台用户组稳定标识
 * @param roles 当前账号的角色稳定标识快照
 * @param permissions 当前用户组的前台权限快照
 * @param forumPostingBanned 是否存在有效的 post/all 封禁
 */
public record MemberAuthUser(
    long id,
    long uid,
    String publicId,
    String username,
    String displayName,
    String avatar,
    Long groupId,
    String groupKey,
    Set<String> roles,
    Set<String> permissions,
    boolean forumPostingBanned
) {

    /**
     * 复制不可变权限快照，避免认证完成后被业务层篡改。
     */
    public MemberAuthUser {
        if (id <= 0L) {
            throw new IllegalStateException("会员认证主体缺少有效用户 ID。");
        }
        if (uid <= 0L) {
            throw new IllegalStateException("会员认证主体缺少有效 UID。");
        }
        publicId = safe(publicId);
        username = safe(username);
        displayName = safe(displayName);
        avatar = safe(avatar);
        groupKey = safe(groupKey);
        roles = roles == null || roles.isEmpty()
            ? Set.of()
            : Set.copyOf(roles);
        permissions = permissions == null || permissions.isEmpty()
            ? Set.of()
            : Set.copyOf(permissions);
    }

    /**
     * 只有服务端角色表中明确存在管理员角色时才允许显示后台入口。
     */
    public boolean hasAdminAccess() {
        return roles.contains("super_admin") || roles.contains("admin");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
