package com.aquafish.license;

/** 在线状态对本地授权的最终影响，仅供 license 模块内部使用。 */
record LicenseOnlineDecision(
    boolean usable,
    LicenseStatusCode deniedStatus,
    String message,
    LicenseOnlineStatusView view
) {
}
