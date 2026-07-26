package com.aquafish.core.installation;

import java.util.Locale;

/**
 * Aquafish 持久化安装状态。
 *
 * <p>
 * 该状态最终由数据库 system_instances 单例记录保存。
 * workdir/install.lock 不再是状态机设计的一部分。
 * </p>
 */
public enum InstallationState {

    /**
     * 尚未开始正式初始化。
     */
    UNINITIALIZED,

    /**
     * 已经取得数据库初始化权，
     * 正在执行安装流程。
     */
    INITIALIZING,

    /**
     * Aquafish 已完成首次安装。
     */
    INSTALLED,

    /**
     * 当前初始化尝试失败，
     * 允许经过检查后重新开始。
     */
    FAILED;

    /**
     * 判断是否允许转换到目标状态。
     *
     * <p>
     * 同状态写入视为幂等操作。
     * </p>
     *
     * @param target 目标状态
     * @return 是否允许
     */
    public boolean allowsTransitionTo(
        InstallationState target
    ) {
        if (target == null) {
            return false;
        }

        if (this == target) {
            return true;
        }

        return switch (this) {
            case UNINITIALIZED ->
                target == INITIALIZING;

            case INITIALIZING ->
                target == INSTALLED
                    || target == FAILED;

            case FAILED ->
                target == INITIALIZING;

            case INSTALLED ->
                false;
        };
    }

    /**
     * 当前状态是否允许重新取得初始化权。
     *
     * @return 是否允许
     */
    public boolean canStartInitialization() {
        return this == UNINITIALIZED
            || this == FAILED;
    }

    /**
     * 当前状态是否表示系统已完成安装。
     *
     * @return 是否已安装
     */
    public boolean installed() {
        return this == INSTALLED;
    }

    /**
     * 从数据库字符串解析状态。
     *
     * @param value 数据库存储值
     * @return 安装状态
     */
    public static InstallationState
        fromDatabaseValue(
            String value
        ) {

        if (
            value == null
            || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                "数据库安装状态不能为空。"
            );
        }

        try {
            return valueOf(
                value
                    .trim()
                    .toUpperCase(
                        Locale.ROOT
                    )
            );
        } catch (
            IllegalArgumentException error
        ) {
            throw new IllegalArgumentException(
                "未知数据库安装状态："
                    + value,
                error
            );
        }
    }
}
