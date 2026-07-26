package com.aquafish.license;

import java.time.Instant;
import java.util.List;

/**
 * 返回给后台的脱敏授权状态。
 *
 * <p>注意：这里故意不返回原始授权码，避免浏览器、日志或其他管理员接口再次泄露
 * 可以被复制使用的凭据。</p>
 */
public record LicenseStatusView(
    LicenseStatusCode status,
    boolean valid,
    boolean usable,
    boolean enforcementEnabled,
    String instanceId,
    String licenseId,
    String edition,
    String customer,
    Instant issuedAt,
    Instant expiresAt,
    List<String> features,
    List<LicenseEntitlement> entitlements,
    LicenseOnlineStatusView online,
    /**
     * 可公开给管理员点击的客户授权中心地址。它来自服务端白名单配置，不包含设备码、
     * 管理员令牌或订单信息；设备码仍由用户明确复制后在授权中心提交。
     */
    String portalUrl,
    String message
) {

    public LicenseStatusView {
        features = features == null ? List.of() : List.copyOf(features);
        entitlements = entitlements == null ? List.of() : List.copyOf(entitlements);
    }
}
