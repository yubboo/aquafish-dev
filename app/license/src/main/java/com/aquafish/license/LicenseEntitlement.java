package com.aquafish.license;

import java.util.Locale;

/**
 * 具体官方资源授权，例如 theme/official-default 或 plugin/official-seo。
 *
 * <p>模块 feature 解决“能否使用主题管理”；entitlement 解决“能否使用某个官方收费
 * 主题”。调用方必须先通过平台和模块授权，再校验本对象，从而形成双授权。</p>
 */
public record LicenseEntitlement(String type, String id) {

    public boolean matches(String expectedType, String expectedId) {
        return normalize(type).equals(normalize(expectedType))
            && normalize(id).equals(normalize(expectedId));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
