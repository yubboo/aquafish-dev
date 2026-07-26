package com.aquafish.theme.validation;

/**
 * 主题包校验问题严重级别。
 */
public enum ThemePackageIssueSeverity {

    /**
     * 错误。
     *
     * 只要校验结果中存在一个 ERROR，
     * 主题包就不允许进入安装阶段。
     */
    ERROR,

    /**
     * 警告。
     *
     * 警告不会直接阻止安装，
     * 但后台必须向管理员明确展示。
     */
    WARNING
}
