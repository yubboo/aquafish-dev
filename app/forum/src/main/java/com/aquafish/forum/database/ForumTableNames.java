package com.aquafish.forum.database;

/**
 * 论坛模块逻辑表名。
 *
 * <p>这里只保存不含安装前缀的逻辑名称。
 * 真实表名必须在运行时交给 core 的 TableNameResolver 生成，
 * 不允许在论坛业务代码中写死 aq_ 或其他前缀。</p>
 *
 * <p>论坛表常量留在 forum 模块，避免将具体业务知识污染到 core。</p>
 */
public final class ForumTableNames {

    public static final String SECTIONS = "forum_sections";
    public static final String SECTION_MODERATORS = "forum_section_moderators";
    public static final String THREADS = "forum_threads";
    public static final String POSTS = "forum_posts";
    public static final String THREAD_SUBSCRIPTIONS = "forum_thread_subscriptions";
    public static final String MODERATION_ACTIONS = "forum_moderation_actions";
    public static final String NOTIFICATION_OUTBOX = "forum_notification_outbox";

    /** 工具常量类不允许实例化。 */
    private ForumTableNames() {
    }
}
