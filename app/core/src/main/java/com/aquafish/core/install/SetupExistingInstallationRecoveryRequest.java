package com.aquafish.core.install;

import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.redis.RedisSettings;

/**
 * 已安装 Aquafish 恢复到当前电脑的请求。
 *
 * <p>该请求不包含管理员密码，不会创建账号或执行数据库迁移。</p>
 */
public record SetupExistingInstallationRecoveryRequest(
    Integer serverPort,
    DatabaseSettings database,
    RedisSettings redis
) {

    /**
     * 规范化恢复请求。
     */
    public SetupExistingInstallationRecoveryRequest normalized() {
        return new SetupExistingInstallationRecoveryRequest(
            serverPort == null
                || serverPort <= 0
                    ? 8520
                    : serverPort,
            database == null
                ? null
                : database.normalized(),
            redis == null
                ? RedisSettings.disabled()
                : redis.normalized()
        );
    }
}
