package com.aquafish.core.installation;

/**
 * 数据库安装状态读取结果。
 */
public enum InstallationStateReadStatus {

    /**
     * 已找到 system_instances 单例记录。
     */
    RECORD_FOUND,

    /**
     * system_instances 表存在，
     * 但尚未创建单例记录。
     */
    RECORD_ABSENT,

    /**
     * V4 system_instances 表尚未创建。
     */
    TABLE_MISSING,

    /**
     * 数据库无法连接或查询失败。
     */
    DATABASE_UNAVAILABLE,

    /**
     * 数据库记录存在，但字段内容不合法。
     */
    INVALID_RECORD
}
