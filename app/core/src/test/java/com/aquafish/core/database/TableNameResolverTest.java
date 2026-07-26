package com.aquafish.core.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquafish.core.config.AquafishProperties;
import org.junit.jupiter.api.Test;

/**
 * TableNameResolver 数据库表名解析测试。
 *
 * <p>这个测试类用于锁定 Aquafish 数据库表名的统一规则。</p>
 *
 * <p>主要验证：</p>
 *
 * <ol>
 *     <li>没有配置表前缀时使用默认前缀 aq_；</li>
 *     <li>合法自定义前缀可以正常使用；</li>
 *     <li>非法表前缀必须直接拒绝；</li>
 *     <li>不能自动转换大小写；</li>
 *     <li>不能自动删除特殊字符；</li>
 *     <li>不能自动补充末尾下划线；</li>
 *     <li>不能自动删除前后空格；</li>
 *     <li>逻辑表名必须经过统一校验；</li>
 *     <li>运行配置和安装阶段配置使用相同规则；</li>
 *     <li>完整真实表名必须由 TableNameResolver 生成。</li>
 * </ol>
 *
 * <p>该测试不会连接数据库，也不会执行任何 SQL。</p>
 */
class TableNameResolverTest {

    /**
     * 验证默认数据库表前缀常量。
     */
    @Test
    void shouldExposeDefaultTablePrefix() {
        assertEquals(
            "aq_",
            TableNameResolver.DEFAULT_TABLE_PREFIX
        );
    }

    /**
     * 验证配置值为 null 时使用默认表前缀。
     */
    @Test
    void shouldUseDefaultPrefixWhenConfiguredPrefixIsNull() {
        assertEquals(
            "aq_",
            TableNameResolver.normalizeConfiguredPrefix(null)
        );
    }

    /**
     * 验证配置值为空或纯空白时使用默认表前缀。
     *
     * <p>空配置表示用户没有设置自定义值，
     * 因此可以使用 Aquafish 默认值 aq_。</p>
     */
    @Test
    void shouldUseDefaultPrefixWhenConfiguredPrefixIsBlank() {
        assertEquals(
            "aq_",
            TableNameResolver.normalizeConfiguredPrefix("")
        );

        assertEquals(
            "aq_",
            TableNameResolver.normalizeConfiguredPrefix("   ")
        );
    }

    /**
     * 验证合法数据库表前缀保持原样。
     *
     * <p>合法值不应被修改、转换或重新拼接。</p>
     */
    @Test
    void shouldKeepValidConfiguredPrefixes() {
        assertEquals(
            "aq_",
            TableNameResolver.normalizeConfiguredPrefix("aq_")
        );

        assertEquals(
            "bbs_",
            TableNameResolver.normalizeConfiguredPrefix("bbs_")
        );

        assertEquals(
            "site01_",
            TableNameResolver.normalizeConfiguredPrefix("site01_")
        );

        assertEquals(
            "aquafish2026_",
            TableNameResolver.normalizeConfiguredPrefix(
                "aquafish2026_"
            )
        );
    }

    /**
     * 验证大写表前缀必须被拒绝。
     *
     * <p>不能把 AQ_ 自动转换成 aq_，
     * 因为 Linux MySQL 可能区分表名大小写。</p>
     */
    @Test
    void shouldRejectUppercasePrefix() {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () ->
                    TableNameResolver
                        .normalizeConfiguredPrefix("AQ_")
            );

