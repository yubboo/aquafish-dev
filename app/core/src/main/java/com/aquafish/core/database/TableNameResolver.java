package com.aquafish.core.database;

import com.aquafish.core.config.AquafishProperties;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Aquafish 数据库表名统一解析器。
 *
 * <p>这个类是 Aquafish 所有数据库表名的唯一解析入口。</p>
 *
 * <p>主要职责：</p>
 *
 * <ol>
 *     <li>读取当前运行配置中的数据库表前缀；</li>
 *     <li>严格校验用户配置的数据表前缀；</li>
 *     <li>校验并规范化源码中的逻辑表名；</li>
 *     <li>把表前缀与逻辑表名组合成真实表名；</li>
 *     <li>为安装、迁移、后台和业务模块提供相同规则。</li>
 * </ol>
 *
 * <p>正确使用方式：</p>
 *
 * <pre>
 * tableNameResolver.tableName("users");
 *
 * TableNameResolver.tableName(
 *     settings.tablePrefix(),
 *     "users"
 * );
 * </pre>
 *
 * <p>禁止使用方式：</p>
 *
 * <pre>
 * String table = "aq_users";
 * String table = settings.tablePrefix() + "users";
 * </pre>
 *
 * <p>为什么不能由各个模块自行拼接：</p>
 *
 * <ul>
 *     <li>可能绕过前缀合法性校验；</li>
 *     <li>可能出现自动转小写等不一致行为；</li>
 *     <li>可能让安装阶段和运行阶段使用不同真实表名；</li>
 *     <li>可能让 Flyway 历史表使用错误前缀；</li>
 *     <li>会增加动态 SQL 表名注入风险。</li>
 * </ul>
 */
@Component
public class TableNameResolver {

    /**
     * Aquafish 默认数据库表前缀。
     *
     * <p>当配置值为 null、空字符串或纯空白时，
     * 统一使用该默认值。</p>
     */
    public static final String DEFAULT_TABLE_PREFIX =
        "aq_";

    /**
     * 数据库表前缀最大长度。
     *
     * <p>该限制只针对前缀本身，不包含后面的逻辑表名。</p>
     *
     * <p>限制前缀长度可以避免组合后的真实表名
     * 超过不同数据库的标识符长度上限。</p>
     */
    public static final int MAX_TABLE_PREFIX_LENGTH =
        24;

    /**
     * 数据库表前缀严格校验规则。
     *
     * <p>合法前缀必须：</p>
     *
     * <ol>
     *     <li>以小写英文字母开头；</li>
     *     <li>中间只能包含小写字母、数字和下划线；</li>
     *     <li>必须以下划线结尾。</li>
     * </ol>
     *
     * <p>合法示例：</p>
     *
     * <pre>
     * aq_
     * bbs_
     * site01_
     * </pre>
     *
     * <p>非法示例：</p>
     *
     * <pre>
     * AQ_
     * aq
     * aq-
     * _aq_
     * aq users_
     * </pre>
     */
    private static final Pattern TABLE_PREFIX_PATTERN =
        Pattern.compile(
            "^[a-z][a-z0-9_]*_$"
        );

    /**
     * 逻辑表名校验规则。
     *
     * <p>逻辑表名是不包含数据库表前缀的源码级表名。</p>
     *
     * <p>规则：</p>
     *
     * <ol>
     *     <li>必须以小写英文字母开头；</li>
     *     <li>只能包含小写字母、数字和下划线；</li>
     *     <li>长度为 1 到 64 个字符。</li>
     * </ol>
     */
    private static final Pattern LOGICAL_TABLE_NAME_PATTERN =
        Pattern.compile(
            "^[a-z][a-z0-9_]{0,63}$"
        );

    /**
     * Aquafish 当前运行配置。
     *
     * <p>当前只读取：</p>
     *
     * <pre>
     * aquafish.database.table-prefix
     * </pre>
     *
     * <p>该配置通常来自：</p>
     *
     * <pre>
     * 当前实例工作目录中的 application.yaml
     * </pre>
     */
    private final AquafishProperties properties;

    /**
     * 创建数据库表名解析器。
     *
     * @param properties Aquafish 当前运行配置
     */
    public TableNameResolver(
        AquafishProperties properties
    ) {
        this.properties =
            properties;
    }

    /**
     * 使用当前运行配置生成真实数据库表名。
     *
     * <p>适用于应用已经启动，并且需要使用当前
     * application.yaml 表前缀的业务代码。</p>
     *
     * <p>示例：</p>
     *
     * <pre>
     * 当前前缀：aq_
     * 逻辑表名：users
     * 返回：aq_users
     * </pre>
     *
     * @param logicalTableName 不包含表前缀的逻辑表名
     * @return 经过统一校验的真实数据库表名
     * @throws IllegalStateException 表前缀或逻辑表名非法时抛出
     */
    public String tableName(
        String logicalTableName
    ) {
        return tableName(
            properties.tablePrefix(),
            logicalTableName
        );
    }

