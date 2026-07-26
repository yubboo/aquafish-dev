package com.aquafish.core.user;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * UID 迁移契约测试。
 *
 * <p>这里验证双数据库迁移都包含独立 uid、唯一约束和分配锁，避免只改某一种
 * 数据库后在另一种部署模式中运行失败。</p>
 */
class UserUidAllocatorSqlContractTest {

    @Test
    void mysqlAndPostgresqlMigrationsDeclareReusableUidContract() throws Exception {
        Path appRoot = Path.of("..").toAbsolutePath().normalize();
        String mysql = read(appRoot.resolve(
            "user/src/main/resources/db/migration/user/mysql/V19__user_reusable_uid.sql"
        ));
        String postgresql = read(appRoot.resolve(
            "user/src/main/resources/db/migration/user/postgresql/V19__user_reusable_uid.sql"
        ));

        assertContract(mysql);
        assertContract(postgresql);
    }

    private void assertContract(String sql) {
        assertTrue(sql.contains("uid"));
        assertTrue(sql.contains("user_uid_allocator"));
        assertTrue(sql.toLowerCase().contains("unique"));
        assertTrue(sql.contains("${tablePrefix}"));
        assertTrue(!sql.contains("aq_users"));
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
