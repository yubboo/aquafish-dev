package com.aquafish.user.database.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * MySQL V2 Flyway 迁移脚本静态安全测试。
 *
 * <p>该测试不会连接真实 MySQL，也不会执行 Flyway migrate。</p>
 *
 * <p>测试目标：</p>
 *
 * <ol>
 *     <li>确认 V2 SQL 已进入运行时 classpath；</li>
 *     <li>确认所有数据表使用动态表前缀；</li>
 *     <li>确认用户资料、资料字段和资料审核结构完整；</li>
 *     <li>确认用户统计表使用 user_statistics；</li>
 *     <li>确认积分余额、流水和管理员调整职责分离；</li>
 *     <li>确认旧数据库兼容升级逻辑存在；</li>
 *     <li>确认不会覆盖已有用户积分和统计数据；</li>
 *     <li>确认不存在删除表、清空表或删除字段等危险语句；</li>
 *     <li>确认没有使用 MySQL 8.0.12 不支持的语法；</li>
 *     <li>确认没有手工创建或写入 Flyway 历史表。</li>
 * </ol>
 */
class FlywayMysqlV2MigrationTest {

    /**
     * V2 迁移脚本在 classpath 中的固定位置。
     */
    private static final String RESOURCE_PATH =
        "db/migration/user/mysql/"
            + "V2__user_profile_statistics_points.sql";

    /**
     * 验证 V2 SQL 文件存在并且内容不为空。
     */
    @Test
    void shouldLoadMysqlV2MigrationFromClasspath()
        throws Exception {
        String sql =
            loadSql();

        assertFalse(
            sql.isBlank(),
            "MySQL V2 迁移脚本不能为空。"
        );

        assertTrue(
            sql.contains("V2"),
            "V2 迁移脚本应保留版本说明。"
        );
    }

    /**
     * 验证全部业务表使用动态表前缀。
     */
    @Test
    void shouldUseDynamicTablePrefix()
        throws Exception {
        String sql =
            normalizedSql();

        assertTrue(
            sql.contains(
                "`${tableprefix}user_profiles`"
            )
        );

        assertTrue(
            sql.contains(
                "`${tableprefix}user_profile_fields`"
            )
        );

        assertTrue(
            sql.contains(
                "`${tableprefix}user_profile_audits`"
            )
        );

        assertTrue(
            sql.contains(
                "`${tableprefix}user_statistics`"
            )
        );

        assertTrue(
            sql.contains(
                "`${tableprefix}points_rules`"
            )
        );

        assertTrue(
            sql.contains(
                "`${tableprefix}points_logs`"
            )
        );

        assertTrue(
            sql.contains(
                "`${tableprefix}points_adjustments`"
            )
        );

        assertFalse(
            normalizedExecutableSql()
                .contains("`aq_"),
            "V2 SQL 不能写死 aq_ 表前缀。"
        );
    }

    /**
     * 验证用户资料体系所需的三张表完整存在。
     */
    @Test
    void shouldContainUserProfileInfrastructure()
        throws Exception {
        String sql =
            normalizedSql();

        assertTrue(
            sql.contains(
                "create table if not exists "
                    + "`${tableprefix}user_profiles`"
            )
        );

        assertTrue(
            sql.contains(
                "create table if not exists "
                    + "`${tableprefix}user_profile_fields`"
            )
        );

        assertTrue(
            sql.contains(
                "create table if not exists "
                    + "`${tableprefix}user_profile_audits`"
            )
        );

        assertTrue(
            sql.contains("`profile_completed`")
        );

        assertTrue(
            sql.contains("`audit_status`")
        );

        assertTrue(
            sql.contains("`audit_required`")
        );

        assertTrue(
            sql.contains("`old_value`")
        );

        assertTrue(
            sql.contains("`new_value`")
        );

        assertTrue(
            sql.contains(
                "uk_user_profiles_user_id"
            )
        );

        assertTrue(
            sql.contains(
                "uk_user_profile_fields_field_key"
            )
        );
    }

    /**
     * 验证只使用正式确定的 user_statistics 表。
     *
     * <p>禁止重新引入已经废弃的 user_stats 命名。</p>
     */
    @Test
    void shouldUseCanonicalUserStatisticsTable()
        throws Exception {
        String sql =
            normalizedSql();

        assertTrue(
            sql.contains(
                "create table if not exists "
                    + "`${tableprefix}user_statistics`"
            )
        );

        assertTrue(
            sql.contains(
                "uk_user_statistics_user_id"
            )
        );

        assertTrue(
            sql.contains(
                "`points` bigint not null default 0"
            )
        );

        assertTrue(
            sql.contains(
                "`last_active_at`"
            )
        );

        assertFalse(
            sql.contains(
                "`${tableprefix}user_stats`"
            ),
            "正式数据库结构不能创建 user_stats 表。"
        );
    }

