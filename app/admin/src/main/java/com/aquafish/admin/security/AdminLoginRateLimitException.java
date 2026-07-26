package com.aquafish.admin.security;

/**
 * 后台登录触发限流时使用的业务异常。
 *
 * <p><strong>功能：</strong>同时携带面向用户的固定提示和剩余等待秒数，避免 Controller
 * 通过解析异常文本推断重试时间。</p>
 *
 * <p><strong>关联：</strong>{@link AdminLoginRateLimiter} 负责判定并抛出本异常，后台认证
 * Controller 将 {@link #retryAfterSeconds()} 写入 {@code Retry-After} 响应头或错误数据。</p>
 */
public final class AdminLoginRateLimitException extends IllegalStateException {

    private final long retryAfterSeconds;

    /**
     * 创建一次限流结果。
     *
     * @param retryAfterSeconds 客户端至少需要等待的秒数
     */
    public AdminLoginRateLimitException(long retryAfterSeconds) {
        super("登录失败次数过多，请稍后再试。");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * 返回客户端可再次尝试登录前的等待秒数。
     */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
