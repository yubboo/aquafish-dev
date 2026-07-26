package com.aquafish.user.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 默认单实例前台登录限流器。
 *
 * <p>按可信代理解析后的客户端地址和登录名组合限制密码猜测。
 * 多实例部署后应迁移到 Redis
 * 或网关分布式限流，但数据库账号状态和会话校验仍是最终安全边界。</p>
 */
@Component
public class MemberLoginRateLimiter {

    static final int MAX_FAILURES = 8;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
    static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public MemberLoginRateLimiter() {
        this(Clock.systemUTC());
    }

    MemberLoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void requireAllowed(String address, String loginName) {
        String key = key(address, loginName);
        AttemptState state = attempts.get(key);
        if (state == null) {
            return;
        }

        Instant now = clock.instant();
        synchronized (state) {
            if (state.blockedUntil != null && now.isBefore(state.blockedUntil)) {
                throw new MemberLoginRateLimitException(
                    Duration.between(now, state.blockedUntil).toSeconds()
                );
            }
            if (state.windowStarted == null
                || now.isAfter(state.windowStarted.plus(FAILURE_WINDOW))) {
                attempts.remove(key, state);
            }
        }
    }

    public void recordFailure(String address, String loginName) {
        String key = key(address, loginName);
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

    public void recordSuccess(String address, String loginName) {
        attempts.remove(key(address, loginName));
    }

    private String key(String address, String loginName) {
        return normalize(address) + "|" + normalize(loginName);
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
            ? "unknown"
            : value.strip().toLowerCase(Locale.ROOT);
    }

    private static final class AttemptState {
        private Instant windowStarted;
        private int failures;
        private Instant blockedUntil;
    }
}
