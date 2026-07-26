package com.aquafish.core.database;

/**
 * R2DBC 查询的跨驱动分页 SQL 工具。
 *
 * <p>MySQL R2DBC 驱动对 {@code LIMIT :limit OFFSET :offset} 命名参数支持不稳定，
 * 某些版本会把该语句错误识别为批量执行并返回 bad SQL grammar。分页值在进入本工具
 * 前必须已经由业务层转换为整数，因此这里只把经过类型约束的数字追加到受控 SQL，
 * 其他筛选条件仍必须使用 R2DBC 参数绑定。</p>
 */
public final class R2dbcPaginationSql {

    private R2dbcPaginationSql() {
    }

    /**
     * 追加仅包含行数限制的分页片段。
     *
     * @param sql 不含分页片段的受控 SQL
     * @param limit 正整数行数限制
     * @return 追加 LIMIT 后的 SQL
     */
    public static String limit(String sql, long limit) {
        return requireSql(sql) + " limit " + requireLimit(limit);
    }

    /**
     * 追加行数限制与非负偏移。
     *
     * @param sql 不含分页片段的受控 SQL
     * @param limit 正整数行数限制
     * @param offset 非负记录偏移
     * @return 追加 LIMIT 与 OFFSET 后的 SQL
     */
    public static String limitOffset(String sql, long limit, long offset) {
        if (offset < 0L) {
            throw new IllegalArgumentException("分页偏移不能为负数");
        }
        return limit(sql, limit) + " offset " + offset;
    }

    private static long requireLimit(long limit) {
        if (limit < 1L) {
            throw new IllegalArgumentException("分页大小必须大于零");
        }
        return limit;
    }

    private static String requireSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("分页 SQL 不能为空");
        }
        return sql;
    }
}
