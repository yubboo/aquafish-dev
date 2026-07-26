package com.aquafish.admin.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdminLoginRateLimiterTest {

    @Test
    void shouldBlockAfterRepeatedFailuresAndClearAfterSuccess() {
        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter();

        for (int index = 0; index < AdminLoginRateLimiter.MAX_FAILURES; index++) {
            limiter.recordFailure("127.0.0.1", "admin");
        }

        AdminLoginRateLimitException error = assertThrows(
            AdminLoginRateLimitException.class,
            () -> limiter.requireAllowed("127.0.0.1", "ADMIN")
        );
        assertTrue(error.retryAfterSeconds() > 0);

        limiter.recordSuccess("127.0.0.1", "admin");
        assertDoesNotThrow(() -> limiter.requireAllowed("127.0.0.1", "admin"));
    }

    @Test
    void shouldIsolateDifferentAddressAndLoginNamePairs() {
        AdminLoginRateLimiter limiter = new AdminLoginRateLimiter();
        for (int index = 0; index < AdminLoginRateLimiter.MAX_FAILURES; index++) {
            limiter.recordFailure("10.0.0.1", "admin");
        }

        assertDoesNotThrow(() -> limiter.requireAllowed("10.0.0.2", "admin"));
        assertDoesNotThrow(() -> limiter.requireAllowed("10.0.0.1", "another"));
    }
}
