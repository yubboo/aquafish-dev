package com.aquafish.core.database.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * MySQL V1 Flyway 迁移脚本静态安全测试。
 *
 * <p>该测试不会连接真实数据库，也不会执行 Flyway migrate。</p>
 *
 * <p>它只负责检查 V1 SQL 文件是否：</p>
 *
 * <ol>
 *     <li>存在并能够从 classpath 读取；</li>
 *     <li>继续使用动态数据表前缀；</li>
 *     <li>包含本阶段必须建立的核心结构；</li>
 *     <li>包含旧 users 表兼容升级逻辑；</li>
 *     <li>没有出现危险删除语句；</li>
 *     <li>没有使用 MySQL 8.0.12 不支持的可执行语法；</li>
 *     <li>继续统一使用 InnoDB 和 utf8mb4。</li>
 * </ol>
 *
 * <p>重要说明：</p>
 *
 * <p>SQL 文件中的说明注释可能会提到某些禁止语法，
 * 例如介绍为什么不能使用 ADD COLUMN IF NOT EXISTS。</p>
 *
 * <p>因此涉及危险语句和兼容性语法的检查，
 * 必须先剔除 SQL 注释，只检查真正可执行的 SQL。</p>
 */
class FlywayMysqlV1MigrationTest {

    /**
     * V1 迁移脚本在 classpath 中的固定位置。
     */
    private static final String RESOURCE_PATH =
        "db/migration/core/mysql/"
            + "V1__core_identity_auth_reconciliation.sql";

    /**
     * 验证迁移脚本存在，并且能够完整读取。
     */
    @Test
    void shouldLoadMysqlV1MigrationFromClasspath()
        throws Exception {
        String sql =
            loadSql();

        assertFalse(
            sql.isBlank(),
            "MySQL V1 迁移脚本不能为空。"
        );

        assertTrue(
            sql.contains(
                "V1"
            ),
            "V1 迁移脚本应该保留版本说明。"
        );
    }

    /**
     * 验证所有业务表继续使用动态表前缀。
     */
    @Test
    void shouldUseDynamicTablePrefix() throws Exception {
        String sql =
            loadSql();

        assertTrue(
            sql.contains(
                "${tablePrefix}users"
            )
        );

        assertTrue(
            sql.contains(
                "${tablePrefix}user_login_logs"
            )
        );

        assertTrue(
            sql.contains(
                "${tablePrefix}role_permissions"
            )
        );

        assertTrue(
            sql.contains(
                "${tablePrefix}admin_operation_logs"
            )
        );

        /*
         * 禁止在真实 SQL 标识符中写死 aq_。
         *
         * 当前默认前缀虽然是 aq_，
         * 但其他安装环境可能使用自定义合法前缀。
         */
        assertFalse(
            executableSql()
                .contains(
                    "`aq_"
                ),
            "Flyway SQL 中不能写死 aq_ 表前缀。"
        );
    }

    /**
     * 验证数据库名称使用 Flyway 内置占位符。
     */
    @Test
    void shouldUseFlywayDatabasePlaceholder()
        throws Exception {
        String sql =
            loadSql();

        assertTrue(
            sql.contains(
                "${flyway:database}"
            ),
            "数据库字符集迁移必须使用 Flyway 数据库名占位符。"
        );
    }

    /**
     * 验证登录流程所需表和字段都在 V1 中。
     */
    @Test
    void shouldContainAuthenticationInfrastructure()
        throws Exception {
        String sql =
            normalizedSql();

        assertTrue(
            sql.contains(
                "create table if not exists "
                    + "`${tableprefix}user_login_logs`"
            )
        );

        assertTrue(
            sql.contains(
                "`last_login_at`"
            )
        );

        assertTrue(
            sql.contains(
                "`last_login_ip`"
            )
        );

        assertTrue(
            sql.contains(
                "`last_user_agent`"
            )
        );

        assertTrue(
            sql.contains(
                "`login_count`"
            )
        );

        assertTrue(
            sql.contains(
                "`failure_reason`"
            )
        );

        assertTrue(
            sql.contains(
                "`x_forwarded_for`"
            )
        );

        assertTrue(
            sql.contains(
                "`x_real_ip`"
            )
        );
    }

