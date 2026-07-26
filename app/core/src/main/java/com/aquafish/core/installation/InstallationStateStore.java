package com.aquafish.core.installation;

import com.aquafish.core.database.DatabaseSettings;
import java.time.Instant;
import java.util.UUID;

/**
 * Aquafish 旧同步数据库安装状态存储契约。
 *
 * <p>
 * R3C 后正式业务服务只依赖 ReactiveInstallationStateStore。
 * 当前接口仅保留给尚未删除的 JDBC 历史实现和对应回归测试，
 * 将在 R7 清理剩余 JDBC 正式链路时一并删除。
 * </p>
 */
public interface InstallationStateStore {

    /**
     * 读取当前数据库安装状态。
     */
    InstallationStateSnapshot read(
        DatabaseSettings settings
    );

    /**
     * 原子尝试取得首次安装初始化权。
     */
    InitializationClaim tryStartInitialization(
        DatabaseSettings settings,
        UUID attemptId,
        Instant startedAt
    );

    /**
     * 把当前初始化尝试标记为安装完成。
     */
    SystemInstallationRecord markInstalled(
        DatabaseSettings settings,
        UUID attemptId,
        Instant installedAt,
        String installedVersion
    );

    /**
     * 把当前初始化尝试标记为失败。
     */
    SystemInstallationRecord markFailed(
        DatabaseSettings settings,
        UUID attemptId,
        Instant failedAt,
        String errorCode,
        String errorMessage
    );
}