    /**
     * 使用明确指定的表前缀生成真实数据库表名。
     *
     * <p>这个静态入口主要供以下场景使用：</p>
     *
     * <ul>
     *     <li>安装向导尚未完成时；</li>
     *     <li>安装器正在使用临时数据库配置时；</li>
     *     <li>Flyway 动态迁移工厂创建历史表名时；</li>
     *     <li>数据库诊断和升级服务处理指定配置时。</li>
     * </ul>
     *
     * <p>该方法会同时校验：</p>
     *
     * <ol>
     *     <li>configuredPrefix；</li>
     *     <li>logicalTableName。</li>
     * </ol>
     *
     * <p>任何一项非法，都不会返回经过偷偷修复的表名。</p>
     *
     * @param configuredPrefix 用户配置或安装阶段指定的表前缀
     * @param logicalTableName 不包含表前缀的逻辑表名
     * @return 经过统一校验的真实数据库表名
     * @throws IllegalStateException 表前缀或逻辑表名非法时抛出
     */
    public static String tableName(
        String configuredPrefix,
        String logicalTableName
    ) {
        String safePrefix =
            normalizeConfiguredPrefix(
                configuredPrefix
            );

        String safeLogicalTableName =
            normalizeLogicalTableName(
                logicalTableName
            );

        return safePrefix
            + safeLogicalTableName;
    }

    /**
     * 获取当前运行配置中的安全数据库表前缀。
     *
     * @return 经过严格校验的当前表前缀
     * @throws IllegalStateException 当前表前缀非法时抛出
     */
    public String currentPrefix() {
        return normalizeConfiguredPrefix(
            properties.tablePrefix()
        );
    }

    /**
     * 兼容旧接口名称。
     *
     * <p>新代码优先使用 currentPrefix()。</p>
     *
     * @return 经过严格校验的当前表前缀
     */
    public String tablePrefix() {
        return currentPrefix();
    }

    /**
     * 严格校验用户配置的数据库表前缀。
     *
     * <p>该方法不会执行以下静默修复：</p>
     *
     * <ul>
     *     <li>不会自动 trim 非空配置；</li>
     *     <li>不会自动转成小写；</li>
     *     <li>不会删除非法字符；</li>
     *     <li>不会自动补充末尾下划线；</li>
     *     <li>不会把非法值替换成默认前缀。</li>
     * </ul>
     *
     * <p>null、空字符串和纯空白字符串代表用户没有配置，
     * 这种情况下使用默认前缀 aq_。</p>
     *
     * @param configuredPrefix 原始数据库表前缀配置
     * @return 合法表前缀
     * @throws IllegalStateException 非空配置不符合规则时抛出
     */
    public static String normalizeConfiguredPrefix(
        String configuredPrefix
    ) {
        /*
         * 没有配置前缀时使用 Aquafish 正式默认值。
         *
         * 纯空白属于“没有配置”，而不是非法自定义值。
         */
        if (
            configuredPrefix == null
                || configuredPrefix.isBlank()
        ) {
            return DEFAULT_TABLE_PREFIX;
        }

        /*
         * 非空配置存在前后空格时必须直接拒绝。
         *
         * 不能自动 trim：
         * 否则用户配置文件中的值和最终真实表名会不一致。
         */
        if (
            !configuredPrefix.equals(
                configuredPrefix.trim()
            )
        ) {
            throw invalidPrefix(
                configuredPrefix
            );
        }

        /*
         * 单独限制前缀长度。
         */
        if (
            configuredPrefix.length()
                > MAX_TABLE_PREFIX_LENGTH
        ) {
            throw new IllegalStateException(
                "数据库表前缀长度不能超过 "
                    + MAX_TABLE_PREFIX_LENGTH
                    + " 个字符。当前值："
                    + configuredPrefix
            );
        }

        /*
         * 严格检查：
         * 1. 小写字母开头；
         * 2. 只能包含小写字母、数字和下划线；
         * 3. 必须以下划线结尾。
         */
        if (
            !TABLE_PREFIX_PATTERN
                .matcher(configuredPrefix)
                .matches()
        ) {
            throw invalidPrefix(
                configuredPrefix
            );
        }

        return configuredPrefix;
    }

    /**
     * 规范化并校验源码中的逻辑表名。
     *
     * <p>逻辑表名来自项目源码，而不是用户输入，
     * 因此允许统一执行 trim 和小写转换。</p>
     *
     * <p>例如：</p>
     *
     * <pre>
     * " Users " -> "users"
     * "USER_ROLES" -> "user_roles"
     * </pre>
     *
     * <p>转换完成后仍然必须通过严格正则校验。</p>
     *
     * @param logicalTableName 原始逻辑表名
     * @return 规范化后的逻辑表名
     * @throws IllegalStateException 逻辑表名为空或非法时抛出
     */
    private static String normalizeLogicalTableName(
        String logicalTableName
    ) {
        if (
            logicalTableName == null
                || logicalTableName.isBlank()
        ) {
            throw new IllegalStateException(
                "逻辑表名不能为空。"
            );
        }

        String normalized =
            logicalTableName
                .trim()
                .toLowerCase(Locale.ROOT);

        if (
            !LOGICAL_TABLE_NAME_PATTERN
                .matcher(normalized)
                .matches()
        ) {
            throw new IllegalStateException(
                "逻辑表名非法，只能使用小写字母、数字、"
                    + "下划线，并且必须以小写字母开头。"
                    + "当前值："
                    + logicalTableName
            );
        }

        return normalized;
    }

    /**
     * 创建统一的非法数据库表前缀异常。
     *
     * @param configuredPrefix 原始非法配置
     * @return 包含规则说明的异常
     */
    private static IllegalStateException invalidPrefix(
        String configuredPrefix
    ) {
        return new IllegalStateException(
            "数据库表前缀非法。"
                + "只能使用小写字母、数字和下划线，"
                + "必须以小写字母开头并以下划线结尾，"
                + "不能包含前后空格。"
                + "合法示例：aq_、bbs_、site01_。"
                + "当前值："
                + configuredPrefix
        );
    }
}
