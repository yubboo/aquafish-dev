package com.aquafish.template.core;

import java.util.Locale;
import java.util.Map;

/**
 * 模板渲染请求对象。
 *
 * 当前阶段：
 * Step 17-20-2：先定义渲染请求结构，不急着真正接入 Thymeleaf 渲染。
 *
 * 后续 forum/content/user 等业务模块渲染前台页面时，不应该直接找模板文件。
 *
 * 正确流程：
 *
 * 业务模块组装 ViewModel
 *        ↓
 * 创建 TemplateRenderRequest
 *        ↓
 * 调用 TemplateRenderService.render()
 *        ↓
 * template/theme 核心统一处理模板路径、主题、缓存、安全和渲染。
 */
public record TemplateRenderRequest(

    /**
     * 要渲染的模板类型。
     *
     * 示例：
     * TemplateTypes.INDEX
     * TemplateTypes.POST
     * TemplateTypes.THREAD
     */
    TemplateType templateType,

    /**
     * 页面数据模型。
     *
     * 这里只允许放安全的 ViewModel 数据。
     * 后续模板只能访问这里面的内容。
     *
     * 不建议直接把数据库实体、Service、Repository 放进去。
     */
    Map<String, Object> model,

    /**
     * 指定主题名称。
     *
     * 当前可以为空。
     * 为空时，后续会由 theme 模块读取当前启用主题。
     */
    String themeName,

    /**
     * 兜底模板路径。
     *
     * 当前可以为空。
     * 后续如果主题模板不存在，可以尝试用这个路径兜底。
     */
    String fallbackTemplatePath,

    /**
     * 页面语言。
     *
     * 后续多语言系统会用到。
     */
    Locale locale,

    /**
     * 缓存 key。
     *
     * 当前可以为空。
     * 后续页面缓存、片段缓存会用到。
     */
    String cacheKey
) {

    /**
     * record 紧凑构造方法。
     *
     * 作用：
     * 1. 校验 templateType；
     * 2. model 为空时自动变成空 Map；
     * 3. locale 为空时默认使用简体中文；
     * 4. 字符串字段自动 trim。
     */
    public TemplateRenderRequest {
        if (templateType == null) {
            throw new IllegalArgumentException("模板渲染请求 templateType 不能为空。");
        }

        model = model == null ? Map.of() : Map.copyOf(model);
        themeName = normalizeNullableText(themeName);
        fallbackTemplatePath = normalizeNullableText(fallbackTemplatePath);
        locale = locale == null ? Locale.SIMPLIFIED_CHINESE : locale;
        cacheKey = normalizeNullableText(cacheKey);
    }

    /**
     * 创建最简单的模板渲染请求。
     *
     * @param templateType 模板类型
     * @param model 页面数据模型
     * @return 模板渲染请求
     */
    public static TemplateRenderRequest of(TemplateType templateType, Map<String, Object> model) {
        return new TemplateRenderRequest(
            templateType,
            model,
            null,
            null,
            Locale.SIMPLIFIED_CHINESE,
            null
        );
    }

    /**
     * 根据模板类型 key 创建渲染请求。
     *
     * @param templateTypeKey 模板类型 key
     * @param model 页面数据模型
     * @return 模板渲染请求
     */
    public static TemplateRenderRequest of(String templateTypeKey, Map<String, Object> model) {
        return of(TemplateTypes.require(templateTypeKey), model);
    }

    /**
     * 返回一个指定主题的新请求对象。
     *
     * record 是不可变对象，所以这里不是修改当前对象，而是创建一个新对象。
     *
     * @param value 主题名称
     * @return 新请求对象
     */
    public TemplateRenderRequest withThemeName(String value) {
        return new TemplateRenderRequest(
            templateType,
            model,
            value,
            fallbackTemplatePath,
            locale,
            cacheKey
        );
    }

    /**
     * 返回一个指定缓存 key 的新请求对象。
     *
     * @param value 缓存 key
     * @return 新请求对象
     */
    public TemplateRenderRequest withCacheKey(String value) {
        return new TemplateRenderRequest(
            templateType,
            model,
            themeName,
            fallbackTemplatePath,
            locale,
            value
        );
    }

    private static String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}