package com.aquafish.core.installation;

import com.aquafish.core.database.DatabaseSettings;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 当前 Aquafish 首次安装尝试会话。
 *
 * <p>
 * 数据库 system_instances 记录始终是最终事实来源。
 * 当前类只缓存当前进程正在使用的 attemptId，
 * 所有状态确认都通过响应式服务重新读取数据库。
 * </p>
 */
@Service
public class InstallationAttemptSession {

    private final InstallationStateService
        installationStateService;

    private final AtomicReference<UUID>
        cachedAttemptId =
            new AtomicReference<>();

    public InstallationAttemptSession(
        InstallationStateService
            installationStateService
    ) {
        this.installationStateService =
            Objects.requireNonNull(
                installationStateService,
                "数据库安装状态服务不能为空。"
            );
    }

    /**
     * 数据库迁移完成后取得或恢复初始化权。
     */
    public Mono<InitializationClaim> claimAfterMigration(
        DatabaseSettings settings
    ) {
        return Mono.defer(
            () -> {
                UUID proposedAttemptId =
                    UUID.randomUUID();

                return installationStateService
                    .tryStartInitialization(
                        settings,
                        proposedAttemptId,
                        Instant.now()
                    )
                    .map(
                        this::rememberClaim
                    )
                    .doOnError(
                        error ->
                            cachedAttemptId.set(null)
                    );
            }
        );
    }

    /**
     * 从数据库恢复并返回当前 INITIALIZING 尝试。
     *
     * <p>每次订阅都会读取数据库，不能只相信进程内缓存。</p>
     */
    public Mono<UUID> requireCurrentAttemptId() {
        return Mono.defer(
            () ->
                installationStateService
                    .current()
                    .map(
                        this::resolveCurrentAttemptId
                    )
        ).doOnError(
            error ->
                cachedAttemptId.set(null)
        );
    }

    /**
     * 查看当前进程中的缓存值，仅用于诊断和测试。
     */
    public Optional<UUID> cachedAttemptId() {
        return Optional.ofNullable(
            cachedAttemptId.get()
        );
    }

    /**
     * 当安装完成或失败流程结束时清除匹配缓存。
     */
    public void clear(
        UUID attemptId
    ) {
        if (attemptId == null) {
            return;
        }

        cachedAttemptId.compareAndSet(
            attemptId,
            null
        );
    }

    private InitializationClaim rememberClaim(
        InitializationClaim claim
    ) {
        if (claim == null) {
            throw new InstallationStateConflictException(
                "数据库安装状态没有返回初始化结果。"
            );
        }

        if (
            claim.status()
                == InitializationClaimStatus.ACQUIRED
            || claim.status()
                == InitializationClaimStatus
                    .ALREADY_INITIALIZING
        ) {
            remember(
                claim.attemptId()
            );
        } else {
            cachedAttemptId.set(null);
        }

        return claim;
    }

    private UUID resolveCurrentAttemptId(
        InstallationStateSnapshot snapshot
    ) {
        if (
            snapshot.status()
                == InstallationStateReadStatus
                    .RECORD_FOUND
        ) {
            SystemInstallationRecord record =
                snapshot
                    .recordOptional()
                    .orElseThrow();

            if (
                record.state()
                    == InstallationState
                        .INITIALIZING
                && record
                    .initializationAttemptId()
                    != null
            ) {
                remember(
                    record
                        .initializationAttemptId()
                );

                return record
                    .initializationAttemptId();
            }

            if (record.installed()) {
                cachedAttemptId.set(null);

                throw new InstallationStateConflictException(
                    "Aquafish 已经完成首次安装。"
                );
            }

            throw new InstallationStateConflictException(
                "当前数据库安装状态没有有效初始化尝试："
                    + record.state()
            );
        }

        throw switch (snapshot.status()) {
            case RECORD_ABSENT ->
                new InstallationStateConflictException(
                    "尚未建立首次安装初始化记录，"
                        + "请先完成数据库迁移。"
                );

            case TABLE_MISSING ->
                new InstallationStateConflictException(
                    "数据库安装状态表尚未创建，"
                        + "请先执行 V4 数据库迁移。"
                );

            case DATABASE_UNAVAILABLE ->
                new IllegalStateException(
                    "数据库暂时不可用，"
                        + "无法确认当前初始化尝试。"
                );

            case INVALID_RECORD ->
                new InstallationStateConflictException(
                    "数据库安装状态记录无效，"
                        + "拒绝继续安装。"
                );

            case RECORD_FOUND ->
                new IllegalStateException(
                    "未能解析当前数据库安装状态。"
                );
        };
    }

    private void remember(
        UUID attemptId
    ) {
        if (attemptId == null) {
            throw new InstallationStateConflictException(
                "数据库初始化状态缺少 attemptId。"
            );
        }

        cachedAttemptId.set(
            attemptId
        );
    }
}
