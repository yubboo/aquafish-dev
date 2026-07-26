package com.aquafish.core.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * 跨模块 R2DBC 分页 SQL 回归测试。
 */
class R2dbcPaginationSqlTest {

    @Test
    void shouldAppendNumericLimitAndOffset() {
        assertEquals(
            "select id from aq_ip_bans order by id desc limit 20 offset 40",
            R2dbcPaginationSql.limitOffset(
                "select id from aq_ip_bans order by id desc",
                20,
                40
            )
        );
        assertEquals(
            "select id from aq_roles order by id limit 200",
            R2dbcPaginationSql.limit(
                "select id from aq_roles order by id",
                200
            )
        );
    }

    @Test
    void shouldRejectInvalidInputs() {
        assertThrows(
            IllegalArgumentException.class,
            () -> R2dbcPaginationSql.limit("", 20)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> R2dbcPaginationSql.limit("select 1", 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> R2dbcPaginationSql.limitOffset("select 1", 20, -1)
        );
    }
}
