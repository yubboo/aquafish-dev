package com.aquafish.forum.permission;

import java.util.Set;

/**
 * 论坛前台会员的最小可信安全上下文。
 *
 * <p>该对象只能由服务端统一认证与用户权限装配层创建，控制器不得直接使用请求参数中的
 * userId、封禁标记或权限集合构造它。forum 模块只消费已经解析好的安全结论，
 * 不复制用户模块的账号、用户组和封禁查询逻辑。</p>
 *
 * <p>第一版把“指定用户组可发帖”和“私有板块可阅读”解析为板块 ID 集合。
 * 后续用户模块可以从用户组权限配置中生成这些集合，而论坛领域服务无需感知配置格式。</p>
 *
 * @param userId 已认证用户 ID；0 表示匿名访问者
 * @param active 账号是否处于可用状态
 * @param forumPostingBanned 是否存在有效的论坛发布封禁
 * @param permissions 已解析的论坛前台权限键
 * @param selectedPostingSectionIds 通过指定用户组策略获准发布的板块 ID
 * @param privateReadableSectionIds 获准读取的私有板块 ID
 */
public record ForumMemberActor(
    long userId,
    boolean active,
    boolean forumPostingBanned,
    Set<String> permissions,
    Set<Long> selectedPostingSectionIds,
    Set<Long> privateReadableSectionIds
) {

    /**
     * 所有集合都复制为不可变快照，避免权限检查完成后被调用方修改。
     */
    public ForumMemberActor {
        permissions = immutableStrings(permissions);
        selectedPostingSectionIds = immutableIds(selectedPostingSectionIds);
        privateReadableSectionIds = immutableIds(privateReadableSectionIds);
    }

    /**
     * 创建匿名访问者。匿名访问者只能读取公开板块。
     */
    public static ForumMemberActor anonymous() {
        return new ForumMemberActor(
            0L,
            false,
            false,
            Set.of(),
            Set.of(),
            Set.of()
        );
    }

    /**
     * 校验发布主题所需的账号状态、封禁状态和稳定权限键。
     */
    public void requireCanCreateThread() {
        requireActiveMember();
        if (forumPostingBanned) {
            throw new IllegalStateException("当前账号已被禁止发布论坛内容。");
        }
        requirePermission(ForumPermissions.THREAD_CREATE);
    }

    /**
     * 校验会员板块或私有板块的读取权限。
     */
    public void requireCanReadThread() {
        requireActiveMember();
        requirePermission(ForumPermissions.THREAD_READ);
    }

    /**
     * 判断当前用户是否通过指定用户组策略获准在该板块发帖。
     */
    public boolean canPublishInSelectedSection(long sectionId) {
        return selectedPostingSectionIds.contains(sectionId);
    }

    /**
     * 判断当前用户是否获准读取该私有板块。
     */
    public boolean canReadPrivateSection(long sectionId) {
        return privateReadableSectionIds.contains(sectionId);
    }

    private void requireActiveMember() {
        if (userId <= 0) {
            throw new IllegalStateException("该论坛操作需要先登录。");
        }
        if (!active) {
            throw new IllegalStateException("当前账号不可用，无法执行论坛操作。");
        }
    }

    private void requirePermission(String permission) {
        if (!permissions.contains(permission)) {
            throw new IllegalStateException("当前用户缺少论坛权限：" + permission);
        }
    }

    private static Set<String> immutableStrings(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        for (String value : source) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("论坛权限集合不能包含空权限键。");
            }
        }
        return Set.copyOf(source);
    }

    private static Set<Long> immutableIds(Set<Long> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        for (Long value : source) {
            if (value == null || value <= 0) {
                throw new IllegalStateException("论坛板块授权集合只能包含有效板块 ID。");
            }
        }
        return Set.copyOf(source);
    }
}
