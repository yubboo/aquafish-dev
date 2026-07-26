package com.aquafish.core.installation.r2dbc;

import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.installation.InitializationClaim;
import com.aquafish.core.installation.InstallationStateSnapshot;
import com.aquafish.core.installation.SystemInstallationRecord;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Aquafish 响应式数据库安装状态仓库。
 *
 * <p>
 * 所有数据库访问必须：
 * </p>
 *
 * <ul>
 *     <li>通过 R2DBC ConnectionFactory；</li>
 *     <li>返回 Mono；</li>
 *     <li>不在调用线程中阻塞；</li>
 *     <li>不依赖进程内缓存作为最终事实来源；</li>
 *     <li>写操作保留状态版本和 attemptId 并发保护。</li>
 * </ul>
 */
public interface ReactiveInstallationStateStore {

    /**
     * 读取当前数据库安装状态。
     */
    Mono<InstallationStateSnapshot> read(
        DatabaseSettings settings
    );

    /**
     * 原子尝试取得首次安装初始化权。
     */
    Mono<InitializationClaim>
        tryStartInitialization(
            DatabaseSettings settings,
            UUID attemptId,
            Instant startedAt
        );

    /**
     * 把匹配的初始化尝试推进到 INSTALLED。
     */
    Mono<SystemInstallationRecord>
        markInstalled(
            DatabaseSettings settings,
            UUID attemptId,
            Instant installedAt,
            String installedVersion
        );

    /**
     * 把匹配的初始化尝试推进到 FAILED。
     */
    Mono<SystemInstallationRecord>
        markFailed(
            DatabaseSettings settings,
            UUID attemptId,
            Instant failedAt,
            String errorCode,
            String errorMessage
        );
}
