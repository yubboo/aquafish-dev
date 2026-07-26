package com.aquafish.theme.manifest;

/**
 * Aquafish 主题运行兼容要求。
 *
 * <p>对应 theme.yaml：</p>
 *
 * <pre>
 * requires:
 *   aquafish: ">=1.0.0"
 *   java: ">=21"
 * </pre>
 *
 * <p>
 * 当前阶段只负责保存声明。
 * 正式版本范围比较将在主题包校验阶段实现。
 * </p>
 *
 * @param aquafish Aquafish 版本要求
 * @param java Java 版本要求
 */
public record ThemeRequirements(
    String aquafish,
    String java
) {

    /**
     * 标准化兼容要求。
     */
    public ThemeRequirements {
        aquafish = normalizeText(aquafish);
        java = normalizeText(java);
    }

    /**
     * 创建没有额外版本要求的对象。
     *
     * @return 空兼容要求
     */
    public static ThemeRequirements empty() {
        return new ThemeRequirements(
            "",
            ""
        );
    }

    /**
     * 判断是否声明 Aquafish 版本要求。
     *
     * @return 存在要求时返回 true
     */
    public boolean hasAquafishRequirement() {
        return !aquafish.isBlank();
    }

    /**
     * 判断是否声明 Java 版本要求。
     *
     * @return 存在要求时返回 true
     */
    public boolean hasJavaRequirement() {
        return !java.isBlank();
    }

    /**
     * 标准化允许为空的字段。
     *
     * @param value 原始值
     * @return 非 null 文本
     */
    private static String normalizeText(
        String value
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            return "";
        }

        return value.trim();
    }
}
