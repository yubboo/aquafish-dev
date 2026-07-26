package com.aquafish.core.database;

/**
 * Aquafish 正式逻辑表名目录。
 *
 * <p>这里只保存不带前缀的逻辑表名。真实表名必须通过
 * {@link TableNameResolver} 解析，例如 {@code users -> aq_users}。
 * 常量按业务域排列，名称必须与版本化迁移 SQL 完全一致。</p>
 */
public final class TableNames {

    private TableNames() {
    }

    // 系统内核与审计。
    public static final String OPTIONS = "options";
    public static final String SYSTEM_INSTANCES = "system_instances";
    public static final String SYSTEM_MODULES = "system_modules";
    public static final String INSTALL_LOGS = "install_logs";
    public static final String ADMIN_OPERATION_LOGS = "admin_operation_logs";

    // 账号、角色、权限与管理组。
    public static final String USERS = "users";
    public static final String USER_UID_ALLOCATOR = "user_uid_allocator";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";
    public static final String USER_ROLES = "user_roles";
    public static final String ROLE_PERMISSIONS = "role_permissions";
    public static final String USER_GROUPS = "user_groups";
    public static final String USER_GROUP_PERMISSIONS = "user_group_permissions";
    public static final String ADMIN_GROUPS = "admin_groups";
    public static final String ADMIN_GROUP_USERS = "admin_group_users";
    public static final String ADMIN_GROUP_PERMISSIONS = "admin_group_permissions";

    // 用户资料、安全、关系与积分。
    public static final String USER_LOGIN_LOGS = "user_login_logs";
    public static final String USER_PROFILES = "user_profiles";
    public static final String USER_PROFILE_FIELDS = "user_profile_fields";
    public static final String USER_PROFILE_AUDITS = "user_profile_audits";
    public static final String USER_STATISTICS = "user_statistics";
    public static final String POINTS_RULES = "points_rules";
    public static final String POINTS_LOGS = "points_logs";
    public static final String POINTS_ADJUSTMENTS = "points_adjustments";
    public static final String USER_TAGS = "user_tags";
    public static final String USER_TAG_RELATIONS = "user_tag_relations";
    public static final String USER_BANS = "user_bans";
    public static final String IP_BANS = "ip_bans";
    public static final String USER_SESSIONS = "user_sessions";
    public static final String USER_OAUTH_ACCOUNTS = "user_oauth_accounts";
    public static final String USER_RELATIONSHIPS = "user_relationships";
    public static final String USER_VERIFICATIONS = "user_verifications";
    public static final String USER_VERIFICATION_TOKENS = "user_verification_tokens";

    // 论坛。
    public static final String FORUM_SECTIONS = "forum_sections";
    public static final String FORUM_SECTION_MODERATORS = "forum_section_moderators";
    public static final String FORUM_THREADS = "forum_threads";
    public static final String FORUM_POSTS = "forum_posts";
    public static final String FORUM_THREAD_SUBSCRIPTIONS = "forum_thread_subscriptions";
    public static final String FORUM_MODERATION_ACTIONS = "forum_moderation_actions";
    public static final String FORUM_NOTIFICATION_OUTBOX = "forum_notification_outbox";
    public static final String FORUM_REPORTS = "forum_reports";
    public static final String FORUM_POST_REACTIONS = "forum_post_reactions";

    // 通用媒体。
    public static final String MEDIA_ASSETS = "media_assets";
    public static final String MEDIA_USAGES = "media_usages";

    // CMS / 博客。
    public static final String CONTENT_ARTICLES = "content_articles";
    public static final String CONTENT_PAGES = "content_pages";
    public static final String CONTENT_CATEGORIES = "content_categories";
    public static final String CONTENT_TAGS = "content_tags";
    public static final String CONTENT_ARTICLE_CATEGORIES = "content_article_categories";
    public static final String CONTENT_ARTICLE_TAGS = "content_article_tags";
    public static final String CONTENT_COMMENTS = "content_comments";
    public static final String CONTENT_REVISIONS = "content_revisions";

    // 客户实例授权审计；原始签名授权码仍只保存在 workdir。
    public static final String LICENSE_ACTIVATIONS = "license_activations";
    public static final String LICENSE_ENTITLEMENTS = "license_entitlements";
    public static final String LICENSE_VALIDATION_EVENTS = "license_validation_events";

    // 主题、插件与市场。
    public static final String THEMES = "themes";
    public static final String THEME_SETTINGS = "theme_settings";
    public static final String PLUGINS = "plugins";
    public static final String PLUGIN_DEPENDENCIES = "plugin_dependencies";
    public static final String PLUGIN_SETTINGS = "plugin_settings";
    public static final String PLUGIN_PERMISSIONS = "plugin_permissions";
    public static final String MARKET_PACKAGES = "market_packages";
    public static final String MARKET_INSTALLATIONS = "market_installations";

    // AI。
    public static final String AI_PROVIDERS = "ai_providers";
    public static final String AI_PROVIDER_CREDENTIALS = "ai_provider_credentials";
    public static final String AI_MODELS = "ai_models";
    public static final String AI_PROMPTS = "ai_prompts";
    public static final String AI_TASKS = "ai_tasks";
    public static final String AI_AUDIT_RECORDS = "ai_audit_records";

    // 搜索。
    public static final String SEARCH_DOCUMENTS = "search_documents";
    public static final String SEARCH_INDEX_QUEUE = "search_index_queue";
}
