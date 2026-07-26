package com.aquafish.core.database.migration.r2dbc;

import com.aquafish.core.database.AquafishDatabaseTableCatalog;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Aquafish 正式 R2DBC 数据库迁移状态读取器。
 *
 * <p>所有数据库访问均通过 DatabaseClient 和 R2DBC 完成。</p>
 */
@Component
public final class DefaultR2dbcMigrationStateReader
    implements R2dbcMigrationStateReader {

    @Override
    public Mono<R2dbcMigrationDatabaseSnapshot> read(
        ConnectionFactory connectionFactory,
        DatabaseSettings settings,
        R2dbcMigrationTableNames tableNames
    ) {
        return Mono.defer(
            () -> {
                ConnectionFactory safeConnectionFactory =
                    Objects.requireNonNull(
                        connectionFactory,
                        "R2DBC 连接工厂不能为空。"
                    );

                DatabaseSettings safeSettings =
                    Objects.requireNonNull(
                        settings,
                        "数据库配置不能为空。"
                    ).normalized();

                R2dbcMigrationTableNames safeNames =
                    Objects.requireNonNull(
                        tableNames,
                        "迁移相关表名不能为空。"
                    );

                DatabaseClient client =
                    DatabaseClient.create(
                        safeConnectionFactory
                    );

                Set<String> aquafishTables =
                    Set.copyOf(
                        AquafishDatabaseTableCatalog
                            .physicalTableNames(
                                safeSettings
                            )
                    );

                Mono<Long> totalTables =
                    countTables(
                        client,
                        safeSettings.type(),
                        aquafishTables
                    );

                Mono<Boolean> migrationsExists =
                    tableExists(
                        client,
                        safeSettings.type(),
                        safeNames.migrationsTable()
                    );

                Mono<Boolean> lockExists =
                    tableExists(
                        client,
                        safeSettings.type(),
                        safeNames.migrationsLockTable()
                    );

                return Mono
                    .zip(
                        totalTables,
                        migrationsExists,
                        lockExists
                    )
                    .flatMap(
                        result -> {
                            long tableCount =
                                result.getT1();

                            boolean historyExists =
                                result.getT2();

                            boolean migrationLockExists =
                                result.getT3();

                            Mono<List<Long>>
                                appliedVersions =
                                historyExists
                                    ? readAppliedVersions(
                                        client,
                                        safeSettings.type(),
                                        safeNames
                                            .migrationsTable()
                                    ).collectList()
                                    : Mono.just(
                                        List.of()
                                    );

                            return appliedVersions.map(
                                versions ->
                                    new R2dbcMigrationDatabaseSnapshot(
                                        tableCount,
                                        historyExists,
                                        migrationLockExists,
                                        versions
                                    )
                            );
                        }
                    );
            }
        ).onErrorMap(
            error ->
                error instanceof IllegalStateException
                    ? error
                    : new IllegalStateException(
                        "读取 R2DBC 数据库迁移状态失败："
                            + rootMessage(error),
                        error
                    )
        );
    }

    /**
     * 只统计当前前缀下、正式白名单内的 Aquafish 业务表。
     *
     * <p>其他程序表和其他前缀不会影响安装判断。</p>
     */
    private Mono<Long> countTables(
        DatabaseClient client,
        DatabaseType databaseType,
        Set<String> aquafishTables
    ) {
        return client
            .sql(
                tableNamesSql(
                    databaseType
                )
            )
            .map(
                (row, metadata) -> {
                    Object value =
                        row.get(
                            "table_name"
                        );

                    return value == null
                        ? ""
                        : value.toString();
                }
            )
            .all()
            .filter(
                aquafishTables::contains
            )
            .count();
    }

    private Mono<Boolean> tableExists(
        DatabaseClient client,
        DatabaseType databaseType,
        String tableName
    ) {
        return client
            .sql(
                tableExistsSql(
                    databaseType
                )
            )
            .bind(
                "tableName",
                tableName
            )
            .map(
                (row, metadata) ->
                    number(
                        row,
                        "match_count"
                    ) > 0
            )
            .one()
            .defaultIfEmpty(false);
    }

    private Flux<Long> readAppliedVersions(
        DatabaseClient client,
        DatabaseType databaseType,
        String migrationsTable
    ) {
        String sql =
            "SELECT id FROM "
                + quoteIdentifier(
                    databaseType,
                    migrationsTable
                )
                + " ORDER BY id";

        return client
            .sql(sql)
            .map(
                (row, metadata) ->
                    number(
                        row,
                        "id"
                    )
            )
            .all();
    }

    /**
     * 查询当前 Schema 的基础表名，
     * 随后与正式白名单精确比对。
     */
    static String tableNamesSql(
        DatabaseType databaseType
    ) {
        return switch (
            Objects.requireNonNull(
                databaseType,
                "数据库类型不能为空。"
            )
        ) {
            case MYSQL, MARIADB ->
                "SELECT table_name "
                    + "FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_type = 'BASE TABLE'";

            case POSTGRESQL ->
                "SELECT table_name "
                    + "FROM information_schema.tables "
                    + "WHERE table_schema = current_schema() "
                    + "AND table_type = 'BASE TABLE'";
        };
    }

    static String tableExistsSql(
        DatabaseType databaseType
    ) {
        return switch (
            Objects.requireNonNull(
                databaseType,
                "数据库类型不能为空。"
            )
        ) {
            case MYSQL, MARIADB ->
                "SELECT COUNT(*) AS match_count "
                    + "FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_type = 'BASE TABLE' "
                    + "AND table_name = :tableName";

            case POSTGRESQL ->
                "SELECT COUNT(*) AS match_count "
                    + "FROM information_schema.tables "
                    + "WHERE table_schema = current_schema() "
                    + "AND table_type = 'BASE TABLE' "
                    + "AND table_name = :tableName";
        };
    }

    static String quoteIdentifier(
        DatabaseType databaseType,
        String identifier
    ) {
        String safeIdentifier =
            requireIdentifier(
                identifier
            );

        return switch (
            Objects.requireNonNull(
                databaseType,
                "数据库类型不能为空。"
            )
        ) {
            case MYSQL, MARIADB ->
                "`" + safeIdentifier + "`";

            case POSTGRESQL ->
                "\"" + safeIdentifier + "\"";
        };
    }

    private static String requireIdentifier(
        String value
    ) {
        if (
            value == null
            || value.isBlank()
            || value.length() > 64
        ) {
            throw new IllegalStateException(
                "数据库表名非法。"
            );
        }

        for (
            int index = 0;
            index < value.length();
            index++
        ) {
            char current =
                value.charAt(index);

            if (
                current != '_'
                && !Character.isLetterOrDigit(
                    current
                )
            ) {
                throw new IllegalStateException(
                    "数据库表名包含非法字符。"
                );
            }
        }

        return value;
    }

    private long number(
        Row row,
        String columnName
    ) {
        Object value =
            row.get(columnName);

        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value == null) {
            return 0L;
        }

        throw new IllegalStateException(
            "数据库字段不是数字："
                + columnName
        );
    }

    private static String rootMessage(
        Throwable error
    ) {
        Throwable current =
            error;

        while (
            current.getCause() != null
            && current.getCause() != current
        ) {
            current =
                current.getCause();
        }

        String message =
            current.getMessage();

        return message == null
            || message.isBlank()
                ? current
                    .getClass()
                    .getSimpleName()
                : message;
    }
}
