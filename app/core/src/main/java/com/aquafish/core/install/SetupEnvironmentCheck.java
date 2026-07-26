package com.aquafish.core.install;

/**
 * 可公开给首次安装页面的单项环境检查结果。
 *
 * <p>这里只返回安全摘要，不返回服务器绝对路径、环境变量内容或数据库密码。</p>
 */
public record SetupEnvironmentCheck(
    String key,
    String label,
    boolean passed,
    boolean required,
    String detail
) {
}
