package com.aquafish.core.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * DatabaseSettings 数据库配置单元测试。
 *
 * <p>这个测试类用于锁定 Aquafish 数据库配置的归一化规则。</p>
 *
 * <p>主要验证：</p>
 *
 * <ol>
 *     <li>MySQL 默认配置正确；</li>
 *     <li>PostgreSQL 默认配置正确；</li>
 *     <li>数据库类型为空时使用 MySQL；</li>
 *     <li>数据库地址、名称和用户名为空时使用默认值；</li>
 *     <li>数据库端口无效时使用对应数据库默认端口；</li>
 *     <li>数据库密码不会被 trim；</li>
 *     <li>数据库密码为 null 时转换为空字符串；</li>
 *     <li>数据库表前缀统一交给 TableNameResolver 校验；</li>
 *     <li>非法前缀不能被静默修复；</li>
 *     <li>归一化后的配置具备必要连接字段。</li>
 * </ol>
 *
 * <p>该测试不会连接数据库，也不会修改任何数据表。</p>
 */
class DatabaseSettingsTest {

    /**
     * 验证 MySQL 默认配置。
     */
    @Test
    void shouldCreateDefaultMysqlSettings() {
        DatabaseSettings settings =
            DatabaseSettings.defaultMysql();

        assertNotNull(settings);

        assertEquals(
            DatabaseType.MYSQL,
            settings.type()
        );

        assertEquals(
            "127.0.0.1",
            settings.host()
        );

        assertEquals(
            3306,
            settings.port()
        );

        assertEquals(
            "aquafish",
            settings.name()
        );

        assertEquals(
            "aquafish",
            settings.username()
        );

        assertEquals(
            "",
            settings.password()
        );

        assertEquals(
            TableNameResolver.DEFAULT_TABLE_PREFIX,
            settings.tablePrefix()
        );
    }

    /**
     * 验证 PostgreSQL 默认配置。
     */
    @Test
    void shouldCreateDefaultPostgresqlSettings() {
        DatabaseSettings settings =
            DatabaseSettings.defaultPostgresql();

        assertNotNull(settings);

        assertEquals(
            DatabaseType.POSTGRESQL,
            settings.type()
        );

        assertEquals(
            "127.0.0.1",
            settings.host()
        );

        assertEquals(
            5432,
            settings.port()
        );

        assertEquals(
            "aquafish",
            settings.name()
        );

        assertEquals(
            "aquafish",
            settings.username()
        );

        assertEquals(
            "",
            settings.password()
        );

        assertEquals(
            TableNameResolver.DEFAULT_TABLE_PREFIX,
            settings.tablePrefix()
        );
    }

    @Test
    void shouldKeepMariadbAsIndependentDatabaseType() {
        DatabaseSettings settings = DatabaseSettings.defaultMariadb();

        assertEquals(DatabaseType.MARIADB, settings.type());
        assertEquals(3306, settings.port());
        assertEquals(DatabaseType.MARIADB, DatabaseType.fromValue("mariadb"));
    }

    @Test
    void shouldRejectUnknownDatabaseTypeInsteadOfFallingBackToMysql() {
        assertThrows(
            IllegalArgumentException.class,
            () -> DatabaseType.fromValue("sqlite")
        );
    }

    /**
     * 验证数据库类型为空时，默认使用 MySQL。
     */
    @Test
    void shouldUseMysqlWhenDatabaseTypeIsNull() {
        DatabaseSettings settings =
            new DatabaseSettings(
                null,
                "127.0.0.1",
                3306,
                "aquafish",
                "aquafish",
                "",
                "aq_"
            );

        DatabaseSettings normalized =
            settings.normalized();

        assertEquals(
            DatabaseType.MYSQL,
            normalized.type()
        );
    }

    /**
     * 验证 MySQL 配置中的无效端口会恢复为 3306。
     */
    @Test
    void shouldUseMysqlDefaultPortWhenPortIsInvalid() {
        DatabaseSettings zeroPortSettings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                0,
                "aquafish",
                "aquafish",
                "",
                "aq_"
            );

