package com.aquafish.license;

/**
 * 在线授权缓存文件结构。
 *
 * <p>只保存授权中心返回的完整 AQL1 签名租约。状态、时间、设备码和刷新周期都必须在
 * 每次读取时重新验签后从 lease 中取得，不能再相信可由本机直接编辑的普通 JSON 字段。
 * schemaVersion=2 会明确拒绝旧版明文状态缓存。</p>
 */
record LicenseOnlineSnapshot(
    int schemaVersion,
    String lease
) {
}