    /**
     * 验证标准 RBAC 角色权限关联表存在。
     */
    @Test
    void shouldContainRolePermissionInfrastructure()
        throws Exception {
        String sql =
            normalizedSql();

        assertTrue(
            sql.contains(
                "create table if not exists "
                    + "`${tableprefix}role_permissions`"
            )
        );

        assertTrue(
            sql.contains(
                "`role_id`"
            )
        );

        assertTrue(
            sql.contains(
                "`permission_id`"
            )
        );

        assertTrue(
            sql.contains(
                "uk_role_permissions_role_permission"
            )
        );

        assertTrue(
            sql.contains(
                "idx_role_permissions_role_id"
            )
        );

        assertTrue(
            sql.contains(
                "idx_role_permissions_permission_id"
            )
        );
    }

    /**
     * 验证后台操作日志表被纳入 Flyway 管理。
     */
    @Test
    void shouldContainAdminOperationLogInfrastructure()
        throws Exception {
        String sql =
            normalizedSql();

        assertTrue(
            sql.contains(
                "create table if not exists "
                    + "`${tableprefix}admin_operation_logs`"
            )
        );

        assertTrue(
            sql.contains(
                "`operator_id`"
            )
        );

        assertTrue(
            sql.contains(
                "`action_key`"
            )
        );

        assertTrue(
            sql.contains(
                "`target_type`"
            )
        );

        assertTrue(
            sql.contains(
                "`target_id`"
            )
        );

        assertTrue(
            sql.contains(
                "idx_admin_operation_logs_created_at"
            )
        );
    }

    /**
     * 验证迁移脚本统一使用 InnoDB、utf8mb4
     * 和 utf8mb4_unicode_ci。
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
                "character set utf8mb4"
            )
        );

        assertTrue(
            sql.contains(
                "collate utf8mb4_unicode_ci"
            )
        );

        assertTrue(
            sql.contains(
                "convert to character set utf8mb4"
            )
        );
    }

    /**
     * 验证 V1 不包含破坏性数据操作。
     *
     * <p>这里只检查实际可执行 SQL，不检查说明注释。</p>
     */
    @Test
    void shouldNotContainDestructiveStatements()
        throws Exception {
        String sql =
            normalizedExecutableSql();

        assertFalse(
            sql.contains(
                "drop table"
            ),
            "V1 不能删除数据表。"
        );

        assertFalse(
            sql.contains(
                "truncate table"
            ),
            "V1 不能清空数据表。"
        );

        assertFalse(
            sql.contains(
                "delete from"
            ),
            "V1 不能删除现有业务数据。"
        );

        assertFalse(
            sql.contains(
                "drop database"
            ),
            "V1 不能删除数据库。"
        );

        assertFalse(
            sql.contains(
                "drop column"
            ),
            "V1 不能删除旧字段。"
        );
    }