        DatabaseSettings negativePortSettings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                -1,
                "aquafish",
                "aquafish",
                "",
                "aq_"
            );

        DatabaseSettings nullPortSettings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                null,
                "aquafish",
                "aquafish",
                "",
                "aq_"
            );

        assertEquals(
            3306,
            zeroPortSettings.normalized().port()
        );

        assertEquals(
            3306,
            negativePortSettings.normalized().port()
        );

        assertEquals(
            3306,
            nullPortSettings.normalized().port()
        );
    }

    /**
     * 验证 PostgreSQL 配置中的无效端口会恢复为 5432。
     */
    @Test
    void shouldUsePostgresqlDefaultPortWhenPortIsInvalid() {
        DatabaseSettings settings =
            new DatabaseSettings(
                DatabaseType.POSTGRESQL,
                "127.0.0.1",
                0,
                "aquafish",
                "aquafish",
                "",
                "aq_"
            );

        DatabaseSettings normalized =
            settings.normalized();

        assertEquals(
            5432,
            normalized.port()
        );
    }

    /**
     * 验证数据库连接基础字段为空时使用默认值。
     */
    @Test
    void shouldUseDefaultsForBlankConnectionFields() {
        DatabaseSettings settings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "   ",
                3306,
                "",
                null,
                "",
                "aq_"
            );

        DatabaseSettings normalized =
            settings.normalized();

        assertEquals(
            "127.0.0.1",
            normalized.host()
        );

        assertEquals(
            "aquafish",
            normalized.name()
        );

        assertEquals(
            "aquafish",
            normalized.username()
        );
    }

    /**
     * 验证数据库地址、数据库名和用户名会去除前后空格。
     *
     * <p>这些字段不是 SQL 标识符，可以做普通文本归一化。</p>
     */
    @Test
    void shouldTrimOrdinaryTextFields() {
        DatabaseSettings settings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                " 127.0.0.1 ",
                3306,
                " aquafish ",
                " root ",
                "password",
                "aq_"
            );

        DatabaseSettings normalized =
            settings.normalized();

        assertEquals(
            "127.0.0.1",
            normalized.host()
        );

        assertEquals(
            "aquafish",
            normalized.name()
        );

        assertEquals(
            "root",
            normalized.username()
        );
    }

    /**
     * 验证数据库密码不会被 trim。
     *
     * <p>密码前后的空格可能属于真实密码内容，
     * 因此归一化时不能删除。</p>
     */
    @Test
    void shouldPreservePasswordExactly() {
        String password =
            "  secret password  ";

        DatabaseSettings settings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "root",
                password,
                "aq_"
            );

        DatabaseSettings normalized =
            settings.normalized();

        assertEquals(
            password,
            normalized.password()
        );
    }

    /**
     * 验证数据库密码为 null 时转换为空字符串。
     */
    @Test
    void shouldConvertNullPasswordToEmptyString() {
        DatabaseSettings settings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "root",
                null,
                "aq_"
            );

        DatabaseSettings normalized =
            settings.normalized();

        assertEquals(
            "",
            normalized.password()
        );
    }

    /**
     * 验证表前缀为空时使用 Aquafish 默认前缀 aq_。
     */
    @Test
    void shouldUseDefaultTablePrefixWhenBlank() {
        DatabaseSettings nullPrefixSettings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "root",
                "",
                null
            );

        DatabaseSettings emptyPrefixSettings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "root",
                "",
                ""
            );

        DatabaseSettings blankPrefixSettings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "root",
                "",
                "   "
            );

        assertEquals(
            "aq_",
            nullPrefixSettings
                .normalized()
                .tablePrefix()
        );

        assertEquals(
            "aq_",
            emptyPrefixSettings
                .normalized()
                .tablePrefix()
        );

        assertEquals(
            "aq_",
            blankPrefixSettings
                .normalized()
                .tablePrefix()
        );
    }

    /**
     * 验证合法自定义表前缀保持原样。
     */
    @Test
    void shouldKeepValidTablePrefix() {
        DatabaseSettings settings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "root",
                "",
                "site01_"
            );

        DatabaseSettings normalized =
            settings.normalized();

        assertEquals(
            "site01_",
            normalized.tablePrefix()
        );
    }

    /**
     * 验证包含空格的数据库表前缀必须被拒绝。
     *
     * <p>包含以下任何一种空格都不能自动修复：</p>
     *
     * <ul>
     *     <li>前导空格；</li>
     *     <li>尾随空格；</li>
     *     <li>中间空格。</li>
     * </ul>
     */
    @Test
    void shouldRejectTablePrefixContainingSpaces() {
        DatabaseSettings leadingSpaceSettings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "root",
                "",
                " aq_"
            );

        IllegalStateException leadingException =
            assertThrows(
                IllegalStateException.class,
                leadingSpaceSettings::normalized
            );

        assertTrue(
            leadingException
                .getMessage()
                .contains("数据库表前缀非法")
        );

        DatabaseSettings trailingSpaceSettings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "root",
                "",
                "aq_ "
            );

        IllegalStateException trailingException =
            assertThrows(
                IllegalStateException.class,
                trailingSpaceSettings::normalized
            );

        assertTrue(
            trailingException
                .getMessage()
                .contains("数据库表前缀非法")
        );

        DatabaseSettings middleSpaceSettings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "root",
                "",
                "aq site_"
            );

        IllegalStateException middleException =
            assertThrows(
                IllegalStateException.class,
                middleSpaceSettings::normalized
            );

        assertTrue(
            middleException
                .getMessage()
                .contains("数据库表前缀非法")
        );
    }

    /**
     * 验证大写表前缀必须被拒绝。
     *
     * <p>不能把 AQ_ 自动转换成 aq_。</p>
     */
    @Test
    void shouldRejectUppercaseTablePrefix() {
        DatabaseSettings settings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "root",
                "",
                "AQ_"
            );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                settings::normalized
            );

        assertTrue(
            exception.getMessage()
                .contains("数据库表前缀非法")
        );
    }

    /**
     * 验证缺少末尾下划线的表前缀必须被拒绝。
     *
     * <p>不能自动把 aq 修改为 aq_。</p>
     */
    @Test
    void shouldRejectTablePrefixWithoutTrailingUnderscore() {
        DatabaseSettings settings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "root",
                "",
                "aq"
            );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                settings::normalized
            );

        assertTrue(
            exception.getMessage()
                .contains("数据库表前缀非法")
        );
    }

    /**
     * 验证包含横线的表前缀必须被拒绝。
     *
     * <p>不能自动删除横线。</p>
     */
    @Test
    void shouldRejectTablePrefixContainingHyphen() {
        DatabaseSettings settings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "root",
                "",
                "aq-site_"
            );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                settings::normalized
            );

        assertTrue(
            exception.getMessage()
                .contains("数据库表前缀非法")
        );
    }

    /**
     * 验证过长表前缀必须被拒绝。
     */
    @Test
    void shouldRejectOverlengthTablePrefix() {
        DatabaseSettings settings =
            new DatabaseSettings(
                DatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "aquafish",
                "root",
                "",
                "abcdefghijklmnopqrstuvw1_"
            );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                settings::normalized
            );

        assertTrue(
            exception.getMessage()
                .contains("长度不能超过")
        );
    }

    /**
     * 验证归一化后的默认配置包含数据库连接必要字段。
     */
    @Test
    void shouldHaveRequiredFieldsAfterNormalization() {
        DatabaseSettings settings =
            new DatabaseSettings(
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

        assertTrue(
            settings.hasRequiredFields()
        );

        DatabaseSettings normalized =
            settings.normalized();

        assertFalse(
            normalized.host().isBlank()
        );

        assertTrue(
            normalized.port() > 0
        );

        assertFalse(
            normalized.name().isBlank()
        );

        assertFalse(
            normalized.username().isBlank()
        );
    }
}
