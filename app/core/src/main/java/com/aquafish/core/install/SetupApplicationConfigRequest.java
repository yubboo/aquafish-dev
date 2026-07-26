package com.aquafish.core.install;

import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.redis.RedisSettings;

/**
 * 安装阶段 application.yaml 写入请求。
 *
 * 当前阶段：
 * Step 17-22-3：安装配置写入 workdir/application.yaml。
 *
 * 当前只负责：
 * 1. server.port；
 * 2. aquafish.work-dir；
 * 3. aquafish.external-url；
 * 4. aquafish.site；
 * 5. aquafish.database；
 * 6. aquafish.theme；
 * 7. aquafish.redis（可选）；
 * 8. aquafish.install.locked。
 *
 * 注意：
 * 这一步不初始化数据库表。
 * 这一步不创建管理员。
 * 这一步不写 install.lock。
 */
public record SetupApplicationConfigRequest(
    Integer serverPort,
    DatabaseSettings database,
    RedisSettings redis,
    SiteSettings site,
    String activeTheme
) {

    public SetupApplicationConfigRequest normalized() {
        SiteSettings safeSite = site == null
            ? SiteSettings.defaultSettings()
            : site.normalized();

        DatabaseSettings safeDatabase = database == null
            ? DatabaseSettings.defaultMysql()
            : database.normalized();

        RedisSettings safeRedis = redis == null
            ? RedisSettings.disabled()
            : redis.normalized();

        return new SetupApplicationConfigRequest(
            serverPort == null || serverPort <= 0 ? 8080 : serverPort,
            safeDatabase,
            safeRedis,
            safeSite,
            textOrDefault(activeTheme, "default")
        );
    }

    private static String textOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value.trim();
    }
}