    /**
     * 验证真正执行的 SQL 没有使用 MySQL 8.0.12
     * 不支持的 ADD COLUMN IF NOT EXISTS。
     *
     * <p>SQL 注释可以解释该语法为什么不能使用，
     * 但可执行语句中绝对不能出现。</p>
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

        /*
         * 应该通过 information_schema.columns
         * 和 PREPARE 实现兼容升级。
         */
        assertTrue(
            sql.contains(
                "information_schema.columns"
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
     * 验证迁移脚本没有自行创建或篡改
     * Flyway 历史表。
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
            "Flyway 历史表只能由 Flyway 自己管理。"
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
     * 从测试运行时 classpath 读取 V1 SQL。
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
            "没有找到 MySQL V1 迁移脚本："
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
     * 返回原始 SQL 的统一小写版本。
     *
     * <p>该方法保留注释，适合检查：</p>
     *
     * <ul>
     *     <li>表结构是否存在；</li>
     *     <li>字段名是否存在；</li>
     *     <li>说明内容是否存在。</li>
     * </ul>
     */
    private String normalizedSql() throws Exception {
        return loadSql()
            .toLowerCase(
                Locale.ROOT
            );
    }

    /**
     * 返回移除注释后的可执行 SQL。
     *
     * <p>该方法保留原始大小写。</p>
     */
    private String executableSql() throws Exception {
        return removeSqlComments(
            loadSql()
        );
    }

    /**
     * 返回移除注释并统一小写后的可执行 SQL。
     *
     * <p>适合检查危险语句和数据库兼容性语法。</p>
     */
    private String normalizedExecutableSql()
        throws Exception {
        return executableSql()
            .toLowerCase(
                Locale.ROOT
            );
    }

    /**
     * 删除 SQL 中的注释。
     *
     * <p>支持：</p>
     *
     * <ol>
     *     <li>块注释：斜杠星号……星号斜杠；</li>
     *     <li>双横线单行注释；</li>
     *     <li>井号单行注释。</li>
     * </ol>
     *
     * <p>处理过程中会保留单引号字符串内容，
     * 防止字符串中的双横线、井号或注释符号被误删。</p>
     */
    private String removeSqlComments(
        String source
    ) {
        if (
            source == null
                || source.isEmpty()
        ) {
            return "";
        }

        StringBuilder result =
            new StringBuilder(
                source.length()
            );

        boolean inSingleQuotedString =
            false;

        boolean inDoubleQuotedString =
            false;

        boolean inBacktickIdentifier =
            false;

        boolean inBlockComment =
            false;

        boolean inLineComment =
            false;

        for (
            int index = 0;
            index < source.length();
            index++
        ) {
            char current =
                source.charAt(index);

            char next =
                index + 1 < source.length()
                    ? source.charAt(index + 1)
                    : '\0';

            /*
             * 当前位于块注释中。
             */
            if (inBlockComment) {
                if (
                    current == '*'
                        && next == '/'
                ) {
                    inBlockComment =
                        false;

                    index++;

                    result.append(' ');
                }

                continue;
            }

            /*
             * 当前位于单行注释中。
             */
            if (inLineComment) {
                if (
                    current == '\r'
                        || current == '\n'
                ) {
                    inLineComment =
                        false;

                    result.append(current);
                }

                continue;
            }

            /*
             * 当前位于单引号字符串中。
             */
            if (inSingleQuotedString) {
                result.append(current);

                /*
                 * MySQL 字符串中的两个连续单引号，
                 * 表示一个转义后的单引号。
                 */
                if (
                    current == '\''
                        && next == '\''
                ) {
                    result.append(next);

                    index++;

                    continue;
                }

                /*
                 * 反斜杠转义字符。
                 */
                if (
                    current == '\\'
                        && next != '\0'
                ) {
                    result.append(next);

                    index++;

                    continue;
                }

                if (current == '\'') {
                    inSingleQuotedString =
                        false;
                }

                continue;
            }

            /*
             * 当前位于双引号字符串中。
             */
            if (inDoubleQuotedString) {
                result.append(current);

                if (
                    current == '"'
                        && next == '"'
                ) {
                    result.append(next);

                    index++;

                    continue;
                }

                if (
                    current == '\\'
                        && next != '\0'
                ) {
                    result.append(next);

                    index++;

                    continue;
                }

                if (current == '"') {
                    inDoubleQuotedString =
                        false;
                }

                continue;
            }

            /*
             * 当前位于反引号标识符中。
             */
            if (inBacktickIdentifier) {
                result.append(current);

                if (current == '`') {
                    inBacktickIdentifier =
                        false;
                }

                continue;
            }

            /*
             * 进入单引号字符串。
             */
            if (current == '\'') {
                inSingleQuotedString =
                    true;

                result.append(current);

                continue;
            }

            /*
             * 进入双引号字符串。
             */
            if (current == '"') {
                inDoubleQuotedString =
                    true;

                result.append(current);

                continue;
            }

            /*
             * 进入反引号标识符。
             */
            if (current == '`') {
                inBacktickIdentifier =
                    true;

                result.append(current);

                continue;
            }

            /*
             * 进入块注释。
             */
            if (
                current == '/'
                    && next == '*'
            ) {
                inBlockComment =
                    true;

                index++;

                result.append(' ');

                continue;
            }

            /*
             * 进入双横线单行注释。
             *
             * MySQL 中标准双横线注释通常要求后面跟空白，
             * 这里静态测试采用更严格策略：
             * 非字符串中的两个连续横线都视为注释开始。
             */
            if (
                current == '-'
                    && next == '-'
            ) {
                inLineComment =
                    true;

                index++;

                result.append(' ');

                continue;
            }

            /*
             * 进入井号单行注释。
             */
            if (current == '#') {
                inLineComment =
                    true;

                result.append(' ');

                continue;
            }

            result.append(current);
        }

        return result.toString();
    }
}
