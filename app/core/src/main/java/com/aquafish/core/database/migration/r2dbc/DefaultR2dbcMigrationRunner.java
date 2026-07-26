package com.aquafish.core.database.migration.r2dbc;

import io.r2dbc.spi.ConnectionFactory;
import java.util.Objects;
import name.nkonev.r2dbc.migrate.core.R2dbcMigrate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * r2dbc-migrate 正式运行器。
 *
 * <p>
 * 整个迁移过程保持响应式：
 * </p>
 *
 * <ul>
 *     <li>不调用 block；</li>
 *     <li>不使用阻塞式数据库访问；</li>
 *     <li>不使用同步数据库连接管理器；</li>
 *     <li>不手动管理阻塞式数据库连接。</li>
 * </ul>
 */
@Component
public final class DefaultR2dbcMigrationRunner
    implements R2dbcMigrationRunner {

    @Override
    public Mono<Void> migrate(
        ConnectionFactory connectionFactory,
        R2dbcMigrationPlan plan
    ) {
        ConnectionFactory safeConnectionFactory =
            Objects.requireNonNull(
                connectionFactory,
                "R2DBC 连接工厂不能为空。"
            );

        R2dbcMigrationPlan safePlan =
            Objects.requireNonNull(
                plan,
                "R2DBC 迁移计划不能为空。"
            );

        /*
         * 后两个参数传 null：
         *
         * r2dbc-migrate 会根据 properties.dialect
         * 自动创建对应数据库的：
         *
         * 1. SqlQueries；
         * 2. Locker。
         *
         * 当前支持：
         * MySQL / MariaDB / PostgreSQL。
         */
        return R2dbcMigrate.migrate(
            safeConnectionFactory,
            safePlan.properties(),
            safePlan.resourceReader(),
            null,
            null
        );
    }
}
