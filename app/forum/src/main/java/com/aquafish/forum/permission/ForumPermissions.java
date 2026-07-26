package com.aquafish.forum.permission;

import java.util.List;

/**
 * 论坛第一版稳定权限节点。
 *
 * <p>权限键是数据库、后台角色和业务校验共用的长期契约，
 * 一旦发布不能随意改名。页面文案可以改，但不能用页面菜单名代替权限键。</p>
 *
 * <p>前台用户组权限和后台管理权限分组保存，
 * 后续权限装配时不能因为后台角色而自动获得任意板块的版主范围。</p>
 */
public final class ForumPermissions {

    // 前台阅读、发布与个人内容管理权限。
    public static final String THREAD_READ = "forum.thread.read";
    public static final String THREAD_CREATE = "forum.thread.create";
    public static final String THREAD_REPLY = "forum.thread.reply";
    public static final String THREAD_EDIT_OWN = "forum.thread.edit_own";
    public static final String POST_EDIT_OWN = "forum.post.edit_own";
    public static final String THREAD_SUBSCRIBE = "forum.thread.subscribe";
    public static final String ATTACHMENT_UPLOAD = "forum.attachment.upload";

    // 后台管理和版主操作权限。
    public static final String SECTION_MANAGE = "forum.section.manage";
    public static final String THREAD_AUDIT = "forum.thread.audit";
    public static final String THREAD_CLOSE = "forum.thread.close";
    public static final String THREAD_PIN = "forum.thread.pin";
    public static final String THREAD_FEATURE = "forum.thread.feature";
    public static final String THREAD_MOVE = "forum.thread.move";
    public static final String THREAD_DELETE = "forum.thread.delete";
    public static final String POST_AUDIT = "forum.post.audit";
    public static final String POST_DELETE = "forum.post.delete";
    public static final String MODERATOR_MANAGE = "forum.moderator.manage";

    /**
     * 前台用户组可被单独授予的权限集。
     */
    public static final List<String> MEMBER_PERMISSIONS = List.of(
        THREAD_READ,
        THREAD_CREATE,
        THREAD_REPLY,
        THREAD_EDIT_OWN,
        POST_EDIT_OWN,
        THREAD_SUBSCRIBE,
        ATTACHMENT_UPLOAD
    );

    /**
     * 后台角色或版主可被授予的管理权限集。
     */
    public static final List<String> MANAGEMENT_PERMISSIONS = List.of(
        SECTION_MANAGE,
        THREAD_AUDIT,
        THREAD_CLOSE,
        THREAD_PIN,
        THREAD_FEATURE,
        THREAD_MOVE,
        THREAD_DELETE,
        POST_AUDIT,
        POST_DELETE,
        MODERATOR_MANAGE
    );

    private ForumPermissions() {
    }
}
