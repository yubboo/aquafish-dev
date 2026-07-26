package com.aquafish.license;

import java.time.Instant;

/**
 * 返回后台的脱敏在线授权状态。
 *
 * <p>关联 {@link LicenseOnlineValidationService} 和系统授权页；不包含在线中心管理员
 * 令牌、完整请求地址或授权码，只展示是否启用、最近成功校验和离线宽限期。</p>
 */
public record LicenseOnlineStatusView(
    boolean enabled,
    String state,
    Instant lastCheckedAt,
    Instant graceExpiresAt,
    Instant nextRefreshAt,
    String message
) {
}
