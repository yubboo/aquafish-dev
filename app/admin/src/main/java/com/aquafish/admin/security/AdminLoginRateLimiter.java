package com.aquafish.admin.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 单机部署默认登录限流器。
 *
 * <p>按直连地址和登录名组合限流，不依赖 Redis，适合默认简单部署。</p>
 */
@Component
public class AdminLoginRateLimiter {

    static final int MAX_FAILURES = 8;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
    static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public AdminLoginRateLimiter() {
        this(Clock.systemUTC());
    }

    AdminLoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * 在校验密码前检查“来源地址 + 登录名”是否处于封禁期。
     *
     * <p>超过失败阈值时抛出 {@link AdminLoginRateLimitException}，由
     * {@code AdminAuthController} 转换为 429 和 Retry-After 响应头。</p>
     */
    public void requireAllowed(String remoteAddress, String loginName) {
        String key = key(remoteAddress, loginName);
        AttemptState state = attempts.get(key);
        if (state == null) {
            return;
        }

        Instant now = clock.instant();
        synchronized (state) {
            if (state.blockedUntil != null && now.isBefore(state.blockedUntil)) {
                long seconds = Math.max(1, Duration.between(now, state.blockedUntil).toSeconds());
                throw new AdminLoginRateLimitException(seconds);
            }
            if (state.windowStarted == null
                || now.isAfter(state.windowStarted.plus(FAILURE_WINDOW))) {
                attempts.remove(key, state);
            }
        }
    }

    /**
     * 记录一次认证失败；十分钟窗口内累计八次后封禁该组合十五分钟。
     */
    public void recordFailure(String remoteAddress, String loginName) {
        String key = key(remoteAddress, loginName);
        Instant now = clock.instant();
        AttemptState state = attempts.computeIfAbsent(key, ignored -> new AttemptState());

        synchronized (state) {
            if (state.windowStarted == null
                || now.isAfter(state.windowStarted.plus(FAILURE_WINDOW))) {
                state.windowStarted = now;
                state.failures = 0;
                state.blockedUntil = null;
            }
            state.failures++;
            if (state.failures >= MAX_FAILURES) {
                state.blockedUntil = now.plus(BLOCK_DURATION);
            }
        }
    }

    /**
     * 登录成功后清除对应失败记录，避免历史失败影响正常用户。
     */
    public void recordSuccess(String remoteAddress, String loginName) {
        attempts.remove(key(remoteAddress, loginName));
    }

    /**
     * 生成限流桶键；同时绑定网络来源和账号可减少单一维度的误伤。
     */
    private String key(String remoteAddress, String loginName) {
        return normalize(remoteAddress, "unknown") + "|" + normalize(loginName, "unknown");
    }

    /**
     * 统一大小写及空值，保证同一来源不会产生多个等价限流桶。
     */
    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class AttemptState {
        private Instant windowStarted;
        private int failures;
        private Instant blockedUntil;
    }
}
