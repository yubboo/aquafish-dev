package com.aquafish.license;

/**
 * 后台提交授权码时使用的请求对象。
 *
 * @param licenseCode 从 Aquafish 授权端获取的完整授权码
 */
public record LicenseActivationRequest(String licenseCode) {
}
