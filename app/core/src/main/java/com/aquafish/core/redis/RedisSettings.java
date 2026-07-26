package com.aquafish.core.redis;

/**
 * 首次安装阶段的可选 Redis 配置。
 *
 * <p>Redis 当前作为可选缓存/会话基础设施；未启用时不阻断单机安装，
 * 启用后安装器会先检测连接再写入 application.yaml。</p>
 */
public record RedisSettings(
    boolean enabled,
    String host,
    Integer port,
    Integer database,
    String username,
    String password,
    boolean ssl
) {

    public RedisSettings normalized() {
        return new RedisSettings(
            enabled,
            textOrDefault(host, "127.0.0.1"),
            port == null || port <= 0 || port > 65535 ? 6379 : port,
            database == null || database < 0 ? 0 : database,
            textOrEmpty(username),
            textOrEmpty(password),
            ssl
        );
    }

    public String validateMessage() {
        RedisSettings safe = normalized();
        if (!safe.enabled()) {
            return null;
        }
        if (safe.host().isBlank()) {
            return "Redis 主机不能为空。";
        }
        if (safe.database() > 15) {
            return "Redis 数据库编号必须在 0-15 之间。";
        }
        return null;
    }

    public static RedisSettings disabled() {
        return new RedisSettings(false, "127.0.0.1", 6379, 0, "", "", false);
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String textOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
