package com.aquafish.core.install;

/**
 * Aquafish 站点初始化配置。
 *
 * 当前阶段：
 * Step 17-22-3：安装配置写入 workdir/application.yaml。
 *
 * 作用：
 * 1. 安装向导填写站点名称；
 * 2. 安装向导填写站点访问地址；
 * 3. 安装向导填写默认语言；
 * 4. 安装向导填写默认时区。
 */
public record SiteSettings(
    String name,
    String url,
    String locale,
    String timezone
) {

    public static SiteSettings defaultSettings() {
        return new SiteSettings(
            "Aquafish",
            "http://127.0.0.1:8080",
            "zh-CN",
            "Asia/Shanghai"
        );
    }

    public SiteSettings normalized() {
        return new SiteSettings(
            textOrDefault(name, "Aquafish"),
            textOrDefault(url, "http://127.0.0.1:8080"),
            textOrDefault(locale, "zh-CN"),
            textOrDefault(timezone, "Asia/Shanghai")
        );
    }

    private static String textOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value.trim();
    }
}
