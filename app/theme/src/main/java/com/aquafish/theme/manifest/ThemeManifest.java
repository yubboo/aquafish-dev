package com.aquafish.theme.manifest;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Aquafish 可移植主题清单。
 *
 * <p>
 * 本对象只表示 theme.yaml 自身声明的数据，
 * 不包含主题在当前服务器上的绝对路径，
 * 也不包含 templates、assets 或 settings.yaml
 * 是否真实存在等运行状态。
 * </p>
 *
 * <p>数据转换关系：</p>
 *
 * <pre>
 * theme.yaml
 * -> ThemeManifest
 * -> ThemeScanner
 * -> ThemeDescriptor
 * </pre>
 *
 * <p>
 * ThemeManifest 可以用于主题包上传、应用中心、
 * 安装前检查和版本兼容检查。
 * ThemeDescriptor 则继续表示已经安装到当前
 * Aquafish 实例中的运行时主题。
 * </p>
 *
 * @param id 主题唯一标识
 * @param title 主题显示标题
 * @param version 主题版本
 * @param engine 模板引擎
 * @param author 作者信息
 * @param parent 父主题标识
 * @param description 主题说明
 * @param apiVersion 主题 API 版本
 * @param requirements 运行版本要求
 */
public record ThemeManifest(
    String id,
    String title,
    String version,
    String engine,
    ThemeAuthor author,
    String parent,
    String description,
    int apiVersion,
    ThemeRequirements requirements
) {

    /**
     * Aquafish 当前支持的主题模板引擎。
     */
    private static final Set<String>
        SUPPORTED_ENGINES = Set.of(
            "thymeleaf",
            "pebble"
        );

    /**
     * 主题唯一标识规则。
     */
    private static final Pattern
        THEME_ID_PATTERN = Pattern.compile(
            "^[a-z][a-z0-9-]{0,63}$"
        );

    /**
     * 标准化并验证清单。
     */
    public ThemeManifest {
        id = normalizeThemeId(
            id,
            "主题唯一标识"
        );

        title = normalizeText(
            title,
            id
        );

        version = normalizeText(
            version,
            "0.0.0"
        );

        engine = normalizeText(
                engine,
                "thymeleaf"
            )
            .toLowerCase(Locale.ROOT);

        if (!SUPPORTED_ENGINES.contains(engine)) {
            throw new IllegalArgumentException(
                "主题声明了不受支持的模板引擎："
                    + engine
                    + "。当前支持：thymeleaf、pebble。"
            );
        }

        author = author == null
            ? ThemeAuthor.empty()
            : author;

        parent = normalizeNullableThemeId(
            parent
        );

        /*
         * 此处只校验父主题标识本身的格式。
         *
         * parent 与当前主题 id 相同，
         * 或多个主题之间形成间接循环，
         * 都属于主题继承关系图校验。
         *
         * 这些规则统一交给 ThemeInheritanceResolver 处理，
         * 这样直接循环和间接循环能够使用相同异常类型，
         * 并输出完整的主题循环链。
         */

        description = normalizeText(
            description,
            ""
        );

        if (apiVersion <= 0) {
            throw new IllegalArgumentException(
                "主题 API 版本必须大于 0。"
            );
        }

        requirements = requirements == null
            ? ThemeRequirements.empty()
            : requirements;
    }

    /**
     * 判断是否声明父主题。
     *
     * @return 存在父主题时返回 true
     */
    public boolean hasParent() {
        return parent != null;
    }

    /**
     * 判断是否使用 Thymeleaf。
     *
     * @return 使用 Thymeleaf 时返回 true
     */
    public boolean isThymeleaf() {
        return usesEngine("thymeleaf");
    }

    /**
     * 判断是否使用 Pebble。
     *
     * @return 使用 Pebble 时返回 true
     */
    public boolean isPebble() {
        return usesEngine("pebble");
    }

    /**
     * 判断是否使用指定模板引擎。
     *
     * @param engineId 模板引擎标识
     * @return 一致时返回 true
     */
    public boolean usesEngine(
        String engineId
    ) {
        if (
            engineId == null
                || engineId.isBlank()
        ) {
            return false;
        }

        return engine.equals(
            engineId
                .trim()
                .toLowerCase(Locale.ROOT)
        );
    }

    /**
     * 判断模板引擎是否受支持。
     *
     * @param engineId 模板引擎标识
     * @return 支持时返回 true
     */
    public static boolean supportsEngine(
        String engineId
    ) {
        if (
            engineId == null
                || engineId.isBlank()
        ) {
            return false;
        }

        return SUPPORTED_ENGINES.contains(
            engineId
                .trim()
                .toLowerCase(Locale.ROOT)
        );
    }

    /**
     * 标准化必填主题标识。
     *
     * @param value 原始值
     * @param fieldName 字段名称
     * @return 标准化标识
     */
    private static String normalizeThemeId(
        String value,
        String fieldName
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                fieldName + "不能为空。"
            );
        }

        String normalized = value
            .trim()
            .toLowerCase(Locale.ROOT);

        if (
            !THEME_ID_PATTERN
                .matcher(normalized)
                .matches()
        ) {
            throw new IllegalArgumentException(
                "非法" + fieldName + "："
                    + normalized
            );
        }

        return normalized;
    }

    /**
     * 标准化可选父主题标识。
     *
     * @param value 原始父主题
     * @return 空值返回 null
     */
    private static String normalizeNullableThemeId(
        String value
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            return null;
        }

        return normalizeThemeId(
            value,
            "父主题标识"
        );
    }

    /**
     * 标准化普通文本。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return 非 null 文本
     */
    private static String normalizeText(
        String value,
        String defaultValue
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            return defaultValue;
        }

        return value.trim();
    }
}
