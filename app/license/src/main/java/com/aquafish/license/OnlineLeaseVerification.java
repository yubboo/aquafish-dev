package com.aquafish.license;

/**
 * AQL1 验签结果。
 *
 * <p>调用方只能在 valid=true 时读取 payload 并参与授权判定；失败信息是给管理员看的
 * 固定描述，不包含公钥原文、响应正文或其他可能进入日志的敏感数据。</p>
 */
record OnlineLeaseVerification(
    boolean valid,
    OnlineLeasePayload payload,
    String message
) {
}
