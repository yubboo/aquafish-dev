package com.aquafish.theme.install;

/**
 * 主题安装稳定错误代码。
 *
 * <p>
 * 后台接口和前端应使用错误代码判断问题类型，
 * 不应依赖可能调整的中文错误文字。
 * </p>
 */
public enum ThemeInstallErrorCode {

    /**
     * ThemePackageValidator 没有通过。
     */
    PACKAGE_VALIDATION_FAILED,

    /**
     * 安装策略要求拒绝带警告的主题包。
     */
    PACKAGE_WARNING_REJECTED,

    /**
     * 校验结果中没有可用 ThemeManifest。
     */
    MANIFEST_UNAVAILABLE,

    /**
     * 主题包在校验完成后发生了变化。
     *
     * <p>
     * 后续安装服务会通过 SHA-256 二次确认，
     * 防止校验对象和实际安装对象不一致。
     * </p>
     */
    PACKAGE_CHANGED_AFTER_VALIDATION,

    /**
     * manifest.id 生成的目标路径不安全。
     */
    TARGET_PATH_INVALID,

    /**
     * 相同主题 ID 已经安装。
     */
    THEME_ALREADY_INSTALLED,

    /**
     * 子主题声明的父主题尚未安装。
     */
    PARENT_THEME_NOT_INSTALLED,

    /**
     * 子主题与父主题使用了不同模板引擎。
     */
    PARENT_THEME_ENGINE_MISMATCH,

    /**
     * 创建主题安装临时目录失败。
     */
    TEMP_DIRECTORY_CREATE_FAILED,

    /**
     * 无法打开经过校验的主题 ZIP。
     */
    ARCHIVE_OPEN_FAILED,

    /**
     * 受限解压主题 ZIP 失败。
     */
    ARCHIVE_EXTRACTION_FAILED,

    /**
     * 解压后的文件数量、大小或路径与校验结果不一致。
     */
    EXTRACTED_CONTENT_INVALID,

    /**
     * 解压后的 theme.yaml 与原始校验清单不一致。
     */
    EXTRACTED_MANIFEST_MISMATCH,

    /**
     * 创建正式主题父目录失败。
     */
    TARGET_DIRECTORY_CREATE_FAILED,

    /**
     * 当前文件系统不支持原子移动，
     * 但安装策略强制要求原子提交。
     */
    ATOMIC_MOVE_REQUIRED_BUT_UNAVAILABLE,

    /**
     * 把临时主题移动到正式主题目录失败。
     */
    INSTALL_MOVE_FAILED,

    /**
     * 主题提交后无法通过 ThemeScanner 二次确认。
     */
    POST_INSTALL_SCAN_FAILED,

    /**
     * 同一时间已经有其他冲突主题操作运行。
     */
    THEME_OPERATION_BUSY,

    /**
     * 安装失败后无法完整删除临时目录或残留。
     */
    CLEANUP_FAILED,

    /**
     * 安装流程发生未分类的非预期错误。
     */
    UNEXPECTED_INSTALL_ERROR
}
