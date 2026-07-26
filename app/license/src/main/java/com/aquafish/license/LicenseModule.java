
package com.aquafish.license;

/**
 * 授权码、免费版/专业版/商业版能力控制。
 */
public final class LicenseModule {

    private LicenseModule() {
    }

    /** @return 用于模块注册、日志和诊断输出的稳定模块标识 */
    public static String name() {
        return "license";
    }
}
