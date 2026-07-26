package com.aquafish.core.database.migration.r2dbc;

import com.aquafish.core.database.DatabaseSettings;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;

/**
 * Aquafish R2DBC 数据库迁移状态读取接口。
 */
public interface R2dbcMigrationStateReader {

    /**
     * 从数据库中读取迁移表和版本状态。
     */
    Mono<R2dbcMigrationDatabaseSnapshot> read(
        ConnectionFactory connectionFactory,
        DatabaseSettings settings,
        R2dbcMigrationTableNames tableNames
    );
}
