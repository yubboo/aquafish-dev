package com.aquafish.core.install.r2dbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;

/**
 * 管理员 R2DBC 仓库的结构和方言约束测试。
 */
class R2dbcSetupAdminAccountStoreTest {

    @Test
    void repositoryShouldBeProxyableSpringBean() {
        assertTrue(
            R2dbcSetupAdminAccountStore.class
                .isAnnotationPresent(
                    Repository.class
                )
        );
        assertFalse(
            Modifier.isFinal(
                R2dbcSetupAdminAccountStore.class
                    .getModifiers()
            )
        );
    }

    @Test
    void tableNamesShouldUseValidatedConfiguredPrefix() {
        DatabaseSettings settings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "aquafish",
                "",
                "site01_"
            ).normalized();

        R2dbcSetupAdminAccountStore.TableNames tables =
            R2dbcSetupAdminAccountStore
                .TableNames.from(settings);

        assertEquals(
            "site01_users",
            tables.users()
        );
        assertEquals(
            "site01_system_instances",
            tables.systemInstances()
        );
        assertEquals(
            "site01_user_uid_allocator",
            tables.userUidAllocator()
        );
        assertEquals(8, tables.required().size());
    }

    @Test
    void creationShouldLockSingletonInstallationRow() {
        R2dbcSetupAdminAccountStore.TableNames tables =
            R2dbcSetupAdminAccountStore
                .TableNames.from(
                    DatabaseSettings.defaultMysql()
                );

        String sql =
            R2dbcSetupAdminAccountStore
                .creationLockSql(tables)
                .toLowerCase();

        assertTrue(
            sql.contains("aq_system_instances")
        );
        assertTrue(sql.contains("for update"));
        assertTrue(sql.contains(":singletonid"));
    }

    @Test
    void optionUpsertShouldBindValuesForBothDialects() {
        String mysql =
            R2dbcSetupAdminAccountStore
                .optionUpsertSql(
                    DatabaseType.MYSQL,
                    "aq_options"
                );
        String postgresql =
            R2dbcSetupAdminAccountStore
                .optionUpsertSql(
                    DatabaseType.POSTGRESQL,
                    "aq_options"
                );

        assertTrue(mysql.contains(":optionKey"));
        assertTrue(mysql.contains(":optionValue"));
        assertTrue(
            mysql.contains("on duplicate key update")
        );
        assertTrue(postgresql.contains(":optionKey"));
        assertTrue(postgresql.contains(":optionValue"));
        assertTrue(
            postgresql.contains("on conflict (option_key)")
        );
    }

    @Test
    void finishShouldLockSingletonAndUseOptimisticAttemptGuard() {
        R2dbcSetupAdminAccountStore.TableNames tables =
            R2dbcSetupAdminAccountStore.TableNames.from(
                DatabaseSettings.defaultMysql()
            );

        for (DatabaseType type : DatabaseType.values()) {
            String lockSql =
                R2dbcSetupAdminAccountStore.finishLockSql(
                    type,
                    tables
                ).toLowerCase();
            String updateSql =
                R2dbcSetupAdminAccountStore.finishInstalledSql(
                    type,
                    tables
                ).toLowerCase();

            assertTrue(lockSql.contains("for update"));
            assertTrue(lockSql.contains("aq_system_instances"));
            assertTrue(updateSql.contains("state_version = :newversion"));
            assertTrue(updateSql.contains("state_version = :expectedversion"));
            assertTrue(updateSql.contains("initialization_attempt_id = :attemptid"));
        }
    }
}
