package com.aquafish.core.database;

/**
 * Aquafish 数据库连接配置。
 *
 * <p>这个 record 用于保存一整套数据库连接参数，主要服务于：</p>
 *
 * <ul>
 *     <li>安装向导提交数据库配置；</li>
 *     <li>数据库连接测试；</li>
 *     <li>生成 JDBC 和 R2DBC 连接地址；</li>
 *     <li>数据库结构初始化；</li>
 *     <li>后续 Flyway 数据库迁移；</li>
 *     <li>写入 workdir/application.yaml。</li>
 * </ul>
 *
 * <p>这个类只保存和标准化配置，不负责真正连接数据库。</p>
 *
 * <p>安全要求：</p>
 *
 * <ul>
 *     <li>password 不允许出现在接口测试结果和日志中；</li>
 *     <li>表前缀必须经过 TableNameResolver 的统一严格校验；</li>
 *     <li>不能在这里静默删除非法字符；</li>
 *     <li>不能自动修改用户填写的非法表前缀。</li>
 * </ul>
 *
 * @param type 数据库类型，目前支持 MySQL 和 PostgreSQL
 * @param host 数据库主机地址
 * @param port 数据库端口
 * @param name 数据库名称
 * @param username 数据库用户名
 * @param password 数据库密码
 * @param tablePrefix Aquafish 数据表前缀
 */
