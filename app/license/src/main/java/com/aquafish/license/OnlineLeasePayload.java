package com.aquafish.license;

import java.time.Instant;

/**
 * 授权中心签发的 AQL1 在线状态租约载荷。
 *
 * <p>它与永久/离线使用的 {@link LicensePayload AQF1 授权载荷}严格分开：AQL1 只证明
 * 某个授权和设备在 issuedAt 时的在线状态，并且最多有效到 validUntil；nonce 绑定
 * 本次 HTTP 请求，sequence 用于拒绝同一进程内倒退到更旧的数据库版本。</p>
 */
record OnlineLeasePayload(
    int schemaVersion,
    String type,
    String keyId,
    String licenseId,
    String instanceId,
    String status,
    String message,
    Instant issuedAt,
    Instant validUntil,
    long sequence,
    String nonce,
    int refreshAfterSeconds
) {
}
