package com.aquafish.core.installation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aquafish 数据库系统实例记录。
 *
 * <p>
 * system_instances 表最多只能存在一条记录，
 * singleton_id 必须固定为 1。
 * </p>
 */
public record SystemInstallationRecord(
    short singletonId,
    UUID instanceId,
    InstallationState state,
    long stateVersion,
    UUID initializationAttemptId,
    Instant initializationStartedAt,
    Instant installedAt,
    String installedVersion,
    String lastErrorCode,
    String lastErrorMessage,
    Instant createdAt,
    Instant updatedAt
) {

    /**
     * 规范化并校验系统实例记录。
     */
    public SystemInstallationRecord {
        if (
            singletonId
                != SystemInstallationSchema
                    .PRIMARY_SINGLETON_ID
        ) {
            throw new IllegalArgumentException(
                "系统实例 singletonId 必须固定为 1。"
            );
        }

        Objects.requireNonNull(
            instanceId,
            "系统实例 ID 不能为空。"
        );

        Objects.requireNonNull(
            state,
            "系统安装状态不能为空。"
        );

        if (stateVersion < 0) {
            throw new IllegalArgumentException(
                "安装状态版本不能小于 0。"
            );
        }

        if (
            state == InstallationState
                .INITIALIZING
            && initializationAttemptId == null
        ) {
            throw new IllegalArgumentException(
                "INITIALIZING 状态必须包含初始化尝试 ID。"
            );
        }

        if (
            state == InstallationState
                .INITIALIZING
            && initializationStartedAt == null
        ) {
            throw new IllegalArgumentException(
                "INITIALIZING 状态必须包含初始化开始时间。"
            );
        }

        if (
            state == InstallationState
                .INSTALLED
            && installedAt == null
        ) {
            throw new IllegalArgumentException(
                "INSTALLED 状态必须包含安装完成时间。"
            );
        }

        if (
            state == InstallationState
                .INSTALLED
            && (
                installedVersion == null
                || installedVersion.isBlank()
            )
        ) {
            throw new IllegalArgumentException(
                "INSTALLED 状态必须包含安装版本。"
            );
        }

        installedVersion =
            normalizeNullable(
                installedVersion
            );

        lastErrorCode =
            normalizeNullable(
                lastErrorCode
            );

        lastErrorMessage =
            normalizeNullable(
                lastErrorMessage
            );
    }

    /**
     * 当前记录是否表示已完成安装。
     *
     * @return 是否已安装
     */
    public boolean installed() {
        return state.installed();
    }

    /**
     * 当前记录是否允许重新开始初始化。
     *
     * @return 是否允许
     */
    public boolean canStartInitialization() {
        return state
            .canStartInitialization();
    }

    /**
     * 标准化可空字符串。
     */
    private static String normalizeNullable(
        String value
    ) {
        if (
            value == null
            || value.isBlank()
        ) {
            return null;
        }

        return value.trim();
    }
}
