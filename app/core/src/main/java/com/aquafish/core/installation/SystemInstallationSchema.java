package com.aquafish.core.installation;

/**
 * Aquafish 系统安装状态表公共常量。
 */
public final class SystemInstallationSchema {

    /**
     * system_instances 逻辑表名。
     *
     * <p>
     * 真实表名由 TableNameResolver 加上用户配置前缀。
     * </p>
     */
    public static final String
        LOGICAL_TABLE_NAME =
            "system_instances";

    /**
     * 唯一允许的单例主键。
     */
    public static final short
        PRIMARY_SINGLETON_ID = 1;

    /**
     * 当前开发阶段安装版本。
     *
     * <p>
     * 后续发行版本应改为统一版本提供器，
     * 不能长期在多个服务中重复硬编码。
     * </p>
     */
    public static final String
        CURRENT_INSTALLATION_VERSION =
            "0.0.1-dev";

    /**
     * 工具类不允许实例化。
     */
    private SystemInstallationSchema() {
    }
}
