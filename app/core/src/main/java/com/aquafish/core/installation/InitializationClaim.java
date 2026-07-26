package com.aquafish.core.installation;

import java.util.Objects;
import java.util.UUID;

/**
 * Aquafish 首次安装初始化权。
 */
public record InitializationClaim(
    InitializationClaimStatus status,
    UUID attemptId,
    SystemInstallationRecord record,
    String message
) {

    /**
     * 校验抢占结果。
     */
    public InitializationClaim {
        Objects.requireNonNull(
            status,
            "初始化权状态不能为空。"
        );

        Objects.requireNonNull(
            record,
            "初始化权结果必须包含系统实例记录。"
        );

        if (
            status
                == InitializationClaimStatus
                    .ACQUIRED
            && (
                attemptId == null
                || record.state()
                    != InstallationState
                        .INITIALIZING
                || !attemptId.equals(
                    record
                        .initializationAttemptId()
                )
            )
        ) {
            throw new IllegalArgumentException(
                "ACQUIRED 必须包含匹配的 INITIALIZING 尝试 ID。"
            );
        }

        if (
            status
                == InitializationClaimStatus
                    .ALREADY_INITIALIZING
            && record.state()
                != InstallationState
                    .INITIALIZING
        ) {
            throw new IllegalArgumentException(
                "ALREADY_INITIALIZING 必须对应 INITIALIZING 记录。"
            );
        }

        if (
            status
                == InitializationClaimStatus
                    .ALREADY_INSTALLED
            && record.state()
                != InstallationState
                    .INSTALLED
        ) {
            throw new IllegalArgumentException(
                "ALREADY_INSTALLED 必须对应 INSTALLED 记录。"
            );
        }

        if (
            message == null
            || message.isBlank()
        ) {
            message = defaultMessage(
                status
            );
        } else {
            message = message.trim();
        }
    }

    /**
     * 当前请求是否取得初始化权。
     */
    public boolean acquired() {
        return status
            == InitializationClaimStatus
                .ACQUIRED;
    }

    /**
     * 创建成功抢占结果。
     */
    public static InitializationClaim acquired(
        UUID attemptId,
        SystemInstallationRecord record
    ) {
        return new InitializationClaim(
            InitializationClaimStatus.ACQUIRED,
            attemptId,
            record,
            "已取得 Aquafish 首次安装初始化权。"
        );
    }

    /**
     * 创建已有初始化任务结果。
     */
    public static InitializationClaim
        alreadyInitializing(
            SystemInstallationRecord record
        ) {

        return new InitializationClaim(
            InitializationClaimStatus
                .ALREADY_INITIALIZING,
            record.initializationAttemptId(),
            record,
            "已经有其他 Aquafish 初始化任务运行。"
        );
    }

    /**
     * 创建已经完成安装结果。
     */
    public static InitializationClaim
        alreadyInstalled(
            SystemInstallationRecord record
        ) {

        return new InitializationClaim(
            InitializationClaimStatus
                .ALREADY_INSTALLED,
            record.initializationAttemptId(),
            record,
            "Aquafish 已经完成首次安装。"
        );
    }

    /**
     * 默认提示。
     */
    private static String defaultMessage(
        InitializationClaimStatus status
    ) {
        return switch (status) {
            case ACQUIRED ->
                "已取得初始化权。";

            case ALREADY_INITIALIZING ->
                "已经有初始化任务运行。";

            case ALREADY_INSTALLED ->
                "系统已经完成安装。";
        };
    }
}
