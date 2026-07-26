package com.aquafish.core.database.r2dbc;

import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * 根据安装器选择的单一数据库配置创建 R2DBC 连接工厂。
 *
 * <p>该类不读取本机固定配置，也不会同时创建多套数据库连接。</p>
 */
public final class R2dbcConnectionFactoryBuilder {

    private R2dbcConnectionFactoryBuilder() {
    }

    public static ConnectionFactory create(DatabaseSettings source) {
        DatabaseSettings settings = source.normalized();

        ConnectionFactoryOptions.Builder builder = ConnectionFactoryOptions
            .builder()
            .option(ConnectionFactoryOptions.DRIVER, protocol(settings))
            .option(ConnectionFactoryOptions.HOST, settings.host())
            .option(ConnectionFactoryOptions.PORT, settings.port())
            .option(ConnectionFactoryOptions.USER, settings.username())
            .option(ConnectionFactoryOptions.PASSWORD, settings.password())
            .option(ConnectionFactoryOptions.DATABASE, settings.name());

        if (isLocalHost(settings.host())) {
            builder.option(ConnectionFactoryOptions.SSL, false);
        }

        return ConnectionFactories.get(builder.build());
    }

    public static String protocol(DatabaseSettings source) {
        DatabaseType type = source.normalized().type();

        return switch (type) {
            case MYSQL -> "mysql";
            case MARIADB -> "mariadb";
            case POSTGRESQL -> "postgresql";
        };
    }

    public static String displayUrl(DatabaseSettings source) {
        DatabaseSettings settings = source.normalized();

        return "r2dbc:" + protocol(settings) + "://"
            + settings.host() + ":" + settings.port() + "/" + settings.name();
    }

    private static boolean isLocalHost(String host) {
        if (host == null) {
            return false;
        }

        return "127.0.0.1".equals(host)
            || "localhost".equalsIgnoreCase(host)
            || "::1".equals(host)
            || "[::1]".equals(host)
            || "0:0:0:0:0:0:0:1".equals(host);
    }
}
