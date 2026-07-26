package com.aquafish.template.core;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Aquafish 模板类型定义。
 *
 * 当前阶段：
 * Step 17-20-2：template 模块基础类。
 *
 * 这个 record 表示一个“模板类型”。
 *
 * 例如：
 * 1. index：网站首页；
 * 2. post：文章详情页；
 * 3. forum：论坛首页；
 * 4. thread：帖子详情页；
 * 5. login：登录页。
 *
 * 为什么需要模板类型？
 *
 * 因为业务模块不能随便写字符串找模板。
 *
 * 错误做法：
 * forum 模块自己写 "forum/viewthread.html"
 * content 模块自己写 "content/view.html"
 *
 * 正确做法：
 * 业务模块告诉 template 核心：
 * 我要渲染 TemplateTypes.THREAD
 *
 * 然后 template/theme 核心统一去找当前主题里的模板文件。
 */
public record TemplateType(

    /**
     * 模板类型 key。
     *
     * 示例：
     * index
     * post
     * forum
     * thread
     * user-home
     *
     * 这个值后续会用于：
     * 1. 业务模块调用；
     * 2. 主题模板查找；
     * 3. 插件注册模板类型；
     * 4. 后台诊断显示。
     */
    String key,

    /**
     * 默认模板路径。
     *
     * 注意：
     * 这里是相对于主题 templates 目录的路径。
     *
     * 示例：
     * index.html
     * content/view.html
     * forum/viewthread.html
     *
     * 不是完整磁盘路径。
     */
    String defaultTemplatePath,

    /**
     * 模板类型显示名称。
     *
     * 主要用于后台诊断、主题开发文档、主题检查结果。
     */
    String displayName,

    /**
     * 模板类型说明。
     *
     * 用于说明这个模板类型负责渲染什么页面。
     */
    String description
) {

    /**
     * 模板类型 key 校验规则。
     *
     * 允许：
     * 1. 小写字母开头；
     * 2. 后续可以包含小写字母、数字、中横线；
     * 3. 最大长度 64。
     *
     * 合法：
     * index
     * post
     * user-home
     * forum-detail
     *
     * 非法：
     * Index
     * user_home
     * 1index
     * forum.detail
     */
    private static final Pattern KEY_PATTERN =
        Pattern.compile("^[a-z][a-z0-9-]{0,63}$");

    /**
     * record 的紧凑构造方法。
     *
     * 作用：
     * 1. 创建 TemplateType 时自动校验 key；
     * 2. 自动清理模板路径里的反斜杠；
     * 3. 防止模板路径出现 ../；
     * 4. 防止模板路径使用绝对路径。
     */
    public TemplateType {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("模板类型 key 不能为空。");
        }

        key = key.trim().toLowerCase(Locale.ROOT);

        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("非法模板类型 key：" + key);
        }

        if (defaultTemplatePath == null || defaultTemplatePath.isBlank()) {
            throw new IllegalArgumentException("模板类型默认模板路径不能为空：" + key);
        }

        defaultTemplatePath = defaultTemplatePath.trim().replace("\\", "/");

        if (defaultTemplatePath.startsWith("/")) {
            throw new IllegalArgumentException("模板路径不能使用绝对路径：" + defaultTemplatePath);
        }

        if (defaultTemplatePath.contains("../") || defaultTemplatePath.contains("..\\")) {
            throw new IllegalArgumentException("模板路径不能包含上级目录跳转：" + defaultTemplatePath);
        }

        if (!defaultTemplatePath.endsWith(".html")) {
            throw new IllegalArgumentException("模板路径必须以 .html 结尾：" + defaultTemplatePath);
        }

        displayName = normalizeText(displayName, key);
        description = normalizeText(description, "");
    }

    /**
     * 判断当前模板类型是否是指定 key。
     *
     * @param value 待比较 key
     * @return 是否相同
     */
    public boolean is(String value) {
        if (value == null) {
            return false;
        }

        return key.equals(value.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 规范化普通文本。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return 处理后的文本
     */
    private static String normalizeText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value.trim();
    }
}