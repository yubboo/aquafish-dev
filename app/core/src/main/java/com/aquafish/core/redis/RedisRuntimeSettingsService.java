package com.aquafish.core.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 从 Spring Environment 读取 Redis 运行参数。
 *
 * <p>1Panel/Docker 托管模式通过 AQUAFISH_REDIS_* 注入；分发安装模式在写入
 * application.yaml 后使用安装期覆盖，让当前进程无需重启即可完成检测和初始化。</p>
 */
@Service
public class RedisRuntimeSettingsService {

    private final RedisSettings configured;
    private volatile RedisSettings installationOverride;

    public RedisRuntimeSettingsService(
        @Value("${aquafish.redis.enabled:false}") boolean enabled,
        @Value("${aquafish.redis.host:127.0.0.1}") String host,
        @Value("${aquafish.redis.port:6379}") Integer port,
        @Value("${aquafish.redis.database:0}") Integer database,
        @Value("${aquafish.redis.username:}") String username,
        @Value("${aquafish.redis.password:}") String password,
        @Value("${aquafish.redis.ssl:false}") boolean ssl
    ) {
        this.configured = new RedisSettings(
            enabled,
            host,
            port,
            database,
            username,
            password,
            ssl
        ).normalized();
    }

    public RedisSettings current() {
        RedisSettings override = installationOverride;
        return override == null ? configured : override;
    }

    public void useForInstallation(RedisSettings settings) {
        installationOverride = settings == null
            ? RedisSettings.disabled()
            : settings.normalized();
    }
}
