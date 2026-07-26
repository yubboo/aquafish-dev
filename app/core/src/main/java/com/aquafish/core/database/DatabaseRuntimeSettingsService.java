package com.aquafish.core.database;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 运行时数据库配置读取服务。
 *
 * 当前阶段：
 * Step 17-22-4：数据库初始化表结构第一版。
 *
 * 作用：
 * 1. 从 Spring Environment 读取 aquafish.database 配置；
 * 2. 配置来源通常是 workdir/application.yaml；
 * 3. 给安装器数据库初始化使用；
 * 4. 后续业务模块也可以复用。
 */
@Service
public class DatabaseRuntimeSettingsService {

    private volatile DatabaseSettings installationOverride;

    private final String type;

    private final String host;

    private final Integer port;

    private final String name;

    private final String username;

    private final String password;

    private final String tablePrefix;

    public DatabaseRuntimeSettingsService(
        @Value("${aquafish.database.type:mysql}") String type,
        @Value("${aquafish.database.host:127.0.0.1}") String host,
        @Value("${aquafish.database.port:3306}") Integer port,
        @Value("${aquafish.database.name:aquafish}") String name,
        @Value("${aquafish.database.username:aquafish}") String username,
        @Value("${aquafish.database.password:}") String password,
        @Value("${aquafish.database.table-prefix:aq_}") String tablePrefix
    ) {
        this.type = type;
        this.host = host;
        this.port = port;
        this.name = name;
        this.username = username;
        this.password = password;
        this.tablePrefix = tablePrefix;
    }

    /**
     * 当前运行时数据库配置。
     */
    public DatabaseSettings current() {
        DatabaseSettings override = installationOverride;

        if (override != null) {
            return override;
        }

        return new DatabaseSettings(
            DatabaseType.fromValue(type),
            host,
            port,
            name,
            username,
            password,
            tablePrefix
        ).normalized();
    }

    /**
     * 安装向导写入 application.yaml 后，让当前进程立即采用同一份数据库配置。
     * 重启后该临时覆盖自然消失，配置重新由 Spring Environment 读取。
     */
    public void useForInstallation(DatabaseSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("安装数据库配置不能为空。");
        }

        installationOverride = settings.normalized();
    }
}
