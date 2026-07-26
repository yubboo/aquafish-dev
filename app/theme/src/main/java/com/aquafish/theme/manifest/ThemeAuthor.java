package com.aquafish.theme.manifest;

/**
 * Aquafish 主题作者信息。
 *
 * <p>
 * 该对象只保存 theme.yaml 中声明的可移植作者信息，
 * 不包含本地安装目录、授权状态或运行时数据。
 * </p>
 *
 * @param name 作者名称
 * @param url 作者主页，可为空
 */
public record ThemeAuthor(
    String name,
    String url
) {

    /**
     * 标准化作者字段。
     */
    public ThemeAuthor {
        name = normalizeText(name);
        url = normalizeText(url);
    }

    /**
     * 创建空作者信息。
     *
     * @return 空作者对象
     */
    public static ThemeAuthor empty() {
        return new ThemeAuthor(
            "",
            ""
        );
    }

    /**
     * 标准化允许为空的普通文本。
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