        assertTrue(
            exception.getMessage()
                .contains("数据库表前缀非法")
        );
    }

    /**
     * 验证缺少末尾下划线的表前缀必须被拒绝。
     *
     * <p>不能自动把 aq 修改成 aq_。</p>
     */
    @Test
    void shouldRejectPrefixWithoutTrailingUnderscore() {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () ->
                    TableNameResolver
                        .normalizeConfiguredPrefix("aq")
            );

        assertTrue(
            exception.getMessage()
                .contains("数据库表前缀非法")
        );
    }

    /**
     * 验证包含横线的表前缀必须被拒绝。
     *
     * <p>不能自动删除横线后继续使用。</p>
     */
    @Test
    void shouldRejectPrefixContainingHyphen() {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () ->
                    TableNameResolver
                        .normalizeConfiguredPrefix("aq-site_")
            );

        assertTrue(
            exception.getMessage()
                .contains("数据库表前缀非法")
        );
    }

    /**
     * 验证以下划线开头的表前缀必须被拒绝。
     */
    @Test
    void shouldRejectPrefixStartingWithUnderscore() {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () ->
                    TableNameResolver
                        .normalizeConfiguredPrefix("_aq_")
            );

        assertTrue(
            exception.getMessage()
                .contains("数据库表前缀非法")
        );
    }

    /**
     * 验证数据库表前缀前后存在空格时必须直接拒绝。
     *
     * <p>这里分别检查：</p>
     *
     * <ul>
     *     <li>前导空格；</li>
     *     <li>尾随空格。</li>
     * </ul>
     *
     * <p>不能静默调用 trim()，否则配置文件原始值
     * 和最终真实表名会不一致。</p>
     */
    @Test
    void shouldRejectPrefixContainingLeadingOrTrailingSpaces() {
        IllegalStateException leadingWhitespaceException =
            assertThrows(
                IllegalStateException.class,
                () ->
                    TableNameResolver
                        .normalizeConfiguredPrefix(" aq_")
            );

        assertTrue(
            leadingWhitespaceException
                .getMessage()
                .contains("数据库表前缀非法")
        );

        IllegalStateException trailingWhitespaceException =
            assertThrows(
                IllegalStateException.class,
                () ->
                    TableNameResolver
                        .normalizeConfiguredPrefix("aq_ ")
            );

        assertTrue(
            trailingWhitespaceException
                .getMessage()
                .contains("数据库表前缀非法")
        );
    }

    /**
     * 验证超过最大长度的表前缀必须被拒绝。
     */
    @Test
    void shouldRejectOverlengthPrefix() {
        /*
         * 25 个字符：
         * 23 个字母 + 1 个数字 + 1 个下划线。
         *
         * 超过当前允许的 24 个字符。
         */
        String overlengthPrefix =
            "abcdefghijklmnopqrstuvw1_";

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () ->
                    TableNameResolver
                        .normalizeConfiguredPrefix(
                            overlengthPrefix
                        )
            );

        assertTrue(
            exception.getMessage()
                .contains("长度不能超过")
        );
    }

    /**
     * 验证实例方法会读取当前 AquafishProperties 表前缀。
     */
    @Test
    void shouldResolveTableNameFromCurrentProperties() {
        AquafishProperties properties =
            mock(AquafishProperties.class);

        when(properties.tablePrefix())
            .thenReturn("site_");

        TableNameResolver resolver =
            new TableNameResolver(properties);

        assertEquals(
            "site_users",
            resolver.tableName("users")
        );

        assertEquals(
            "site_user_roles",
            resolver.tableName("user_roles")
        );
    }

    /**
     * 验证 currentPrefix() 和兼容接口 tablePrefix()
     * 返回相同的严格校验结果。
     */
    @Test
    void shouldExposeCurrentPrefixThroughBothMethods() {
        AquafishProperties properties =
            mock(AquafishProperties.class);

        when(properties.tablePrefix())
            .thenReturn("bbs_");

        TableNameResolver resolver =
            new TableNameResolver(properties);

        assertEquals(
            "bbs_",
            resolver.currentPrefix()
        );

        assertEquals(
            "bbs_",
            resolver.tablePrefix()
        );
    }

    /**
     * 验证安装阶段可以使用显式表前缀生成真实表名。
     *
     * <p>该静态入口不依赖当前 application.yaml，
     * 可以供安装器和 Flyway 工厂使用。</p>
     */
    @Test
    void shouldResolveTableNameFromExplicitPrefix() {
        assertEquals(
            "site_users",
            TableNameResolver.tableName(
                "site_",
                "users"
            )
        );

        assertEquals(
            "site_flyway_schema_history",
            TableNameResolver.tableName(
                "site_",
                "flyway_schema_history"
            )
        );
    }

    /**
     * 验证显式前缀为 null 或空白时，
     * 完整表名使用默认前缀 aq_。
     */
    @Test
    void shouldUseDefaultPrefixWhenResolvingExplicitTableName() {
        assertEquals(
            "aq_users",
            TableNameResolver.tableName(
                null,
                "users"
            )
        );

        assertEquals(
            "aq_roles",
            TableNameResolver.tableName(
                "   ",
                "roles"
            )
        );
    }

    /**
     * 验证源码级逻辑表名可以统一执行 trim 和小写转换。
     *
     * <p>逻辑表名来自项目源码，不是用户配置，
     * 因此允许做确定性的规范化。</p>
     */
    @Test
    void shouldNormalizeLogicalTableName() {
        assertEquals(
            "aq_users",
            TableNameResolver.tableName(
                "aq_",
                " Users "
            )
        );

        assertEquals(
            "aq_user_roles",
            TableNameResolver.tableName(
                "aq_",
                "USER_ROLES"
            )
        );
    }

    /**
     * 验证危险或非法逻辑表名必须被拒绝。
     */
    @Test
    void shouldRejectUnsafeLogicalTableName() {
        assertThrows(
            IllegalStateException.class,
            () ->
                TableNameResolver.tableName(
                    "aq_",
                    "user-roles"
                )
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                TableNameResolver.tableName(
                    "aq_",
                    "user roles"
                )
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                TableNameResolver.tableName(
                    "aq_",
                    "user.roles"
                )
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                TableNameResolver.tableName(
                    "aq_",
                    "1users"
                )
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                TableNameResolver.tableName(
                    "aq_",
                    "users;drop_table"
                )
        );
    }

    /**
     * 验证逻辑表名不能为 null、空字符串或纯空白。
     */
    @Test
    void shouldRejectNullOrBlankLogicalTableName() {
        IllegalStateException nullException =
            assertThrows(
                IllegalStateException.class,
                () ->
                    TableNameResolver.tableName(
                        "aq_",
                        null
                    )
            );

        assertTrue(
            nullException.getMessage()
                .contains("逻辑表名不能为空")
        );

        IllegalStateException emptyException =
            assertThrows(
                IllegalStateException.class,
                () ->
                    TableNameResolver.tableName(
                        "aq_",
                        ""
                    )
            );

        assertTrue(
            emptyException.getMessage()
                .contains("逻辑表名不能为空")
        );

        IllegalStateException blankException =
            assertThrows(
                IllegalStateException.class,
                () ->
                    TableNameResolver.tableName(
                        "aq_",
                        "   "
                    )
            );

        assertTrue(
            blankException.getMessage()
                .contains("逻辑表名不能为空")
        );
    }
}