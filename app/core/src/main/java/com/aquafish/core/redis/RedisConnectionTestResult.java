package com.aquafish.core.redis;

/**
 * Redis 安装期连接检测结果。
 *
 * <p>只包含公开诊断信息，不回显用户名、密码或完整连接串。</p>
 */
public record RedisConnectionTestResult(
    boolean connected,
    boolean skipped,
    long elapsedMillis,
    String message
) {
}
