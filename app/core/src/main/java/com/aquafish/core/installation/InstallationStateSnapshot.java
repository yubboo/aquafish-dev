package com.aquafish.core.installation;

import java.util.Optional;

/**
 * Aquafish 数据库安装状态快照。
 *
 * <p>
 * 该模型明确区分：
 * </p>
 *
 * <ul>
 *     <li>真正没有安装记录；</li>
 *     <li>V4 表尚未创建；</li>
 *     <li>数据库无法访问；</li>
 *     <li>数据库记录损坏。</li>
 * </ul>
 *
 * <p>
 * 数据库异常绝不能被静默解释成“尚未安装”，
 * 否则可能错误开放首次安装接口。
 * </p>
 */
public record InstallationStateSnapshot(
    InstallationStateReadStatus status,
    SystemInstallationRecord record,
    String safeErrorCode,
    String safeMessage
) {

    /**
     * 校验快照一致性。
     */
    public InstallationStateSnapshot {
        if (status == null) {
            throw new IllegalArgumentException(
                "安装状态读取结果不能为空。"
            );
        }

        if (
            status
                == InstallationStateReadStatus
                    .RECORD_FOUND
            && record == null
        ) {
            throw new IllegalArgumentException(
                "RECORD_FOUND 必须包含系统实例记录。"
            );
        }

        if (
            status
                != InstallationStateReadStatus
                    .RECORD_FOUND
            && record != null
        ) {
            throw new IllegalArgumentException(
                "非 RECORD_FOUND 结果不能包含系统实例记录。"
            );
        }

        safeErrorCode =
            normalizeNullable(
                safeErrorCode
            );

        safeMessage =
            normalizeNullable(
                safeMessage
            );
    }

    /**
     * 创建找到记录的快照。
     */
    public static InstallationStateSnapshot
        found(
            SystemInstallationRecord record
        ) {

        if (record == null) {
            throw new IllegalArgumentException(
                "系统实例记录不能为空。"
            );
        }

        return new InstallationStateSnapshot(
            InstallationStateReadStatus
                .RECORD_FOUND,
            record,
            null,
            null
        );
    }

    /**
     * 创建表存在但没有记录的快照。
     */
    public static InstallationStateSnapshot
        absent() {

        return new InstallationStateSnapshot(
            InstallationStateReadStatus
                .RECORD_ABSENT,
            null,
            null,
            null
        );
    }

    /**
     * 创建状态表尚未迁移的快照。
     */
    public static InstallationStateSnapshot
        tableMissing() {

        return new InstallationStateSnapshot(
            InstallationStateReadStatus
                .TABLE_MISSING,
            null,
            "INSTALLATION_STATE_TABLE_MISSING",
            "数据库安装状态表尚未创建。"
        );
    }

    /**
     * 创建数据库不可用快照。
     */
    public static InstallationStateSnapshot
        databaseUnavailable(
            String safeMessage
        ) {

        return new InstallationStateSnapshot(
            InstallationStateReadStatus
                .DATABASE_UNAVAILABLE,
            null,
            "INSTALLATION_STATE_DATABASE_UNAVAILABLE",
            safeMessage
        );
    }

    /**
     * 创建非法数据库记录快照。
     */
    public static InstallationStateSnapshot
        invalidRecord(
            String safeMessage
        ) {

        return new InstallationStateSnapshot(
            InstallationStateReadStatus
                .INVALID_RECORD,
            null,
            "INSTALLATION_STATE_INVALID_RECORD",
            safeMessage
        );
    }

    /**
     * 可选获取系统实例记录。
     */
    public Optional<SystemInstallationRecord>
        recordOptional() {

        return Optional.ofNullable(
            record
        );
    }

    /**
     * 当前是否已经完成首次安装。
     */
    public boolean installed() {
        return record != null
            && record.installed();
    }

    /**
     * 当前是否允许尝试取得初始化权。
     *
     * <p>
     * 只有以下两种情况允许：
     * </p>
     *
     * <ul>
     *     <li>表存在但没有记录；</li>
     *     <li>记录状态为 UNINITIALIZED 或 FAILED。</li>
     * </ul>
     */
    public boolean canStartInitialization() {
        if (
            status
                == InstallationStateReadStatus
                    .RECORD_ABSENT
        ) {
            return true;
        }

        return record != null
            && record
                .canStartInitialization();
    }

    /**
     * 当前结果是否来自已经存在的状态表。
     */
    public boolean schemaReady() {
        return status
                == InstallationStateReadStatus
                    .RECORD_FOUND
            || status
                == InstallationStateReadStatus
                    .RECORD_ABSENT;
    }

    /**
     * 当前读取结果是否可以作为安装状态事实来源。
     */
    public boolean authoritative() {
        return schemaReady();
    }

    /**
     * 数据库是否至少已经成功响应查询。
     */
    public boolean databaseReachable() {
        return status
                != InstallationStateReadStatus
                    .DATABASE_UNAVAILABLE;
    }

    /**
     * 标准化可空文本。
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
