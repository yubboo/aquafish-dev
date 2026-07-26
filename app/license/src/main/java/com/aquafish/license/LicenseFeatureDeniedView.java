package com.aquafish.license;

import java.util.List;

/**
 * 模块授权不足时返回给后台的安全信息。
 *
 * <p>关联 {@link LicenseEnforcementWebFilter} 和前端模块授权不足页面。这里只返回
 * 缺少的模块、当前版本和已授权功能列表，不返回原始授权码或签名内容。</p>
 */
public record LicenseFeatureDeniedView(
    String requiredFeature,
    String requiredFeatureLabel,
    String edition,
    List<String> licensedFeatures
) {

    public LicenseFeatureDeniedView {
        licensedFeatures = licensedFeatures == null ? List.of() : List.copyOf(licensedFeatures);
    }
}
