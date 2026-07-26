package com.aquafish.core.database.r2dbc;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import io.r2dbc.pool.PoolingConnectionFactoryProvider;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import io.r2dbc.spi.ValidationDepth;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.DisposableBean;

/**
 * Aquafish 运行时 R2DBC ConnectionFactory。
 *
 * 核心目标：
 * 1. 在应用启动时不立即连接数据库；
 * 2. 第一次真正执行响应式 SQL 时再读取安装配置；
 * 3. 根据 MYSQL、MARIADB、POSTGRESQL 自动选择驱动；
 * 4. 使用 r2dbc-pool 统一提供连接池；
 * 5. 数据库配置改变后自动重建底层连接工厂。
 *
 * 这样即使系统尚未安装，也不会因为缺少数据库配置而阻断安装页面。
 */
public final class RuntimeR2dbcConnectionFactory
    implements ConnectionFactory, DisposableBean {

    private static final Option<Integer> INITIAL_SIZE =
        Option.valueOf("initialSize");

    private static final Option<Integer> MAX_SIZE =
        Option.valueOf("maxSize");

    private static final Option<Duration> MAX_IDLE_TIME =
        Option.valueOf("maxIdleTime");

    private static final Option<Duration> MAX_ACQUIRE_TIME =
        Option.valueOf("maxAcquireTime");

    private final DatabaseRuntimeSettingsService settingsService;

    /**
     * 当前已缓存的真实 ConnectionFactory。
     */
    private volatile CachedFactory cachedFactory;

    public RuntimeR2dbcConnectionFactory(
        DatabaseRuntimeSettingsService settingsService
    ) {
        this.settingsService = settingsService;
    }

    /**
     * 每次连接时解析当前运行配置。
     *
     * 未修改数据库配置时直接复用连接池；
     * 配置发生变化时重新创建连接池。
     */
    @Override
    public Publisher<? extends Connection> create() {
        return currentFactory().create();
    }

    /**
     * 返回当前真实数据库驱动对应的标准元数据名称。
     *
     * Spring Data R2DBC 会根据这个名称识别数据库方言：
     *
     * MySQL      -> MySqlDialect
     * MariaDB    -> MySqlDialect
     * PostgreSQL -> PostgresDialect
     *
     * 不能返回自定义名称，否则 Spring Data 无法识别方言。
     */
    @Override
    public ConnectionFactoryMetadata getMetadata() {
        String protocol =
            currentProtocol();

        final String metadataName;

        if (
            "postgresql".equalsIgnoreCase(
                protocol
            )
        ) {
            metadataName = "PostgreSQL";
        } else if (
            "mariadb".equalsIgnoreCase(
                protocol
            )
        ) {
            metadataName = "MariaDB";
        } else if (
            "mysql".equalsIgnoreCase(
                protocol
            )
        ) {
            metadataName = "MySQL";
        } else {
            throw new IllegalStateException(
                "无法识别 R2DBC 数据库方言："
                    + protocol
            );
        }

        return () -> metadataName;
    }

    /**
     * 返回当前数据库对应的 R2DBC 协议。
     */
    public String currentProtocol() {
        return resolveProtocol(
            requireSettings()
        );
    }

    /**
     * 手动清除已缓存连接池。
     *
     * 后续安装完成或后台修改数据库配置后，
     * 可以调用此方法强制重新读取配置。
     */
    public synchronized void refresh() {
        CachedFactory previous =
            cachedFactory;

        cachedFactory = null;

        if (previous != null) {
            closeFactory(
                previous.connectionFactory()
            );
        }
    }

    /**
     * 获取当前真实 ConnectionFactory。
     */
    private ConnectionFactory currentFactory() {
        DatabaseSettings settings =
            requireSettings();

        String fingerprint =
            fingerprint(settings);

        CachedFactory snapshot =
            cachedFactory;

        if (
            snapshot != null &&
                snapshot.fingerprint()
                    .equals(fingerprint)
        ) {
            return snapshot.connectionFactory();
        }

        synchronized (this) {
            snapshot = cachedFactory;

            if (
                snapshot != null &&
                    snapshot.fingerprint()
                        .equals(fingerprint)
            ) {
                return snapshot.connectionFactory();
            }

            ConnectionFactory created =
                createFactory(settings);

            CachedFactory previous =
                cachedFactory;

            cachedFactory =
                new CachedFactory(
                    fingerprint,
                    created
                );

            if (previous != null) {
                closeFactory(
                    previous.connectionFactory()
                );
            }

            return created;
        }
    }

    /**
     * 创建带连接池的真实 R2DBC ConnectionFactory。
     */
    private ConnectionFactory createFactory(
        DatabaseSettings settings
    ) {
        String protocol =
            resolveProtocol(settings);

        String host = requireText(
            settings.host(),
            "数据库主机"
        );

        String databaseName = requireText(
            settings.name(),
            "数据库名称"
        );

        String username = requireText(
            settings.username(),
            "数据库用户名"
        );

        int port = settings.port();

        if (port <= 0 || port > 65535) {
            throw new IllegalStateException(
                "数据库端口不正确：" + port
            );
        }

        ConnectionFactoryOptions.Builder optionsBuilder =
            ConnectionFactoryOptions
                .builder()
                /*
                 * DRIVER=pool 表示最外层使用连接池。
                 */
                .option(
                    ConnectionFactoryOptions.DRIVER,
                    "pool"
                )
                /*
                 * PROTOCOL 表示连接池内部使用的真实驱动。
                 */
                .option(
                    ConnectionFactoryOptions.PROTOCOL,
                    protocol
                )
                .option(
                    ConnectionFactoryOptions.HOST,
                    host
                )
                .option(
                    ConnectionFactoryOptions.PORT,
                    port
                )
                .option(
                    ConnectionFactoryOptions.USER,
                    username
                )
                .option(
                    ConnectionFactoryOptions.PASSWORD,
                    settings.password() == null
                        ? ""
                        : settings.password()
                )
                .option(
                    ConnectionFactoryOptions.DATABASE,
                    databaseName
                )
                /*
                 * 连接池基础配置。
                 *
                 * initialSize=0：
                 * 启动时不立即连接，兼容未安装状态。
                 *
                 * maxSize=20：
                 * 当前开发阶段的默认上限，
                 * 后续再开放到后台系统设置。
                 */
                .option(
                    INITIAL_SIZE,
                    0
                )
                .option(
                    MAX_SIZE,
                    20
                )
                .option(
                    MAX_IDLE_TIME,
                    Duration.ofSeconds(60)
                )
                .option(
                    MAX_ACQUIRE_TIME,
                    Duration.ofSeconds(10)
                )
                /*
                 * 连接池必须在借出连接前做远程校验。
                 *
                 * 不能只依赖本地连接对象状态：MySQL、MariaDB、代理或防火墙
                 * 都可能已经关闭空闲 TCP 连接，而客户端对象暂时仍认为可用。
                 * 远程 SELECT 1 能让连接池丢弃失效连接并自动新建连接，避免
                 * 第一次后台请求把短暂的陈旧连接误判成 DATABASE_UNAVAILABLE。
                 */
                .option(
                    PoolingConnectionFactoryProvider
                        .VALIDATION_DEPTH,
                    ValidationDepth.REMOTE
                )
                .option(
                    PoolingConnectionFactoryProvider
                        .VALIDATION_QUERY,
                    "SELECT 1"
                )
                .option(
                    PoolingConnectionFactoryProvider
                        .MAX_VALIDATION_TIME,
                    Duration.ofSeconds(5)
                )
                /*
                 * 主动清理空闲连接，避免连接长期超过数据库 wait_timeout。
                 * 即使服务器或代理把超时设得更短，借出前远程校验仍是兜底。
                 */
                .option(
                    PoolingConnectionFactoryProvider
                        .BACKGROUND_EVICTION_INTERVAL,
                    Duration.ofSeconds(30)
                );

        /*
         * SSL 策略：
         *
         * 1. 127.0.0.1、localhost、::1 默认关闭数据库 SSL；
         * 2. 远程数据库不强制覆盖，继续采用驱动默认配置；
         * 3. 可以通过系统属性或环境变量显式覆盖。
         *
         * 系统属性：
         * -Daquafish.r2dbc.ssl-enabled=true
         *
         * 环境变量：
         * AQUAFISH_R2DBC_SSL_ENABLED=true
         */
        Boolean sslEnabled =
            resolveSslEnabled(host);

        if (sslEnabled != null) {
            optionsBuilder.option(
                ConnectionFactoryOptions.SSL,
                sslEnabled
            );
        }

        return ConnectionFactories.get(
            optionsBuilder.build()
        );
    }

    /**
     * 解析数据库 SSL 开关。
     *
     * 返回值说明：
     * true  = 明确启用 SSL；
     * false = 明确禁用 SSL；
     * null  = 不覆盖驱动默认值。
     */
    private Boolean resolveSslEnabled(
        String host
    ) {
        String configured =
            firstNonBlank(
                System.getProperty(
                    "aquafish.r2dbc.ssl-enabled"
                ),
                System.getenv(
                    "AQUAFISH_R2DBC_SSL_ENABLED"
                )
            );

        if (configured != null) {
            if (
                "true".equalsIgnoreCase(
                    configured
                )
            ) {
                return Boolean.TRUE;
            }

            if (
                "false".equalsIgnoreCase(
                    configured
                )
            ) {
                return Boolean.FALSE;
            }

            throw new IllegalStateException(
                "R2DBC SSL 配置只能是 true 或 false：" +
                    configured
            );
        }

        /*
         * 本机环回连接不需要 TLS。
         *
         * 这也可以避免旧版 MySQL/OpenSSL
         * 与新版 Java、Netty TLS 协议协商失败。
         */
        if (isLocalDatabaseHost(host)) {
            return Boolean.FALSE;
        }

        /*
         * 远程数据库不覆盖驱动默认设置。
         *
         * 后续安装器会增加正式的数据库 SSL 配置项。
         */
        return null;
    }

    private boolean isLocalDatabaseHost(
        String host
    ) {
        if (
            host == null ||
                host.isBlank()
        ) {
            return false;
        }

        String normalized =
            host.trim();

        return "127.0.0.1".equals(
            normalized
        ) ||
            "localhost".equalsIgnoreCase(
                normalized
            ) ||
            "::1".equals(normalized) ||
            "[::1]".equals(normalized) ||
            "0:0:0:0:0:0:0:1".equals(
                normalized
            );
    }

    private String firstNonBlank(
        String first,
        String second
    ) {
        if (
            first != null &&
                !first.isBlank()
        ) {
            return first.trim();
        }

        if (
            second != null &&
                !second.isBlank()
        ) {
            return second.trim();
        }

        return null;
    }

    /**
     * 根据安装器保存的数据库类型选择对应协议。
     */
    private String resolveProtocol(
        DatabaseSettings settings
    ) {
        String type = String.valueOf(
            settings.type()
        )
            .trim()
            .toUpperCase(Locale.ROOT);

        /*
         * 必须先判断 MariaDB。
         * 避免未来某些类型文本同时包含 mysql 时匹配错误。
         */
        if (type.contains("MARIADB")) {
            return "mariadb";
        }

        if (type.contains("POSTGRES")) {
            return "postgresql";
        }

        if (type.contains("MYSQL")) {
            return "mysql";
        }

        throw new IllegalStateException(
            "当前数据库类型暂不支持 R2DBC："
                + type
        );
    }

    private DatabaseSettings requireSettings() {
        DatabaseSettings settings =
            settingsService.current();

        if (settings == null) {
            throw new IllegalStateException(
                "尚未找到数据库运行配置。"
            );
        }

        return settings;
    }

    private String requireText(
        String value,
        String fieldName
    ) {
        if (
            value == null ||
                value.isBlank()
        ) {
            throw new IllegalStateException(
                fieldName + "不能为空。"
            );
        }

        return value.trim();
    }

    /**
     * 配置指纹只用于判断连接池是否需要重建。
     *
     * 不会对外输出数据库密码。
     */
    private String fingerprint(
        DatabaseSettings settings
    ) {
        return Integer.toHexString(
            Objects.hash(
                String.valueOf(
                    settings.type()
                ),
                settings.host(),
                settings.port(),
                settings.name(),
                settings.username(),
                settings.password()
            )
        );
    }

    /**
     * 通过反射关闭连接池。
     *
     * 这里不强耦合具体连接池实现，
     * 以后替换连接池时不需要修改接口定义。
     */
    private void closeFactory(
        ConnectionFactory factory
    ) {
        if (factory == null) {
            return;
        }

        invokeNoArgumentMethod(
            factory,
            "dispose"
        );

        invokeNoArgumentMethod(
            factory,
            "close"
        );
    }

    private void invokeNoArgumentMethod(
        Object target,
        String methodName
    ) {
        try {
            Method method = target
                .getClass()
                .getMethod(methodName);

            method.invoke(target);
        } catch (
            NoSuchMethodException ignored
        ) {
            /*
             * 当前实现没有该关闭方法时直接忽略。
             */
        } catch (Exception error) {
            /*
             * 应用关闭过程中不能因为连接池关闭失败
             * 再次阻断 Spring 容器关闭。
             */
        }
    }

    @Override
    public void destroy() {
        refresh();
    }

    private record CachedFactory(
        String fingerprint,
        ConnectionFactory connectionFactory
    ) {
    }
}
