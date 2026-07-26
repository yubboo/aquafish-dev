package com.aquafish.license;

/**
 * 在线激活请求。activationCode 是授权中心生成的 AQO1 短码，不是 AQF1 离线授权码。
 */
public record OnlineLicenseActivationRequest(String activationCode) {
}
