package com.aquafish.core.install;

/**
 * 安装器对目标数据库的安全识别状态。
 */
public enum SetupDatabaseMode {

    /**
     * 当前前缀下没有 Aquafish 表，可以首次安装。
     */
    NEW_INSTALL,

    /**
     * 已经完整安装，只能恢复当前电脑配置。
     */
    EXISTING_INSTALLED,

    /**
     * 存在部分表或未完成的安装状态。
     */
    INCOMPLETE_INSTALLATION,

    /**
     * 迁移历史异常、数据库版本超前或记录损坏。
     */
    INCOMPATIBLE_DATABASE,

    /**
     * 无法可靠读取数据库状态。
     */
    STATE_UNAVAILABLE
}
