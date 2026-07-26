package com.aquafish.theme.validation;

/**
 * 单个主题包校验问题。
 *
 * @param severity 严重级别
 * @param code 稳定问题代码
 * @param entryName 相关 ZIP 条目名称，可为空
 * @param message 面向管理员的中文说明
 */
public record ThemePackageIssue(
    ThemePackageIssueSeverity severity,
    ThemePackageIssueCode code,
    String entryName,
    String message
) {

    /**
     * 标准化并验证问题对象。
     */
    public ThemePackageIssue {
        if (severity == null) {
            throw new IllegalArgumentException(
                "主题包问题严重级别不能为空。"
            );
        }

        if (code == null) {
            throw new IllegalArgumentException(
                "主题包问题代码不能为空。"
            );
        }

        entryName = normalizeOptionalText(
            entryName
        );

        if (
            message == null
                || message.isBlank()
        ) {
            throw new IllegalArgumentException(
                "主题包问题说明不能为空。"
            );
        }

        message = message.trim();
    }

    /**
     * 创建错误问题。
     *
     * @param code 问题代码
     * @param entryName 条目名称
     * @param message 错误说明
     * @return 错误问题
     */
    public static ThemePackageIssue error(
        ThemePackageIssueCode code,
        String entryName,
        String message
    ) {
        return new ThemePackageIssue(
            ThemePackageIssueSeverity.ERROR,
            code,
            entryName,
            message
        );
    }

    /**
     * 创建警告问题。
     *
     * @param code 问题代码
     * @param entryName 条目名称
     * @param message 警告说明
     * @return 警告问题
     */
    public static ThemePackageIssue warning(
        ThemePackageIssueCode code,
        String entryName,
        String message
    ) {
        return new ThemePackageIssue(
            ThemePackageIssueSeverity.WARNING,
            code,
            entryName,
            message
        );
    }

    /**
     * 判断当前问题是否为错误。
     *
     * @return ERROR 时返回 true
     */
    public boolean isError() {
        return severity
            == ThemePackageIssueSeverity.ERROR;
    }

    /**
     * 判断当前问题是否为警告。
     *
     * @return WARNING 时返回 true
     */
    public boolean isWarning() {
        return severity
            == ThemePackageIssueSeverity.WARNING;
    }

    /**
     * 标准化允许为空的文本。
     *
     * @param value 原始值
     * @return 空值转换为空字符串
     */
    private static String normalizeOptionalText(
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
