package com.aquafish.core.install;

/**
 * 托管数据库的安全展示摘要。
 *
 * <p>密码只返回“是否已配置”，绝不把平台注入的密码发送给浏览器。</p>
 */
public record SetupDatabaseSummary(
    String type,
    String host,
    int port,
    String name,
    String username,
    String tablePrefix,
    boolean passwordConfigured
) {
}
