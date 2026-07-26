package com.aquafish.theme.install;

/**
 * 主题安装流程阶段。
 *
 * <p>
 * 安装失败时记录准确阶段，
 * 方便后台、安全审计和日志系统判断故障发生位置。
 * </p>
 */
public enum ThemeInstallStage {

    /**
     * 对原始主题 ZIP 执行安全校验。
     */
    VALIDATION,

    /**
     * 创建临时安装工作目录并准备目标路径。
     */
    PREPARATION,

    /**
     * 把经过校验的 ZIP 内容受限解压到临时目录。
     */
    EXTRACTION,

    /**
     * 对解压后的真实目录再次执行检查。
     */
    POST_VALIDATION,

    /**
     * 把临时主题目录提交到 workdir/themes。
     */
    COMMIT,

    /**
     * 删除临时目录和安装残留。
     */
    CLEANUP,

    /**
     * 安装流程已经全部完成。
     */
    COMPLETE
}
