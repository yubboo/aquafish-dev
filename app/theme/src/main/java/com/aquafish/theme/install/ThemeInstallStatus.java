package com.aquafish.theme.install;

/**
 * 主题安装最终状态。
 */
public enum ThemeInstallStatus {

    /**
     * 主题已经成功提交到正式主题目录。
     */
    INSTALLED,

    /**
     * 因主题包、安装策略或已有主题冲突而被正常拒绝。
     *
     * <p>
     * REJECTED 表示系统按规则阻止了操作，
     * 不代表服务器发生了非预期故障。
     * </p>
     */
    REJECTED,

    /**
     * 安装流程中发生文件系统或其他运行时故障。
     */
    FAILED
}
