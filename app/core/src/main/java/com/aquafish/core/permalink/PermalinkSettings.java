package com.aquafish.core.permalink;

/**
 * Aquafish 固定链接设置。
 *
 * 当前阶段：
 * Step 17-21-3：固定链接后端配置接口。
 *
 * 说明：
 * 这个对象会保存到：
 * workdir/settings/permalink.json
 *
 * 后续模板里不应该自己拼链接。
 * 应该由后端生成：
 * article.permalink
 * thread.permalink
 * forum.permalink
 * category.permalink
 * tag.permalink
 */
public record PermalinkSettings(
    PermalinkMode mode,
    String articlePattern,
    String pagePattern,
    String categoryPattern,
    String tagPattern,
    String forumPattern,
    String threadPattern,
    String userPattern,
    boolean enableDiscuzCompat,
    boolean enableHaloCompat,
    boolean enableOldLinkRedirect
) {

    /**
     * 默认固定链接配置。
     *
     * 默认使用 short 短链接模式。
     */
    public static PermalinkSettings defaultSettings() {
        return shortPreset();
    }

    /**
     * 根据模式返回预设配置。
     */
    public static PermalinkSettings preset(PermalinkMode mode) {
        PermalinkMode safeMode = mode == null ? PermalinkMode.SHORT : mode;

        return switch (safeMode) {
            case SHORT -> shortPreset();
            case HALO -> haloPreset();
            case DISCUZ -> discuzPreset();
            case CUSTOM -> customPreset();
        };
    }

    /**
     * short 短链接模式。
     */
    public static PermalinkSettings shortPreset() {
        return new PermalinkSettings(
            PermalinkMode.SHORT,
            "/p/{id}",
            "/page/{slug}",
            "/c/{key}",
            "/tag/{key}",
            "/f/{key}",
            "/t/{tid}",
            "/u/{name}",
            true,
            true,
            true
        );
    }

    /**
     * Halo CMS 风格。
     */
    public static PermalinkSettings haloPreset() {
        return new PermalinkSettings(
            PermalinkMode.HALO,
            "/archives/{slug}",
            "/page/{slug}",
            "/categories/{slug}",
            "/tags/{slug}",
            "/f/{key}",
            "/t/{tid}",
            "/u/{name}",
            false,
            true,
            true
        );
    }

    /**
     * Discuz 兼容风格。
     */
    public static PermalinkSettings discuzPreset() {
        return new PermalinkSettings(
            PermalinkMode.DISCUZ,
            "article-{id}.html",
            "/page/{slug}",
            "category-{id}.html",
            "tag-{id}.html",
            "forum-{fid}.html",
            "thread-{tid}.html",
            "space-{uid}.html",
            true,
            false,
            true
        );
    }

    /**
     * 自定义模式。
     *
     * 自定义模式默认也给一套安全规则。
     */
    public static PermalinkSettings customPreset() {
        return new PermalinkSettings(
            PermalinkMode.CUSTOM,
            "/article/{id}",
            "/page/{slug}",
            "/category/{slug}",
            "/tag/{slug}",
            "/forum/{key}",
            "/thread/{tid}",
            "/user/{name}",
            true,
            true,
            true
        );
    }

    /**
     * 归一化配置。
     *
     * 作用：
     * 1. 防止 mode 为空；
     * 2. 防止某个 pattern 为空；
     * 3. 防止前端传了空字符串导致系统生成空链接。
     */
    public PermalinkSettings normalized() {
        PermalinkMode safeMode = mode == null ? PermalinkMode.SHORT : mode;
        PermalinkSettings fallback = preset(safeMode);

        return new PermalinkSettings(
            safeMode,
            textOrDefault(articlePattern, fallback.articlePattern),
            textOrDefault(pagePattern, fallback.pagePattern),
            textOrDefault(categoryPattern, fallback.categoryPattern),
            textOrDefault(tagPattern, fallback.tagPattern),
            textOrDefault(forumPattern, fallback.forumPattern),
            textOrDefault(threadPattern, fallback.threadPattern),
            textOrDefault(userPattern, fallback.userPattern),
            enableDiscuzCompat,
            enableHaloCompat,
            enableOldLinkRedirect
        );
    }

    private static String textOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value.trim();
    }
}