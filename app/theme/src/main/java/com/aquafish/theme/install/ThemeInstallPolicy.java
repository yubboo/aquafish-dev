package com.aquafish.theme.install;

/**
 * 主题安装策略。
 *
 * <p>
 * 第一版安装服务永远拒绝覆盖已有主题，
 * 因此本策略不会提供 allowOverwrite 等危险开关。
 * 主题升级和覆盖将在后续独立服务中实现。
 * </p>
 *
 * @param rejectPackageWarnings 是否把主题包警告视为拒绝原因
 * @param requireAtomicMove 是否强制要求文件系统支持原子移动
 */
public record ThemeInstallPolicy(
    boolean rejectPackageWarnings,
    boolean requireAtomicMove
) {

    /**
     * 默认主题安装策略。
     *
     * <ul>
     *     <li>校验警告允许安装，但必须返回给后台；</li>
     *     <li>优先尝试原子移动；</li>
     *     <li>文件系统不支持原子移动时允许安全降级；</li>
     *     <li>始终拒绝覆盖已安装主题。</li>
     * </ul>
     *
     * @return 默认安装策略
     */
    public static ThemeInstallPolicy defaults() {
        return new ThemeInstallPolicy(
            false,
            false
        );
    }

    /**
     * 严格安装策略。
     *
     * <ul>
     *     <li>任何主题包警告都会拒绝安装；</li>
     *     <li>必须使用原子移动完成正式提交。</li>
     * </ul>
     *
     * @return 严格安装策略
     */
    public static ThemeInstallPolicy strict() {
        return new ThemeInstallPolicy(
            true,
            true
        );
    }
}
