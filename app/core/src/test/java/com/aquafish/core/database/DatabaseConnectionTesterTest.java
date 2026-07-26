package com.aquafish.core.database;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 数据库连接测试耗时回归测试。
 *
 * <p>使用非法表前缀触发连接前校验失败，不连接真实数据库，
 * 用于确认所有结果分支都会返回 elapsedMillis。</p>
 */
class DatabaseConnectionTesterTest {

    @Test
    void shouldReturnElapsedMillisWhenValidationFails() {
        DatabaseConnectionTestResult result =
            new DatabaseConnectionTester()
                .test(
                    new DatabaseSettings(
                        DatabaseType.MYSQL,
                        "127.0.0.1",
                        3306,
                        "aquafish",
                        "aquafish",
                        "",
                        "INVALID-"
                    )
                )
                .block(
                    Duration.ofSeconds(2)
                );

        assertNotNull(result);
        assertFalse(result.connected());
        assertTrue(
            result.elapsedMillis() >= 1L
        );
    }
}