    /**
     * 验证积分体系职责分离。
     */
    @Test
    void shouldSeparatePointsSnapshotLedgerAndAudit()
        throws Exception {
        String sql =
            normalizedSql();

        /*
         * user_statistics.points：
         * 当前积分余额快照。
         */
        assertTrue(
            sql.contains(
                "`points` bigint not null default 0"
            )
        );

        /*
         * points_logs：
         * 每一次积分变化流水。
         */
        assertTrue(
            sql.contains(
                "create table if not exists "
                    + "`${tableprefix}points_logs`"
            )
        );

        assertTrue(
            sql.contains("`points_delta`")
        );

        assertTrue(
            sql.contains("`balance_after`")
        );

        assertTrue(
            sql.contains("`source_type`")
        );

        assertTrue(
            sql.contains("`source_id`")
        );

        /*
         * points_adjustments：
         * 管理员手工奖惩审计。
         */
        assertTrue(
            sql.contains(
                "create table if not exists "
                    + "`${tableprefix}points_adjustments`"
            )
        );

        assertTrue(
            sql.contains("`operator_id`")
        );

        assertTrue(
            sql.contains("`reason`")
        );
    }

    /**
     * 验证积分规则表存在并具有唯一规则标识。
     */
    @Test
    void shouldContainPointsRuleInfrastructure()
        throws Exception {
        String sql =
            normalizedSql();

        assertTrue(
            sql.contains(
                "create table if not exists "
                    + "`${tableprefix}points_rules`"
            )
        );

        assertTrue(
            sql.contains("`rule_key`")
        );

        assertTrue(
            sql.contains("`scene`")
        );

        assertTrue(
            sql.contains("`daily_limit`")
        );

        assertTrue(
            sql.contains(
                "uk_points_rules_rule_key"
            )
        );

        assertTrue(
            sql.contains(
                "idx_points_rules_enabled"
            )
        );
    }

    /**
     * 验证积分流水查询索引存在。
     */
    @Test
    void shouldContainPointsQueryIndexes()
        throws Exception {
        String sql =
            normalizedSql();

        assertTrue(
            sql.contains(
                "idx_points_logs_user_created"
            )
        );

        assertTrue(
            sql.contains(
                "idx_points_logs_rule_key"
            )
        );

        assertTrue(
            sql.contains(
                "idx_points_logs_source"
            )
        );

        assertTrue(
            sql.contains(
                "idx_points_adjustments_user_created"
            )
        );

        assertTrue(
            sql.contains(
                "idx_points_adjustments_operator_id"
            )
        );
    }

    /**
     * 验证 V2 包含旧数据库字段和索引修复能力。
     */
    @Test
    void shouldContainLegacySchemaReconciliation()
        throws Exception {
        String sql =
            normalizedExecutableSql();

        assertTrue(
            sql.contains(
                "information_schema.columns"
            )
        );

        assertTrue(
            sql.contains(
                "information_schema.statistics"
            )
        );

        assertTrue(
            sql.contains(
                "group_concat_max_len"
            )
        );

        assertTrue(
            sql.contains(
                "group_concat"
            )
        );

        assertTrue(
            sql.contains(
                "prepare aq_stmt"
            )
        );

        assertTrue(
            sql.contains(
                "execute aq_stmt"
            )
        );

        assertTrue(
            sql.contains(
                "deallocate prepare aq_stmt"
            )
        );
    }

    /**
     * 验证已有用户只会补充缺失的统计快照。
     *
     * <p>不能覆盖已有积分和统计数据。</p>
     */
    @Test
    void shouldInitializeOnlyMissingUserStatistics()
        throws Exception {
        String sql =
            normalizedExecutableSql();

        assertTrue(
            sql.contains(
                "insert into "
                    + "`${tableprefix}user_statistics`"
            )
        );

        assertTrue(
            sql.contains(
                "from `${tableprefix}users`"
            )
        );

        assertTrue(
            sql.contains(
                "where not exists"
            )
        );

        assertFalse(
            sql.contains(
                "on duplicate key update"
            ),
            "初始化统计快照时不能覆盖已有统计数据。"
        );

        assertFalse(
            sql.contains(
                "update `${tableprefix}user_statistics`"
            ),
            "V2 不能批量改写现有用户积分和统计数据。"
        );
    }

