package com.aquafish.license;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Aquafish 可单独销售和校验的运行模块。
 *
 * <p>本枚举是“授权码 features 字段”和“后端 API 路径”的唯一对应表，关联
 * {@link LicenseEnforcementWebFilter}。以后新增模块时必须先在这里登记，避免某个
 * Controller 忘记加授权判断而被直接调用。</p>
 *
 * <p>{@code cms} 是早期签发工具使用的兼容总包：它同时授予 content 与 theme；
 * 新授权仍可分别签发 content、theme，实现更细粒度的商业版本组合。</p>
 */
public enum LicenseFeature {

    CONTENT("content", "内容管理"),
    THEME("theme", "主题管理"),
    PLUGIN("plugin", "插件管理"),
    FORUM("forum", "论坛管理"),
    MARKET("market", "应用市场"),
    AI("ai", "AI 能力"),
    SEARCH("search", "站内搜索"),
    UPDATES("updates", "更新服务");

    private final String code;
    private final String label;

    LicenseFeature(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /**
     * 判断一份已经验签成功的授权是否包含本模块。
     *
     * <p>只比较规范化后的功能代码，不接受模糊匹配；content/theme 额外接受 cms，
     * 是为了兼容已经签发的 CMS 总包授权。</p>
     */
    public boolean grantedBy(List<String> licensedFeatures) {
        Set<String> normalized = new HashSet<>();
        if (licensedFeatures != null) {
            for (String feature : licensedFeatures) {
                if (feature != null && !feature.isBlank()) {
                    normalized.add(feature.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (normalized.contains(code)) {
            return true;
        }
        return (this == CONTENT || this == THEME) && normalized.contains("cms");
    }

    /**
     * 根据请求路径寻找必须具备的模块授权。
     *
     * <p>同时登记后台与未来前台 API 前缀。路径按完整段匹配，防止类似
     * {@code /api/admin/aired} 被错误识别为 AI 模块。</p>
     */
    public static Optional<LicenseFeature> requiredForApiPath(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }

        if (matchesAny(path, "/api/admin/license/updates", "/api/license/updates")) {
            return Optional.of(UPDATES);
        }
        if (matchesAny(path, "/api/admin/forum", "/api/forum")) {
            return Optional.of(FORUM);
        }
        if (matchesAny(path, "/api/admin/content", "/api/content", "/api/admin/cms", "/api/cms")) {
            return Optional.of(CONTENT);
        }
        if (matchesAny(path, "/api/admin/theme", "/api/admin/themes", "/api/theme", "/api/themes")) {
            return Optional.of(THEME);
        }
        if (matchesAny(path, "/api/admin/plugin", "/api/admin/plugins", "/api/plugin", "/api/plugins")) {
            return Optional.of(PLUGIN);
        }
        if (matchesAny(path, "/api/admin/market", "/api/market")) {
            return Optional.of(MARKET);
        }
        if (matchesAny(path, "/api/admin/ai", "/api/ai")) {
            return Optional.of(AI);
        }
        if (matchesAny(path, "/api/admin/search", "/api/search")) {
            return Optional.of(SEARCH);
        }
        return Optional.empty();
    }

    /** 判断路径是否等于指定前缀，或位于该前缀的下级路径。 */
    private static boolean matchesAny(String path, String... prefixes) {
        for (String prefix : prefixes) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
