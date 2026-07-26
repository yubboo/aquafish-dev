package com.aquafish.template.resolve;

import com.aquafish.template.core.TemplateType;
import com.aquafish.template.core.TemplateTypes;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Aquafish 核心内置只读模板解析器。
 *
 * <p>
 * 本组件负责从 template 模块自身的 classpath 资源中，
 * 查找平台随核心程序发布的安全兜底模板。
 * </p>
 *
 * <p>资源目录固定为：</p>
 *
 * <pre>
 * classpath:/aquafish/core-fallback/templates/
 * </pre>
 *
 * <p>完整目标回退顺序：</p>
 *
 * <pre>
 * 当前活动主题
 * -> 父主题继承链
 * -> 外置官方 default
 * -> 核心内置只读 fallback
 * -> 最小紧急静态页面
 * </pre>
 *
 * <p>
 * 核心 fallback 与外置 default 存在本质区别：
 * </p>
 *
 * <ul>
 *     <li>
 *         外置 default 位于 workdir/themes，
 *         可以通过安装、更新或删除主题包进行管理；
 *     </li>
 *     <li>
 *         核心 fallback 位于应用程序 JAR 内部，
 *         普通主题管理操作不能修改或删除；
 *     </li>
 *     <li>
 *         外置 default 可以使用 Thymeleaf 或 Pebble；
 *     </li>
 *     <li>
 *         核心 fallback 固定使用 Thymeleaf，
 *         保持平台最后安全渲染路径稳定。
 *     </li>
 * </ul>
 *
 * <p>
 * 本组件当前只负责确认 classpath 模板是否存在，
 * 并返回对应的 {@link ResolvedTemplate}。
 * </p>
 *
 * <p>
 * 当前尚未把 classpath 模板接入
 * {@link ThemeTemplateResolver} 和 Thymeleaf 渲染器。
 * 后续步骤会分别完成回退链集成和 classpath 渲染。
 * </p>
 */
@Component
public class CoreFallbackTemplateResolver {

    /**
     * 核心 fallback 在诊断结果中的固定主题名称。
     *
     * <p>
     * 它不是真正安装在 themes 目录中的主题，
     * 该名称只用于日志、诊断和渲染结果标识。
     * </p>
     */
    public static final String CORE_FALLBACK_THEME_NAME =
        "aquafish-core-fallback";

    /**
     * 核心 fallback 固定使用 Thymeleaf。
     */
    public static final String CORE_FALLBACK_ENGINE_ID =
        "thymeleaf";

    /**
     * classpath 中核心模板资源根目录。
     *
     * <p>
     * ClassPathResource 使用的路径不能以斜杠开头。
     * </p>
     */
    public static final String CORE_FALLBACK_RESOURCE_ROOT =
        "aquafish/core-fallback/templates/";

    /**
     * 查找指定模板类型对应的核心只读模板。
     *
     * <p>处理流程：</p>
     *
     * <ol>
     *     <li>检查模板类型是否为空；</li>
     *     <li>读取模板类型的默认相对路径；</li>
     *     <li>执行路径安全校验；</li>
     *     <li>拼接 classpath 资源路径；</li>
     *     <li>检查资源是否存在且可读；</li>
     *     <li>返回记录 Thymeleaf 引擎的解析结果。</li>
     * </ol>
     *
     * @param templateType 需要查找的模板类型
     * @return 找到时返回核心 fallback 模板；
     *         缺失时返回 Optional.empty()
     */
    public Optional<ResolvedTemplate> resolve(
        TemplateType templateType
    ) {
        if (templateType == null) {
            throw new IllegalArgumentException(
                "核心 fallback 模板类型不能为空。"
            );
        }

        String relativeTemplatePath =
            normalizeRelativeTemplatePath(
                templateType.defaultTemplatePath()
            );

        String resourcePath =
            CORE_FALLBACK_RESOURCE_ROOT
                + relativeTemplatePath;

        Resource resource =
            new ClassPathResource(resourcePath);

        if (
            !resource.exists()
                || !resource.isReadable()
        ) {
            return Optional.empty();
        }

        return Optional.of(
            new ResolvedTemplate(
                templateType,
                CORE_FALLBACK_THEME_NAME,
                CORE_FALLBACK_ENGINE_ID,
                relativeTemplatePath,
                "classpath:/" + resourcePath,
                true,
                "当前主题继承链和外置 default 均未提供模板，"
                    + "已解析到 Aquafish 核心内置只读 fallback："
                    + resourcePath
            )
        );
    }

    /**
     * 获取必须存在的核心 fallback 模板。
     *
     * <p>
     * 该方法适用于系统启动检查、发布完整性检查
     * 和自动化测试。
     * </p>
     *
     * @param templateType 模板类型
     * @return 必须存在的核心 fallback 模板
     * @throws IllegalStateException 当核心资源缺失时抛出
     */
    public ResolvedTemplate require(
        TemplateType templateType
    ) {
        return resolve(templateType)
            .orElseThrow(
                () -> new IllegalStateException(
                    "核心内置 fallback 模板不存在："
                        + templateType.defaultTemplatePath()
                )
            );
    }

    /**
     * 根据模板类型 key 查找核心 fallback。
     *
     * @param templateTypeKey 模板类型唯一 key
     * @return 对应核心 fallback 模板
     */
    public Optional<ResolvedTemplate> resolve(
        String templateTypeKey
    ) {
        return resolve(
            TemplateTypes.require(
                templateTypeKey
            )
        );
    }

    /**
     * 检查全部内置模板类型的核心 fallback。
     *
     * <p>
     * 只要其中任意模板缺失，
     * require 方法就会抛出明确异常。
     * </p>
     *
     * @return 全部内置模板类型对应的不可修改结果列表
     */
    public List<ResolvedTemplate>
        resolveAllBuiltInTypes() {

        return TemplateTypes.all()
            .stream()
            .map(this::require)
            .toList();
    }

    /**
     * 判断解析结果是否来自核心 fallback。
     *
     * @param resolvedTemplate 已解析模板结果
     * @return 来自核心 fallback 时返回 true
     */
    public boolean isCoreFallback(
        ResolvedTemplate resolvedTemplate
    ) {
        return resolvedTemplate != null
            && CORE_FALLBACK_THEME_NAME.equals(
                resolvedTemplate.themeName()
            );
    }

    /**
     * 标准化并验证核心 fallback 相对路径。
     *
     * <p>
     * 虽然 TemplateType 已经执行过路径校验，
     * 核心安全边界仍然独立复核，
     * 避免未来新增其他调用入口后绕过安全规则。
     * </p>
     *
     * @param value 原始模板相对路径
     * @return 安全的 classpath 相对模板路径
     */
    private String normalizeRelativeTemplatePath(
        String value
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                "核心 fallback 模板路径不能为空。"
            );
        }

        String normalized = value
            .trim()
            .replace("\\", "/");

        if (
            normalized.startsWith("/")
                || normalized.equals("..")
                || normalized.startsWith("../")
                || normalized.contains("/../")
                || normalized.endsWith("/..")
        ) {
            throw new IllegalArgumentException(
                "核心 fallback 模板路径非法："
                    + value
            );
        }

        if (!normalized.endsWith(".html")) {
            throw new IllegalArgumentException(
                "核心 fallback 模板必须以 .html 结尾："
                    + value
            );
        }

        return normalized;
    }
}
