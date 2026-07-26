package com.aquafish.license;

/**
 * 授权码验签后的内部结果，不直接序列化给前端。
 */
record LicenseVerification(
    LicenseStatusCode status,
    LicensePayload payload,
    String message
) {

    boolean valid() {
        return status == LicenseStatusCode.VALID;
    }
}
