package com.aquafish.core.install;

/**
 * 安装最终提交请求。
 */
public record SetupFinishRequest(
    SetupAdminAccountRequest admin,
    SiteSettings site
) {

    public SetupFinishRequest normalized() {
        SetupAdminAccountRequest safeAdmin =
            admin == null
                ? new SetupAdminAccountRequest(
                    "",
                    "",
                    "",
                    ""
                )
                : admin.normalized();

        SiteSettings safeSite =
            site == null
                ? SiteSettings.defaultSettings()
                : site.normalized();

        return new SetupFinishRequest(
            safeAdmin,
            safeSite
        );
    }
}
