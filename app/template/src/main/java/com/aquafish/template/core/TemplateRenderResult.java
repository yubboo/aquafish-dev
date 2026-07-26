package com.aquafish.template.core;

/**
 * 模板渲染结果对象。
 *
 * 当前阶段：
 * Step 17-20-2：先定义渲染结果结构。
 *
 * 后续真正接入 Thymeleaf 后，TemplateRenderService 会返回这个对象。
 */
public record TemplateRenderResult(

    /**
     * 是否渲染成功。
     */
    boolean success,

    /**
     * 渲染后的 HTML 内容。
     */
    String html,

    /**
     * 实际使用的模板路径。
     *
     * 示例：
     * workdir/themes/default/templates/index.html
     */
    String templatePath,

    /**
     * 实际使用的主题名称。
     */
    String themeName,

    /**
     * 是否命中缓存。
     */
    boolean cacheHit,

    /**
     * 错误信息。
     *
     * success = true 时通常为空。
     */
    String errorMessage
) {

    public TemplateRenderResult {
        html = html == null ? "" : html;
        templatePath = normalizeNullableText(templatePath);
        themeName = normalizeNullableText(themeName);
        errorMessage = normalizeNullableText(errorMessage);
    }

    /**
     * 创建成功结果。
     *
     * @param html HTML 内容
     * @param templatePath 模板路径
     * @param themeName 主题名称
     * @param cacheHit 是否命中缓存
     * @return 渲染结果
     */
    public static TemplateRenderResult success(
        String html,
        String templatePath,
        String themeName,
        boolean cacheHit
    ) {
        return new TemplateRenderResult(
            true,
            html,
            templatePath,
            themeName,
            cacheHit,
            null
        );
    }

    /**
     * 创建失败结果。
     *
     * @param errorMessage 错误信息
     * @return 渲染结果
     */
    public static TemplateRenderResult failure(String errorMessage) {
        return new TemplateRenderResult(
            false,
            "",
            null,
            null,
            false,
            errorMessage
        );
    }

    private static String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}