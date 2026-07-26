package com.aquafish.license;

/**
 * Aquafish 系统平台授权状态。
 *
 * <p>这个枚举同时提供给授权校验服务、API 拦截器和后台页面使用，保证三处不会
 * 分别发明不同的状态字符串。</p>
 */
public enum LicenseStatusCode {
    NOT_ACTIVATED,
    VALID,
    EXPIRED,
    NOT_YET_VALID,
    INSTANCE_MISMATCH,
    PRODUCT_MISMATCH,
    /** 授权中心临时暂停；通常用于风控、欠费或人工复核，恢复后可继续使用。 */
    SUSPENDED,
    REVOKED,
    DEVICE_UNBOUND,
    ONLINE_CHECK_REQUIRED,
    INVALID,
    CONFIGURATION_ERROR
}
