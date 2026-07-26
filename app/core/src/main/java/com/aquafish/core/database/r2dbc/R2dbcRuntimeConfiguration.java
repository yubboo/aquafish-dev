package com.aquafish.core.database.r2dbc;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.binding.BindMarkers;
import org.springframework.r2dbc.core.binding.BindMarkersFactory;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Aquafish 响应式数据库核心配置。
 *
 * 在线业务统一注入：
 * 1. ConnectionFactory；
 * 2. DatabaseClient；
 * 3. ReactiveTransactionManager；
 * 4. TransactionalOperator。
 */
@Configuration(proxyBeanMethods = false)
public class R2dbcRuntimeConfiguration {

    /**
     * 全局运行时 ConnectionFactory。
     */
    @Bean(name = "connectionFactory")
    @Primary
    public RuntimeR2dbcConnectionFactory connectionFactory(
        DatabaseRuntimeSettingsService settingsService
    ) {
        return new RuntimeR2dbcConnectionFactory(
            settingsService
        );
    }

    /**
     * 响应式 SQL 客户端。
     *
     * 后续登录、用户、权限、内容等在线业务
     * 统一使用这个 DatabaseClient。
     */
    @Bean
    @Primary
    public DatabaseClient databaseClient(
        ConnectionFactory connectionFactory,
        RuntimeR2dbcConnectionFactory
            runtimeConnectionFactory
    ) {
        /*
         * RuntimeR2dbcConnectionFactory 可以动态切换数据库驱动，
         * Spring 无法通过自定义 Metadata 自动识别占位符类型。
         *
         * 因此显式提供动态 BindMarkersFactory：
         *
         * MySQL / MariaDB 使用 ?
         * PostgreSQL 使用 $1、$2、$3
         */
        BindMarkersFactory dynamicBindMarkers =
            new BindMarkersFactory() {

                @Override
                public BindMarkers create() {
                    String protocol =
                        runtimeConnectionFactory
                            .currentProtocol();

                    if (
                        "postgresql"
                            .equalsIgnoreCase(
                                protocol
                            )
                    ) {
                        return BindMarkersFactory
                            .indexed("$", 1)
                            .create();
                    }

                    return BindMarkersFactory
                        .anonymous("?")
                        .create();
                }

                /**
                 * 返回 false 可以安全支持相同命名参数
                 * 在一条 SQL 中出现多次。
                 */
                @Override
                public boolean
                    identifiablePlaceholders() {
                    return false;
                }
            };

        return DatabaseClient
            .builder()
            .connectionFactory(
                connectionFactory
            )
            .bindMarkers(
                dynamicBindMarkers
            )
            .build();
    }

    /**
     * R2DBC 响应式事务管理器。
     */
    @Bean
    @Primary
    public R2dbcTransactionManager r2dbcTransactionManager(
        ConnectionFactory connectionFactory
    ) {
        return new R2dbcTransactionManager(
            connectionFactory
        );
    }

    /**
     * 编程式响应式事务入口。
     *
     * 禁止在响应式业务中调用 block()。
     */
    @Bean
    @Primary
    public TransactionalOperator transactionalOperator(
        ReactiveTransactionManager transactionManager
    ) {
        return TransactionalOperator.create(
            transactionManager
        );
    }
}
