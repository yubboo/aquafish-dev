package com.aquafish.core.permalink;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Aquafish 固定链接生成器。
 *
 * 当前阶段：
 * Step 17-21-5：固定链接生成器。
 *
 * 作用：
 * 1. 读取当前固定链接设置；
 * 2. 根据目标类型选择对应 pattern；
 * 3. 替换 {id}、{slug}、{key}、{fid}、{tid}、{uid}、{name} 等占位符；
 * 4. 返回最终 permalink。
 *
 * 重要原则：
 * 以后模板里不要自己拼链接。
 *
 * 正确方式：
 * article.permalink
 * thread.permalink
 * forum.permalink
 *
 * 这些 permalink 应该由后端业务模块调用本类生成。
 */
@Service
public class PermalinkBuilder {

    private final PermalinkSettingsService permalinkSettingsService;

    public PermalinkBuilder(PermalinkSettingsService permalinkSettingsService) {
        this.permalinkSettingsService = permalinkSettingsService;
    }

    /**
     * 使用当前系统配置生成固定链接。
     */
    public String build(PermalinkBuildRequest request) {
        return build(permalinkSettingsService.getSettings(), request);
    }

    /**
     * 使用指定配置生成固定链接。
     */
    public String build(PermalinkSettings settings, PermalinkBuildRequest request) {
        PermalinkSettings normalized = safeSettings(settings).normalized();
        PermalinkBuildRequest safeRequest = safeRequest(request);

        String pattern = patternFor(normalized, safeRequest.safeType());

        return applyPattern(pattern, safeRequest.toPlaceholderValues());
    }

    /**
     * 生成一组开发演示链接。
     */
    public Map<String, String> buildDemoLinks() {
        Map<String, String> result = new LinkedHashMap<>();

        result.put("article", build(PermalinkBuildRequest.article(1L, "demo")));
        result.put("page", build(PermalinkBuildRequest.page(1L, "about")));
        result.put("category", build(PermalinkBuildRequest.category(1L, "dev")));
        result.put("tag", build(PermalinkBuildRequest.tag(1L, "ai")));
        result.put("forum", build(PermalinkBuildRequest.forum(1L, "general")));
        result.put("thread", build(PermalinkBuildRequest.thread(1L, "demo")));
        result.put("user", build(PermalinkBuildRequest.user(1L, "admin")));

        return result;
    }

    /**
     * 根据目标类型选择 pattern。
     */
    private String patternFor(PermalinkSettings settings, PermalinkTargetType type) {
        return switch (type) {
            case ARTICLE -> settings.articlePattern();
            case PAGE -> settings.pagePattern();
            case CATEGORY -> settings.categoryPattern();
            case TAG -> settings.tagPattern();
            case FORUM -> settings.forumPattern();
            case THREAD -> settings.threadPattern();
            case USER -> settings.userPattern();
        };
    }

    /**
     * 替换 pattern 中的占位符。
     */
    private String applyPattern(String pattern, Map<String, String> values) {
        if (pattern == null || pattern.isBlank()) {
            return "";
        }

        String result = pattern.trim();

        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return normalizeResult(result);
    }

    /**
     * 最终结果归一化。
     *
     * 当前只做轻量处理：
     * 1. 去掉首尾空格；
     * 2. 避免出现重复斜杠；
     * 3. 保留 http:// 和 https:// 的双斜杠。
     */
    private String normalizeResult(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String result = value.trim();

        if (result.startsWith("http://") || result.startsWith("https://")) {
            return result;
        }

        while (result.contains("//")) {
            result = result.replace("//", "/");
        }

        return result;
    }

    private PermalinkSettings safeSettings(PermalinkSettings settings) {
        if (settings == null) {
            return PermalinkSettings.defaultSettings();
        }

        return settings;
    }

    private PermalinkBuildRequest safeRequest(PermalinkBuildRequest request) {
        if (request == null) {
            return PermalinkBuildRequest.article(1L, "demo");
        }

        return request;
    }
}
