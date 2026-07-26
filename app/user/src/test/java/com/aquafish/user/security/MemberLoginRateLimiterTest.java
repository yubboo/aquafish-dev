package com.aquafish.user.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * 前台会员登录失败限流测试。
 */
class MemberLoginRateLimiterTest {

    @Test
    void shouldBlockSameAddressAndLoginAfterFailureThreshold() {
        MemberLoginRateLimiter limiter = new MemberLoginRateLimiter(
            Clock.fixed(Instant.parse("2026-07-18T02:00:00Z"), ZoneOffset.UTC)
        );

        for (int index = 0; index < MemberLoginRateLimiter.MAX_FAILURES; index++) {
            limiter.recordFailure("127.0.0.1", "member");
        }

        assertThrows(
            MemberLoginRateLimitException.class,
            () -> limiter.requireAllowed("127.0.0.1", "member")
        );
        assertDoesNotThrow(
            () -> limiter.requireAllowed("127.0.0.2", "member")
        );
    }

    @Test
    void successfulLoginShouldClearFailureState() {
        MemberLoginRateLimiter limiter = new MemberLoginRateLimiter();
        for (int index = 0; index < MemberLoginRateLimiter.MAX_FAILURES; index++) {
            limiter.recordFailure("127.0.0.1", "member");
        }

        limiter.recordSuccess("127.0.0.1", "member");

        assertDoesNotThrow(
            () -> limiter.requireAllowed("127.0.0.1", "member")
        );
    }
}
