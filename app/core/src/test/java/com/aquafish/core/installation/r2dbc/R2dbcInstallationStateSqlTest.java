package com.aquafish.core.installation.r2dbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.database.DatabaseType;
import org.junit.jupiter.api.Test;

/**
 * 响应式安装状态 SQL 测试。
 */
class R2dbcInstallationStateSqlTest {

    private static final String TABLE =
        "aq_system_instances";

    @Test
    void shouldQuoteMysqlIdentifier() {
        assertEquals(
            "`aq_system_instances`",
            R2dbcInstallationStateSql
                .quoteIdentifier(
                    DatabaseType.MYSQL,
                    TABLE
                )
        );
    }

    @Test
    void shouldQuotePostgresqlIdentifier() {
        assertEquals(
            "\"aq_system_instances\"",
            R2dbcInstallationStateSql
                .quoteIdentifier(
                    DatabaseType.POSTGRESQL,
                    TABLE
                )
        );
    }

    @Test
    void shouldBuildUnlockedReadSql() {
        String sql =
            R2dbcInstallationStateSql
                .selectCurrent(
                    DatabaseType.MYSQL,
                    TABLE,
                    false
                );

        assertTrue(
            sql.contains(
                "WHERE singleton_id = :singletonId"
            )
        );

        assertFalse(
            sql.contains(
                "FOR UPDATE"
            )
        );
    }

    @Test
    void shouldBuildLockedReadSql() {
        String sql =
            R2dbcInstallationStateSql
                .selectCurrent(
                    DatabaseType.POSTGRESQL,
                    TABLE,
                    true
                );

        assertTrue(
            sql.endsWith(
                "FOR UPDATE"
            )
        );
    }

    @Test
    void shouldBuildInitializingInsertSql() {
        String sql =
            R2dbcInstallationStateSql
                .insertInitializing(
                    DatabaseType.MYSQL,
                    TABLE
                );

        assertTrue(
            sql.contains(
                "initialization_attempt_id"
            )
        );

        assertTrue(
            sql.contains(
                ":attemptId"
            )
        );

        assertTrue(
            sql.contains(
                ":stateVersion"
            )
        );
    }

    @Test
    void shouldProtectRestartWithExpectedVersionAndState() {
        String sql =
            R2dbcInstallationStateSql
                .updateToInitializing(
                    DatabaseType.MYSQL,
                    TABLE
                );

        assertTrue(
            sql.contains(
                "state_version = :expectedVersion"
            )
        );

        assertTrue(
            sql.contains(
                "installation_state = :expectedState"
            )
        );

        assertTrue(
            sql.contains(
                "last_error_code = NULL"
            )
        );
    }

    @Test
    void shouldProtectInstalledTransitionWithAttemptId() {
        String sql =
            R2dbcInstallationStateSql
                .updateToInstalled(
                    DatabaseType.POSTGRESQL,
                    TABLE
                );

        assertTrue(
            sql.contains(
                "initialization_attempt_id = :attemptId"
            )
        );

        assertTrue(
            sql.contains(
                "installed_version = :installedVersion"
            )
        );

        assertTrue(
            sql.contains(
                "state_version = :expectedVersion"
            )
        );
    }

    @Test
    void shouldProtectFailedTransitionWithAttemptId() {
        String sql =
            R2dbcInstallationStateSql
                .updateToFailed(
                    DatabaseType.MYSQL,
                    TABLE
                );

        assertTrue(
            sql.contains(
                "last_error_code = :errorCode"
            )
        );

        assertTrue(
            sql.contains(
                "last_error_message = :errorMessage"
            )
        );

        assertTrue(
            sql.contains(
                "initialization_attempt_id = :attemptId"
            )
        );

        assertTrue(
            sql.contains(
                "installed_at = NULL"
            )
        );

        assertTrue(
            sql.contains(
                "installed_version = NULL"
            )
        );
    }

    @Test
    void shouldRejectUnsafeIdentifier() {
        assertThrows(
            IllegalStateException.class,
            () ->
                R2dbcInstallationStateSql
                    .quoteIdentifier(
                        DatabaseType.MYSQL,
                        "aq_system_instances;drop"
                    )
        );
    }
}