    /**
     * 验证 V2 统一使用 InnoDB 和 utf8mb4。
     */
    @Test
    void shouldNormalizeEngineAndCharacterSet()
        throws Exception {
        String sql =
            normalizedExecutableSql();

        assertTrue(
            sql.contains(
                "engine = innodb"
            )
        );

        assertTrue(
            sql.contains(
                "default charset = utf8mb4"
            )
        );

        assertTrue(
            sql.contains(
                "collate = utf8mb4_unicode_ci"
            )
        );

        assertTrue(
            sql.contains(
                "convert to character set utf8mb4"
            )
        );
    }

    /**
     * 验证 V2 不包含破坏性数据库操作。
     */
    @Test
    void shouldNotContainDestructiveStatements()
        throws Exception {
        String sql =
            normalizedExecutableSql();

        assertFalse(
            sql.contains("drop table"),
            "V2 不能删除数据表。"
        );

        assertFalse(
            sql.contains("truncate table"),
            "V2 不能清空数据表。"
        );

        assertFalse(
            sql.contains("delete from"),
            "V2 不能删除现有业务数据。"
        );

        assertFalse(
            sql.contains("drop database"),
            "V2 不能删除数据库。"
        );

        assertFalse(
            sql.contains("drop column"),
            "V2 不能删除旧字段。"
        );

        assertFalse(
            sql.contains("rename table"),
            "V2 不能静默改名旧数据表。"
        );
    }

    /**
     * 验证没有使用 MySQL 8.0.12
     * 不支持的 ADD COLUMN IF NOT EXISTS。
     */
    @Test
    void shouldAvoidUnsupportedAddColumnSyntax()
        throws Exception {
        String sql =
            normalizedExecutableSql();

        assertFalse(
            sql.contains(
                "add column if not exists"
            ),
            "MySQL 8.0.12 不能执行 ADD COLUMN IF NOT EXISTS。"
        );

        assertTrue(
            sql.contains(
                "information_schema.columns"
            ),
            "缺失字段应通过 information_schema 判断。"
        );
    }

    /**
     * 验证 V2 不会自行创建或写入 Flyway 历史表。
     */
    @Test
    void shouldLeaveHistoryTableToFlyway()
        throws Exception {
        String sql =
            normalizedExecutableSql();

        assertFalse(
            sql.contains(
                "create table if not exists "
                    + "`${tableprefix}flyway_schema_history`"
            ),
            "Flyway 历史表只能由 Flyway 自己创建。"
        );

        assertFalse(
            sql.contains(
                "insert into "
                    + "`${tableprefix}flyway_schema_history`"
            ),
            "业务迁移不能手工写入 Flyway 历史表。"
        );
    }

    /**
     * 从测试运行时 classpath 读取 V2 SQL。
     */
    private String loadSql() throws Exception {
        ClassLoader classLoader =
            Thread.currentThread()
                .getContextClassLoader();

        InputStream inputStream =
            classLoader.getResourceAsStream(
                RESOURCE_PATH
            );

        assertNotNull(
            inputStream,
            "没有找到 MySQL V2 迁移脚本："
                + RESOURCE_PATH
        );

        try (inputStream) {
            return new String(
                inputStream.readAllBytes(),
                StandardCharsets.UTF_8
            );
        }
    }

    /**
     * 返回完整 SQL 的统一小写版本。
     *
     * <p>该版本保留注释，适合检查表名、字段和索引。</p>
     */
    private String normalizedSql()
        throws Exception {
        return loadSql()
            .toLowerCase(
                Locale.ROOT
            );
    }

    /**
     * 返回移除注释后的 SQL。
     *
     * <p>危险语句和兼容性检查只检查可执行 SQL，
     * 防止说明注释造成误报。</p>
     */
    private String normalizedExecutableSql()
        throws Exception {
        return removeSqlComments(
            loadSql()
        ).toLowerCase(
            Locale.ROOT
        );
    }

    /**
     * 删除 SQL 块注释和独立单行注释。
     *
     * <p>当前 V2 中的动态 SQL 字符串不包含注释标记，
     * 因此这里可以用于静态安全扫描。</p>
     */
    private String removeSqlComments(
        String source
    ) {
        if (
            source == null
                || source.isBlank()
        ) {
            return "";
        }

        String withoutBlockComments =
            source.replaceAll(
                "(?s)/\\*.*?\\*/",
                " "
            );

        String withoutDashComments =
            withoutBlockComments.replaceAll(
                "(?m)^\\s*--.*$",
                " "
            );

        return withoutDashComments.replaceAll(
            "(?m)^\\s*#.*$",
            " "
        );
    }
}