public record DatabaseSettings(
    DatabaseType type,
    String host,
    Integer port,
    String name,
    String username,
    String password,
    String tablePrefix
) {

    /**
     * 创建一份默认的 MySQL 数据库配置。
     *
     * <p>该配置主要用于：</p>
     *
     * <ul>
     *     <li>安装向导首次打开时展示默认值；</li>
     *     <li>测试代码创建标准 MySQL 配置；</li>
     *     <li>配置缺失时提供明确的基础结构。</li>
     * </ul>
     *
     * <p>这里的密码默认为空字符串，不会提供默认数据库密码。</p>
     *
     * @return 默认 MySQL 数据库配置
     */
    public static DatabaseSettings defaultMysql() {
        return new DatabaseSettings(
            DatabaseType.MYSQL,
            "127.0.0.1",
            3306,
            "aquafish",
            "aquafish",
            "",
            TableNameResolver.DEFAULT_TABLE_PREFIX
        );
    }

    /**
     * 创建一份默认的 PostgreSQL 数据库配置。
     *
     * <p>PostgreSQL 默认端口为 5432，其他默认值与 MySQL
     * 配置保持一致，方便安装向导在数据库类型切换时使用。</p>
     *
     * @return 默认 PostgreSQL 数据库配置
     */
    public static DatabaseSettings defaultPostgresql() {
        return new DatabaseSettings(
            DatabaseType.POSTGRESQL,
            "127.0.0.1",
            5432,
            "aquafish",
            "aquafish",
            "",
            TableNameResolver.DEFAULT_TABLE_PREFIX
        );
    }

    /**
     * 创建一份默认的 MariaDB 数据库配置。
     */
    public static DatabaseSettings defaultMariadb() {
        return new DatabaseSettings(
            DatabaseType.MARIADB,
            "127.0.0.1",
            3306,
            "aquafish",
            "aquafish",
            "",
            TableNameResolver.DEFAULT_TABLE_PREFIX
        );
    }

    /**
     * 返回一份经过标准化和安全校验的数据库配置。
     *
     * <p>该方法主要解决安装请求或配置文件中可能出现的 null、
     * 空字符串和无效端口问题。</p>
     *
     * <p>处理规则：</p>
     *
     * <ol>
     *     <li>数据库类型为空时，默认使用 MySQL；</li>
     *     <li>主机地址为空时，默认使用 127.0.0.1；</li>
     *     <li>端口为空或小于等于 0 时，使用对应数据库默认端口；</li>
     *     <li>数据库名称为空时，默认使用 aquafish；</li>
     *     <li>用户名为空时，默认使用 aquafish；</li>
     *     <li>密码为空时，转换为空字符串；</li>
     *     <li>表前缀交给 TableNameResolver 统一严格校验。</li>
     * </ol>
     *
     * <p>表前缀不会再执行以下旧行为：</p>
     *
     * <ul>
     *     <li>自动删除横线、空格或特殊字符；</li>
     *     <li>自动转换大小写；</li>
     *     <li>自动补充末尾下划线；</li>
     * </ul>
     *
     * <p>例如用户填写 {@code My-Site} 时，会直接抛出明确异常，
     * 不会偷偷变成 {@code MySite_} 或 {@code mysite_}。</p>
     *
     * @return 标准化后的新数据库配置
     * @throws IllegalStateException 当表前缀不符合统一规则时抛出
     */
    public DatabaseSettings normalized() {
        /*
         * 数据库类型为空时使用 MySQL。
         *
         * safeType 后面还用于获得对应数据库的默认端口。
         */
        DatabaseType safeType =
            type == null ? DatabaseType.MYSQL : type;

        return new DatabaseSettings(
            safeType,

            /*
             * host、数据库名和用户名属于普通文本配置。
             *
             * 对这些字段允许去除前后空格，
             * 因为空格不是其有效业务内容。
             */
            textOrDefault(host, "127.0.0.1"),

            /*
             * 未提供有效端口时，根据数据库类型使用默认端口：
             *
             * MySQL：3306
             * PostgreSQL：5432
             */
            port == null || port <= 0
                ? safeType.defaultPort()
                : port,

            textOrDefault(name, "aquafish"),
            textOrDefault(username, "aquafish"),

            /*
             * 密码不能 trim。
             *
             * 数据库密码可能合法地包含前导或尾随空格，
             * 所以这里只处理 null，不修改真实密码内容。
             */
            password == null ? "" : password,

            /*
             * 表前缀统一交给 TableNameResolver。
             *
             * 安装配置、运行配置、数据库初始化和 Flyway
             * 必须使用完全相同的校验规则。
             */
            TableNameResolver.normalizeConfiguredPrefix(tablePrefix)
        );
    }

    /**
     * 判断数据库配置是否包含建立连接所需的基本字段。
     *
     * <p>判断前会先调用 {@link #normalized()}，因此：</p>
     *
     * <ul>
     *     <li>空数据库类型会获得默认值；</li>
     *     <li>空主机、端口、数据库名和用户名会获得默认值；</li>
     *     <li>非法数据表前缀会直接抛出异常；</li>
     * </ul>
     *
     * <p>密码不在这里强制要求非空，因为本地数据库可能允许
     * 使用空密码进行开发或首次安装。</p>
     *
     * @return 基本连接字段完整时返回 true
     */
    public boolean hasRequiredFields() {
        DatabaseSettings safe = normalized();

        return !safe.host().isBlank()
            && safe.port() > 0
            && !safe.name().isBlank()
            && !safe.username().isBlank();
    }

    /**
     * 对普通文本配置进行基础标准化。
     *
     * <p>当配置为空时返回默认值；配置不为空时去除前后空格。</p>
     *
     * <p>该方法只适用于：</p>
     *
     * <ul>
     *     <li>数据库主机地址；</li>
     *     <li>数据库名称；</li>
     *     <li>数据库用户名。</li>
     * </ul>
     *
     * <p>不能用于密码和表前缀：</p>
     *
     * <ul>
     *     <li>密码中的空格可能是合法密码内容；</li>
     *     <li>表前缀中的空格必须明确报错，不能静默去除。</li>
     * </ul>
     *
     * @param value 原始文本
     * @param defaultValue 空值时使用的默认值
     * @return 处理后的文本
     */
    private static String textOrDefault(
        String value,
        String defaultValue
    ) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value.trim();
    }
}
