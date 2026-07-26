package com.aquafish.user.security;

/**
 * 前台会员登录超过单机失败阈值。
 */
public class MemberLoginRateLimitException extends IllegalStateException {

    private final long retryAfterSeconds;

    public MemberLoginRateLimitException(long retryAfterSeconds) {
        super("登录尝试过于频繁，请稍后重试。");
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
