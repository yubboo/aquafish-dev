package com.aquafish.boot.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.r2dbc.R2dbcConnectionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

/**
 * 本地真实数据库 R2DBC 矩阵。
 *
 * <p>不启动 Docker。只有显式提供对应环境变量时才连接该测试数据库，
 * 测试只执行 SELECT 1，不修改业务数据。</p>
 */
class LocalR2dbcDatabaseMatrixTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "AQUAFISH_TEST_MYSQL_HOST", matches = ".+")
    void shouldConnectToMysql() {
        verify(DatabaseType.MYSQL, "MYSQL", 3306);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AQUAFISH_TEST_MARIADB_HOST", matches = ".+")
    void shouldConnectToMariadb() {
        verify(DatabaseType.MARIADB, "MARIADB", 3306);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AQUAFISH_TEST_POSTGRESQL_HOST", matches = ".+")
    void shouldConnectToPostgresql() {
        verify(DatabaseType.POSTGRESQL, "POSTGRESQL", 5432);
    }

    private void verify(DatabaseType type, String prefix, int defaultPort) {
        DatabaseSettings settings = new DatabaseSettings(
            type,
            required(prefix + "_HOST"),
            integer(prefix + "_PORT", defaultPort),
            required(prefix + "_DATABASE"),
            required(prefix + "_USERNAME"),
            environment(prefix + "_PASSWORD", ""),
            "aq_"
        );

        DatabaseClient client = DatabaseClient.create(
            R2dbcConnectionFactoryBuilder.create(settings)
        );

        StepVerifier.create(
                client.sql("select 1 as validation_value")
                    .map((row, metadata) ->
                        ((Number) row.get("validation_value")).intValue()
                    )
                    .one()
            )
            .assertNext(value -> assertEquals(1, value))
            .verifyComplete();
    }

    private String required(String suffix) {
        String value = System.getenv("AQUAFISH_TEST_" + suffix);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少测试环境变量：AQUAFISH_TEST_" + suffix);
        }
        return value;
    }

    private int integer(String suffix, int fallback) {
        String value = System.getenv("AQUAFISH_TEST_" + suffix);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private String environment(String suffix, String fallback) {
        String value = System.getenv("AQUAFISH_TEST_" + suffix);
        return value == null ? fallback : value;
    }
}
