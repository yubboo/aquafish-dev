package com.aquafish.theme.validation;

/**
 * Aquafish 主题压缩包校验问题代码。
 *
 * <p>
 * 后台接口和 Vue 管理页面应根据稳定代码判断问题类型，
 * 不应该依赖可能调整的中文错误文字。
 * </p>
 */
public enum ThemePackageIssueCode {

    /**
     * 校验请求没有提供主题包路径。
     */
    PACKAGE_PATH_MISSING,

    /**
     * 主题包文件不存在。
     */
    PACKAGE_NOT_FOUND,

    /**
     * 目标不是普通文件。
     */
    PACKAGE_NOT_REGULAR_FILE,

    /**
     * 文件扩展名不是 .zip。
     */
    PACKAGE_EXTENSION_INVALID,

    /**
     * 上传的压缩包本身超过允许大小。
     */
    PACKAGE_SIZE_EXCEEDED,

    /**
     * 无法打开或识别为有效 ZIP。
     */
    ARCHIVE_OPEN_FAILED,

    /**
     * ZIP 中没有任何有效文件。
     */
    ARCHIVE_EMPTY,

    /**
     * ZIP 条目数量超过限制。
     */
    ENTRY_COUNT_EXCEEDED,

    /**
     * ZIP 条目名称为空。
     */
    ENTRY_NAME_EMPTY,

    /**
     * ZIP 条目路径过长。
     */
    ENTRY_PATH_LENGTH_EXCEEDED,

    /**
     * ZIP 条目目录层级过深。
     */
    ENTRY_PATH_DEPTH_EXCEEDED,

    /**
     * ZIP 条目使用绝对路径。
     */
    ENTRY_ABSOLUTE_PATH,

    /**
     * ZIP 条目包含盘符路径。
     */
    ENTRY_DRIVE_PREFIX,

    /**
     * ZIP 条目包含路径穿越。
     */
    ENTRY_PATH_TRAVERSAL,

    /**
     * ZIP 中存在名称冲突的重复条目。
     */
    ENTRY_DUPLICATE,

    /**
     * 当前解压组件无法安全读取该条目。
     */
    ENTRY_UNREADABLE,

    /**
     * ZIP 中包含符号链接。
     */
    ENTRY_SYMBOLIC_LINK,

    /**
     * ZIP 中包含设备文件、套接字等特殊文件。
     */
    ENTRY_SPECIAL_FILE,

    /**
     * ZIP 条目使用加密方式。
     */
    ENTRY_ENCRYPTED,

    /**
     * 单个文件解压后超过大小限制。
     */
    SINGLE_FILE_SIZE_EXCEEDED,

    /**
     * 全部文件解压后的总大小超过限制。
     */
    TOTAL_UNCOMPRESSED_SIZE_EXCEEDED,

    /**
     * 压缩比异常，疑似压缩炸弹。
     */
    COMPRESSION_RATIO_EXCEEDED,

    /**
     * 包含主题不允许携带的危险文件类型。
     */
    DANGEROUS_FILE_TYPE,

    /**
     * 主题包中没有找到 theme.yaml。
     */
    MANIFEST_MISSING,

    /**
     * 主题包中存在多个可能的 theme.yaml。
     */
    MANIFEST_DUPLICATE,

    /**
     * theme.yaml 本身超过允许大小。
     */
    MANIFEST_SIZE_EXCEEDED,

    /**
     * theme.yaml 无法解析或字段不合法。
     */
    MANIFEST_INVALID,

    /**
     * 压缩包根目录结构不符合主题包规范。
     */
    ROOT_STRUCTURE_INVALID,

    /**
     * 无法计算主题包完整性哈希。
     */
    PACKAGE_HASH_FAILED,

    /**
     * 主题包中存在不会阻止安装但应提示的文件。
     */
    UNRECOMMENDED_FILE
}
