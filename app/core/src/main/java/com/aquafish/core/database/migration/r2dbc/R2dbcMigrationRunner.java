package com.aquafish.core.database.migration.r2dbc;

import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;

/**
 * Aquafish R2DBC 数据库迁移运行器。
 *
 * <p>
 * 该接口用于隔离第三方 r2dbc-migrate 的静态入口，
 * 让正式迁移服务可以进行稳定的单元测试。
 * </p>
 */
public interface R2dbcMigrationRunner {

    /**
     * 执行一份已经构造完成的响应式迁移计划。
     *
     * @param connectionFactory R2DBC 数据库连接工厂
     * @param plan 安全迁移计划
     * @return 迁移完成信号
     */
    Mono<Void> migrate(
        ConnectionFactory connectionFactory,
        R2dbcMigrationPlan plan
    );
}
