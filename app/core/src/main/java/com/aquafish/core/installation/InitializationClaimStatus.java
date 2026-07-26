package com.aquafish.core.installation;

/**
 * 首次安装初始化权抢占结果。
 */
public enum InitializationClaimStatus {

    /**
     * 当前请求成功取得初始化权。
     */
    ACQUIRED,

    /**
     * 已经有其他初始化尝试正在运行。
     */
    ALREADY_INITIALIZING,

    /**
     * 系统已经完成安装。
     */
    ALREADY_INSTALLED
}
