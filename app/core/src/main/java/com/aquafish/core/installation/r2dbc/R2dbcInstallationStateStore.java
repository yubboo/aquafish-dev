package com.aquafish.core.installation.r2dbc;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.r2dbc.RuntimeR2dbcConnectionFactory;
import com.aquafish.core.installation.InitializationClaim;
import com.aquafish.core.installation.InstallationState;
import com.aquafish.core.installation.InstallationStateConflictException;
import com.aquafish.core.installation.InstallationStateSnapshot;
import com.aquafish.core.installation.SystemInstallationRecord;
import com.aquafish.core.installation.SystemInstallationSchema;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * Aquafish R2DBC 安装状态仓库。
 *
 * <p>
 * 当前阶段实现：
 * </p>
 *
 * <ul>
 *     <li>响应式读取 system_instances；</li>
 *     <li>识别状态表不存在；</li>
 *     <li>首次创建 INITIALIZING；</li>
 *     <li>恢复已有 INITIALIZING；</li>
 *     <li>从 FAILED 或 UNINITIALIZED 重新开始；</li>
 *     <li>拒绝已经完成的首次安装；</li>
 *     <li>并发首次插入冲突后读取真实赢家；</li>
 *     <li>使用 attemptId 与 stateVersion 推进 INSTALLED / FAILED；</li>
 *     <li>相同尝试重复提交时保持幂等。</li>
 * </ul>
 *
 * <p>
 * 该实现是正式安装状态仓库，由 Spring 容器统一注册。
 * </p>
 */
@Repository
public class R2dbcInstallationStateStore
    implements ReactiveInstallationStateStore {

    private final DatabaseRuntimeSettingsService
        settingsService;

    private final RuntimeR2dbcConnectionFactory
        connectionFactory;

    public R2dbcInstallationStateStore(
        DatabaseRuntimeSettingsService settingsService,
        RuntimeR2dbcConnectionFactory connectionFactory
    ) {
        this.settingsService =
            Objects.requireNonNull(
                settingsService,
                "数据库运行配置服务不能为空。"
            );

        this.connectionFactory =
            Objects.requireNonNull(
                connectionFactory,
                "运行时 R2DBC 连接工厂不能为空。"
            );
    }

    /**
     * 响应式读取当前数据库安装状态。
     */
    @Override
    public Mono<InstallationStateSnapshot> read(
        DatabaseSettings settings
    ) {
        return Mono.defer(
            () -> {
                OperationContext context =
                    context(settings);

                return tableExists(context)
                    .flatMap(
                        exists -> {
                            if (!exists) {
                                return Mono.just(
                                    InstallationStateSnapshot
                                        .tableMissing()
                                );
                            }

                            return selectCurrent(
                                context,
                                false
                            )
                                .map(
                                    InstallationStateSnapshot
                                        ::found
                                )
                                .defaultIfEmpty(
                                    InstallationStateSnapshot
                                        .absent()
                                );
                        }
                    )
                    .onErrorResume(
                        InvalidInstallationRecordException
                            .class,
                        error ->
                            Mono.just(
                                InstallationStateSnapshot
                                    .invalidRecord(
                                        "数据库安装状态记录格式无效。"
                                    )
                            )
                    )
                    .onErrorResume(
                        error ->
                            Mono.just(
                                InstallationStateSnapshot
                                    .databaseUnavailable(
                                        "数据库暂时不可用，"
                                            + "无法读取安装状态。"
                                    )
                            )
                    );
            }
        );
    }

    /**
     * 原子取得或恢复 INITIALIZING 初始化权。
     */
    @Override
    public Mono<InitializationClaim>
        tryStartInitialization(
            DatabaseSettings settings,
            UUID attemptId,
            Instant startedAt
        ) {

        return Mono.defer(
            () -> {
                OperationContext context =
                    context(settings);

                UUID safeAttemptId =
                    Objects.requireNonNull(
                        attemptId,
                        "初始化尝试 ID 不能为空。"
                    );

                Instant safeStartedAt =
                    Objects.requireNonNull(
                        startedAt,
                        "初始化开始时间不能为空。"
                    );

                Mono<InitializationClaim>
                    transactionalAction =
                    tableExists(context)
                        .flatMap(
                            exists -> {
                                if (!exists) {
                                    return Mono.error(
                                        new InstallationStateConflictException(
                                            "数据库安装状态表尚未创建，"
                                                + "请先执行 V4 数据库迁移。"
                                        )
                                    );
                                }

                                return selectCurrent(
                                    context,
                                    true
                                )
                                    .flatMap(
                                        current ->
                                            claimExisting(
                                                context,
                                                current,
                                                safeAttemptId,
                                                safeStartedAt
                                            )
                                    )
                                    .switchIfEmpty(
                                        Mono.defer(
                                            () ->
                                                insertFirstInitialization(
                                                    context,
                                                    safeAttemptId,
                                                    safeStartedAt
                                                )
                                        )
                                    );
                            }
                        );

                return context
                    .transactions()
                    .transactional(
                        transactionalAction
                    )
                    .onErrorResume(
                        R2dbcInstallationStateStore
                            ::isDuplicateKey,
                        error ->
                            claimAfterConcurrentInsert(
                                context
                            )
                    )
                    .onErrorMap(
                        error -> {
                            if (
                                error
                                    instanceof
                                    InstallationStateConflictException
                                || error
                                    instanceof
                                    IllegalStateException
                            ) {
                                return error;
                            }

                            return new IllegalStateException(
                                "取得首次安装初始化权失败。",
                                error
                            );
                        }
                    );
            }
        );
    }

    /**
     * 把匹配的 INITIALIZING 尝试推进到 INSTALLED。
     */
    @Override
    public Mono<SystemInstallationRecord>
        markInstalled(
            DatabaseSettings settings,
            UUID attemptId,
            Instant installedAt,
            String installedVersion
        ) {

        return Mono.defer(
            () -> {
                UUID safeAttemptId =
                    Objects.requireNonNull(
                        attemptId,
                        "初始化尝试 ID 不能为空。"
                    );

                Instant safeInstalledAt =
                    Objects.requireNonNull(
                        installedAt,
                        "安装完成时间不能为空。"
                    );

                String safeInstalledVersion =
                    requireInstalledVersion(
                        installedVersion
                    );

                OperationContext context =
                    context(settings);

                Mono<SystemInstallationRecord> action =
                    lockedCurrent(
                        context,
                        "数据库安装状态记录不存在，无法标记安装完成。"
                    )
                        .flatMap(
                            current -> {
                                SystemInstallationRecord next =
                                    installedRecord(
                                        current,
                                        safeAttemptId,
                                        safeInstalledAt,
                                        safeInstalledVersion
                                    );

                                if (next == current) {
                                    return Mono.just(current);
                                }

                                return updateToInstalled(
                                    context,
                                    current,
                                    next
                                )
                                    .flatMap(
                                        updated ->
                                            requireSingleUpdate(
                                                updated,
                                                next,
                                                "安装状态已被其他请求修改，未能标记安装完成。"
                                            )
                                    );
                            }
                        );

                return context
                    .transactions()
                    .transactional(action)
                    .onErrorMap(
                        error ->
                            mapTransitionError(
                                error,
                                "标记首次安装完成失败。"
                            )
                    );
            }
        );
    }

    /**
     * 把匹配的 INITIALIZING 尝试推进到 FAILED。
     */
    @Override
    public Mono<SystemInstallationRecord>
        markFailed(
            DatabaseSettings settings,
            UUID attemptId,
            Instant failedAt,
            String errorCode,
            String errorMessage
        ) {

        return Mono.defer(
            () -> {
                UUID safeAttemptId =
                    Objects.requireNonNull(
                        attemptId,
                        "初始化尝试 ID 不能为空。"
                    );

                Instant safeFailedAt =
                    Objects.requireNonNull(
                        failedAt,
                        "安装失败时间不能为空。"
                    );

                OperationContext context =
                    context(settings);

                Mono<SystemInstallationRecord> action =
                    lockedCurrent(
                        context,
                        "数据库安装状态记录不存在，无法标记安装失败。"
                    )
                        .flatMap(
                            current -> {
                                SystemInstallationRecord next =
                                    failedRecord(
                                        current,
                                        safeAttemptId,
                                        safeFailedAt,
                                        errorCode,
                                        errorMessage
                                    );

                                if (next == current) {
                                    return Mono.just(current);
                                }

                                return updateToFailed(
                                    context,
                                    current,
                                    next
                                )
                                    .flatMap(
                                        updated ->
                                            requireSingleUpdate(
                                                updated,
                                                next,
                                                "安装状态已被其他请求修改，未能标记安装失败。"
                                            )
                                    );
                            }
                        );

                return context
                    .transactions()
                    .transactional(action)
                    .onErrorMap(
                        error ->
                            mapTransitionError(
                                error,
                                "标记首次安装失败状态失败。"
                            )
                    );
            }
        );
    }

    /**
     * 在事务内锁定并读取单例安装状态。
     */
    private Mono<SystemInstallationRecord> lockedCurrent(
        OperationContext context,
        String missingMessage
    ) {
        return tableExists(context)
            .flatMap(
                exists -> {
                    if (!exists) {
                        return Mono.error(
                            new InstallationStateConflictException(
                                "数据库安装状态表尚未创建，请先执行 V4 数据库迁移。"
                            )
                        );
                    }

                    return selectCurrent(
                        context,
                        true
                    )
                        .switchIfEmpty(
                            Mono.error(
                                new InstallationStateConflictException(
                                    missingMessage
                                )
                            )
                        );
                }
            );
    }

    /**
     * 根据当前记录构造 INSTALLED，或返回同一尝试的幂等结果。
     */
    static SystemInstallationRecord installedRecord(
        SystemInstallationRecord current,
        UUID attemptId,
        Instant installedAt,
        String installedVersion
    ) {
        SystemInstallationRecord safeCurrent =
            Objects.requireNonNull(
                current,
                "当前安装状态记录不能为空。"
            );

        UUID safeAttemptId =
            Objects.requireNonNull(
                attemptId,
                "初始化尝试 ID 不能为空。"
            );

        Instant safeInstalledAt =
            Objects.requireNonNull(
                installedAt,
                "安装完成时间不能为空。"
            );

        String safeVersion =
            requireInstalledVersion(
                installedVersion
            );

        if (safeCurrent.state() == InstallationState.INSTALLED) {
            requireMatchingAttempt(
                safeCurrent,
                safeAttemptId,
                "当前系统已由其他初始化尝试完成安装。"
            );

            return safeCurrent;
        }

        requireInitializingAttempt(
            safeCurrent,
            safeAttemptId,
            "只有匹配的 INITIALIZING 尝试才能标记安装完成。"
        );

        requireCompletionTime(
            safeCurrent,
            safeInstalledAt,
            "安装完成时间不能早于初始化开始时间。"
        );

        return new SystemInstallationRecord(
            safeCurrent.singletonId(),
            safeCurrent.instanceId(),
            InstallationState.INSTALLED,
            nextStateVersion(safeCurrent),
            safeAttemptId,
            safeCurrent.initializationStartedAt(),
            safeInstalledAt,
            safeVersion,
            null,
            null,
            safeCurrent.createdAt(),
            safeInstalledAt
        );
    }

    /**
     * 根据当前记录构造 FAILED，或返回同一尝试的幂等结果。
     */
    static SystemInstallationRecord failedRecord(
        SystemInstallationRecord current,
        UUID attemptId,
        Instant failedAt,
        String errorCode,
        String errorMessage
    ) {
        SystemInstallationRecord safeCurrent =
            Objects.requireNonNull(
                current,
                "当前安装状态记录不能为空。"
            );

        UUID safeAttemptId =
            Objects.requireNonNull(
                attemptId,
                "初始化尝试 ID 不能为空。"
            );

        Instant safeFailedAt =
            Objects.requireNonNull(
                failedAt,
                "安装失败时间不能为空。"
            );

        if (safeCurrent.state() == InstallationState.FAILED) {
            requireMatchingAttempt(
                safeCurrent,
                safeAttemptId,
                "当前失败状态属于其他初始化尝试。"
            );

            return safeCurrent;
        }

        requireInitializingAttempt(
            safeCurrent,
            safeAttemptId,
            "只有匹配的 INITIALIZING 尝试才能标记安装失败。"
        );

        requireCompletionTime(
            safeCurrent,
            safeFailedAt,
            "安装失败时间不能早于初始化开始时间。"
        );

        return new SystemInstallationRecord(
            safeCurrent.singletonId(),
            safeCurrent.instanceId(),
            InstallationState.FAILED,
            nextStateVersion(safeCurrent),
            safeAttemptId,
            safeCurrent.initializationStartedAt(),
            null,
            null,
            InstallationFailureSanitizer
                .sanitizeCode(errorCode),
            InstallationFailureSanitizer
                .sanitizeMessage(errorMessage),
            safeCurrent.createdAt(),
            safeFailedAt
        );
    }

    private static void requireInitializingAttempt(
        SystemInstallationRecord current,
        UUID attemptId,
        String message
    ) {
        if (current.state() != InstallationState.INITIALIZING) {
            throw new InstallationStateConflictException(
                message + " 当前状态：" + current.state()
            );
        }

        requireMatchingAttempt(
            current,
            attemptId,
            message
        );
    }

    private static void requireMatchingAttempt(
        SystemInstallationRecord current,
        UUID attemptId,
        String message
    ) {
        if (!Objects.equals(
            current.initializationAttemptId(),
            attemptId
        )) {
            throw new InstallationStateConflictException(
                message
            );
        }
    }

    private static void requireCompletionTime(
        SystemInstallationRecord current,
        Instant completedAt,
        String message
    ) {
        Instant startedAt =
            current.initializationStartedAt();

        if (
            startedAt != null
            && completedAt.isBefore(startedAt)
        ) {
            throw new IllegalArgumentException(
                message
            );
        }
    }

    private static long nextStateVersion(
        SystemInstallationRecord current
    ) {
        if (current.stateVersion() == Long.MAX_VALUE) {
            throw new InstallationStateConflictException(
                "数据库安装状态版本已经达到上限。"
            );
        }

        return current.stateVersion() + 1L;
    }

    private static String requireInstalledVersion(
        String installedVersion
    ) {
        if (
            installedVersion == null
            || installedVersion.isBlank()
        ) {
            throw new IllegalArgumentException(
                "安装版本不能为空。"
            );
        }

        String normalized =
            installedVersion.trim();

        if (normalized.length() > 64) {
            throw new IllegalArgumentException(
                "安装版本长度不能超过 64 个字符。"
            );
        }

        return normalized;
    }

    private static Mono<SystemInstallationRecord>
        requireSingleUpdate(
            Long updated,
            SystemInstallationRecord result,
            String conflictMessage
        ) {

        if (updated == null || updated.longValue() != 1L) {
            return Mono.error(
                new InstallationStateConflictException(
                    conflictMessage
                )
            );
        }

        return Mono.just(result);
    }

    private static Throwable mapTransitionError(
        Throwable error,
        String operationMessage
    ) {
        if (
            error instanceof InstallationStateConflictException
            || error instanceof IllegalArgumentException
            || error instanceof NullPointerException
        ) {
            return error;
        }

        return new IllegalStateException(
            operationMessage,
            error
        );
    }

    /**
     * 根据现有记录返回稳定状态，
     * 或重新推进到 INITIALIZING。
     */
    private Mono<InitializationClaim> claimExisting(
        OperationContext context,
        SystemInstallationRecord current,
        UUID attemptId,
        Instant startedAt
    ) {
        InitializationClaim stableClaim =
            stableClaim(current);

        if (stableClaim != null) {
            return Mono.just(
                stableClaim
            );
        }

        if (
            !current
                .canStartInitialization()
        ) {
            return Mono.error(
                new InstallationStateConflictException(
                    "当前数据库安装状态不允许重新初始化："
                        + current.state()
                )
            );
        }

        SystemInstallationRecord restarted =
            restartInitializingRecord(
                current,
                attemptId,
                startedAt
            );

        return updateToInitializing(
            context,
            current,
            restarted
        )
            .flatMap(
                updated -> {
                    if (
                        updated == null
                        || updated.longValue() != 1L
                    ) {
                        return Mono.error(
                            new InstallationStateConflictException(
                                "数据库安装状态已被其他请求修改，"
                                    + "未能取得初始化权。"
                            )
                        );
                    }

                    return Mono.just(
                        InitializationClaim
                            .acquired(
                                attemptId,
                                restarted
                            )
                    );
                }
            );
    }

    /**
     * 创建第一条 INITIALIZING 单例记录。
     */
    private Mono<InitializationClaim>
        insertFirstInitialization(
            OperationContext context,
            UUID attemptId,
            Instant startedAt
        ) {

        SystemInstallationRecord created =
            newInitializingRecord(
                attemptId,
                startedAt
            );

        return insertRecord(
            context,
            created
        )
            .flatMap(
                inserted -> {
                    if (
                        inserted == null
                        || inserted.longValue() != 1L
                    ) {
                        return Mono.error(
                            new InstallationStateConflictException(
                                "未能创建数据库安装状态记录。"
                            )
                        );
                    }

                    return Mono.just(
                        InitializationClaim
                            .acquired(
                                attemptId,
                                created
                            )
                    );
                }
            );
    }

    /**
     * 并发首次插入失败后读取真实赢家。
     *
     * <p>
     * 此方法在原事务回滚完成后执行，
     * 不在失败事务中继续访问数据库。
     * </p>
     */
    private Mono<InitializationClaim>
        claimAfterConcurrentInsert(
            OperationContext context
        ) {

        return selectCurrent(
            context,
            false
        )
            .switchIfEmpty(
                Mono.error(
                    new InstallationStateConflictException(
                        "并发初始化发生冲突，"
                            + "但没有读取到真实安装状态。"
                    )
                )
            )
            .flatMap(
                current -> {
                    InitializationClaim claim =
                        stableClaim(
                            current
                        );

                    if (claim != null) {
                        return Mono.just(
                            claim
                        );
                    }

                    return Mono.error(
                        new InstallationStateConflictException(
                            "并发初始化完成后，"
                                + "数据库状态不允许恢复："
                                + current.state()
                        )
                    );
                }
            );
    }

    /**
     * 已安装和初始化中的记录可以直接返回稳定结果。
     */
    static InitializationClaim stableClaim(
        SystemInstallationRecord current
    ) {
        Objects.requireNonNull(
            current,
            "数据库安装状态记录不能为空。"
        );

        if (current.installed()) {
            return InitializationClaim
                .alreadyInstalled(
                    current
                );
        }

        if (
            current.state()
                == InstallationState
                    .INITIALIZING
        ) {
            return InitializationClaim
                .alreadyInitializing(
                    current
                );
        }

        return null;
    }

    /**
     * 创建首条 INITIALIZING 记录。
     */
    static SystemInstallationRecord
        newInitializingRecord(
            UUID attemptId,
            Instant startedAt
        ) {

        UUID safeAttemptId =
            Objects.requireNonNull(
                attemptId,
                "初始化尝试 ID 不能为空。"
            );

        Instant safeStartedAt =
            Objects.requireNonNull(
                startedAt,
                "初始化开始时间不能为空。"
            );

        return new SystemInstallationRecord(
            SystemInstallationSchema
                .PRIMARY_SINGLETON_ID,
            UUID.randomUUID(),
            InstallationState.INITIALIZING,
            1,
            safeAttemptId,
            safeStartedAt,
            null,
            null,
            null,
            null,
            safeStartedAt,
            safeStartedAt
        );
    }

    /**
     * 从 FAILED 或 UNINITIALIZED 重新开始。
     */
    static SystemInstallationRecord
        restartInitializingRecord(
            SystemInstallationRecord current,
            UUID attemptId,
            Instant startedAt
        ) {

        SystemInstallationRecord safeCurrent =
            Objects.requireNonNull(
                current,
                "当前安装状态记录不能为空。"
            );

        UUID safeAttemptId =
            Objects.requireNonNull(
                attemptId,
                "初始化尝试 ID 不能为空。"
            );

        Instant safeStartedAt =
            Objects.requireNonNull(
                startedAt,
                "初始化开始时间不能为空。"
            );

        if (
            !safeCurrent
                .canStartInitialization()
        ) {
            throw new InstallationStateConflictException(
                "当前数据库安装状态不允许重新初始化："
                    + safeCurrent.state()
            );
        }

        return new SystemInstallationRecord(
            safeCurrent.singletonId(),
            safeCurrent.instanceId(),
            InstallationState.INITIALIZING,
            safeCurrent.stateVersion() + 1,
            safeAttemptId,
            safeStartedAt,
            null,
            null,
            null,
            null,
            safeCurrent.createdAt(),
            safeStartedAt
        );
    }

    /**
     * 检查 system_instances 真实表是否存在。
     */
    private Mono<Boolean> tableExists(
        OperationContext context
    ) {
        return context
            .client()
            .sql(
                tableExistsSql(
                    context
                        .settings()
                        .type()
                )
            )
            .bind(
                "tableName",
                context
                    .tableNames()
                    .systemInstancesTable()
            )
            .map(
                (row, metadata) ->
                    numberValue(
                        row.get(
                            "match_count"
                        ),
                        "match_count"
                    ) > 0
            )
            .one()
            .defaultIfEmpty(false);
    }

    /**
     * 读取单例记录。
     */
    private Mono<SystemInstallationRecord>
        selectCurrent(
            OperationContext context,
            boolean forUpdate
        ) {

        return context
            .client()
            .sql(
                R2dbcInstallationStateSql
                    .selectCurrent(
                        context
                            .settings()
                            .type(),
                        context
                            .tableNames()
                            .systemInstancesTable(),
                        forUpdate
                    )
            )
            .bind(
                "singletonId",
                SystemInstallationSchema
                    .PRIMARY_SINGLETON_ID
            )
            .map(
                (row, metadata) ->
                    mapRecord(row)
            )
            .one();
    }

    /**
     * 插入首条安装状态记录。
     */
    private Mono<Long> insertRecord(
        OperationContext context,
        SystemInstallationRecord record
    ) {
        return context
            .client()
            .sql(
                R2dbcInstallationStateSql
                    .insertInitializing(
                        context
                            .settings()
                            .type(),
                        context
                            .tableNames()
                            .systemInstancesTable()
                    )
            )
            .bind(
                "singletonId",
                record.singletonId()
            )
            .bind(
                "instanceId",
                record
                    .instanceId()
                    .toString()
            )
            .bind(
                "installationState",
                record
                    .state()
                    .name()
            )
            .bind(
                "stateVersion",
                record.stateVersion()
            )
            .bind(
                "attemptId",
                record
                    .initializationAttemptId()
                    .toString()
            )
            .bind(
                "startedAt",
                databaseTime(
                    record
                        .initializationStartedAt()
                )
            )
            .bind(
                "createdAt",
                databaseTime(
                    record.createdAt()
                )
            )
            .bind(
                "updatedAt",
                databaseTime(
                    record.updatedAt()
                )
            )
            .fetch()
            .rowsUpdated();
    }

    /**
     * 使用 state_version 和旧状态执行乐观更新。
     */
    private Mono<Long> updateToInitializing(
        OperationContext context,
        SystemInstallationRecord current,
        SystemInstallationRecord restarted
    ) {
        return context
            .client()
            .sql(
                R2dbcInstallationStateSql
                    .updateToInitializing(
                        context
                            .settings()
                            .type(),
                        context
                            .tableNames()
                            .systemInstancesTable()
                    )
            )
            .bind(
                "newState",
                restarted
                    .state()
                    .name()
            )
            .bind(
                "newVersion",
                restarted.stateVersion()
            )
            .bind(
                "attemptId",
                restarted
                    .initializationAttemptId()
                    .toString()
            )
            .bind(
                "startedAt",
                databaseTime(
                    restarted
                        .initializationStartedAt()
                )
            )
            .bind(
                "updatedAt",
                databaseTime(
                    restarted.updatedAt()
                )
            )
            .bind(
                "singletonId",
                current.singletonId()
            )
            .bind(
                "expectedVersion",
                current.stateVersion()
            )
            .bind(
                "expectedState",
                current
                    .state()
                    .name()
            )
            .fetch()
            .rowsUpdated();
    }

    /**
     * 使用旧状态、旧版本和 attemptId 推进到 INSTALLED。
     */
    private Mono<Long> updateToInstalled(
        OperationContext context,
        SystemInstallationRecord current,
        SystemInstallationRecord installed
    ) {
        return context.client()
            .sql(
                R2dbcInstallationStateSql.updateToInstalled(
                    context.settings().type(),
                    context.tableNames().systemInstancesTable()
                )
            )
            .bind("newState", installed.state().name())
            .bind("newVersion", installed.stateVersion())
            .bind("installedAt", databaseTime(installed.installedAt()))
            .bind("installedVersion", installed.installedVersion())
            .bind("updatedAt", databaseTime(installed.updatedAt()))
            .bind("singletonId", current.singletonId())
            .bind("expectedVersion", current.stateVersion())
            .bind("expectedState", current.state().name())
            .bind(
                "attemptId",
                installed.initializationAttemptId().toString()
            )
            .fetch()
            .rowsUpdated();
    }

    /**
     * 使用旧状态、旧版本和 attemptId 推进到 FAILED。
     */
    private Mono<Long> updateToFailed(
        OperationContext context,
        SystemInstallationRecord current,
        SystemInstallationRecord failed
    ) {
        return context.client()
            .sql(
                R2dbcInstallationStateSql.updateToFailed(
                    context.settings().type(),
                    context.tableNames().systemInstancesTable()
                )
            )
            .bind("newState", failed.state().name())
            .bind("newVersion", failed.stateVersion())
            .bind("errorCode", failed.lastErrorCode())
            .bind("errorMessage", failed.lastErrorMessage())
            .bind("updatedAt", databaseTime(failed.updatedAt()))
            .bind("singletonId", current.singletonId())
            .bind("expectedVersion", current.stateVersion())
            .bind("expectedState", current.state().name())
            .bind(
                "attemptId",
                failed.initializationAttemptId().toString()
            )
            .fetch()
            .rowsUpdated();
    }

    /**
     * 把 R2DBC 行映射为完整安装状态。
     */
    static SystemInstallationRecord mapRecord(
        Row row
    ) {
        Objects.requireNonNull(
            row,
            "数据库行不能为空。"
        );

        try {
            return new SystemInstallationRecord(
                shortValue(
                    row.get(
                        "singleton_id"
                    ),
                    "singleton_id"
                ),
                uuidValue(
                    row.get(
                        "instance_id"
                    ),
                    "instance_id"
                ),
                stateValue(
                    row.get(
                        "installation_state"
                    )
                ),
                numberValue(
                    row.get(
                        "state_version"
                    ),
                    "state_version"
                ),
                nullableUuidValue(
                    row.get(
                        "initialization_attempt_id"
                    ),
                    "initialization_attempt_id"
                ),
                nullableInstantValue(
                    row.get(
                        "initialization_started_at"
                    ),
                    "initialization_started_at"
                ),
                nullableInstantValue(
                    row.get(
                        "installed_at"
                    ),
                    "installed_at"
                ),
                nullableText(
                    row.get(
                        "installed_version"
                    )
                ),
                nullableText(
                    row.get(
                        "last_error_code"
                    )
                ),
                nullableText(
                    row.get(
                        "last_error_message"
                    )
                ),
                instantValue(
                    row.get(
                        "created_at"
                    ),
                    "created_at"
                ),
                instantValue(
                    row.get(
                        "updated_at"
                    ),
                    "updated_at"
                )
            );
        } catch (RuntimeException error) {
            if (
                error
                    instanceof
                    InvalidInstallationRecordException
            ) {
                throw error;
            }

            throw new InvalidInstallationRecordException(
                "数据库安装状态记录格式无效。",
                error
            );
        }
    }

    static String tableExistsSql(
        DatabaseType databaseType
    ) {
        return switch (
            Objects.requireNonNull(
                databaseType,
                "数据库类型不能为空。"
            )
        ) {
            case MYSQL, MARIADB ->
                "SELECT COUNT(*) AS match_count "
                    + "FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_type = 'BASE TABLE' "
                    + "AND table_name = :tableName";

            case POSTGRESQL ->
                "SELECT COUNT(*) AS match_count "
                    + "FROM information_schema.tables "
                    + "WHERE table_schema = current_schema() "
                    + "AND table_type = 'BASE TABLE' "
                    + "AND table_name = :tableName";
        };
    }

    static boolean isDuplicateKey(
        Throwable error
    ) {
        Throwable current =
            error;

        while (current != null) {
            if (
                current
                    instanceof
                    DuplicateKeyException
            ) {
                return true;
            }

            if (
                current
                    instanceof
                    R2dbcException r2dbcError
            ) {
                String sqlState =
                    r2dbcError.getSqlState();

                int errorCode =
                    r2dbcError.getErrorCode();

                if (
                    "23505".equals(
                        sqlState
                    )
                    || errorCode == 1062
                ) {
                    return true;
                }
            }

            String className =
                current
                    .getClass()
                    .getSimpleName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            String message =
                current.getMessage();

            String normalizedMessage =
                message == null
                    ? ""
                    : message
                        .toLowerCase(
                            Locale.ROOT
                        );

            if (
                className.contains(
                    "duplicatekey"
                )
                || normalizedMessage.contains(
                    "duplicate entry"
                )
                || normalizedMessage.contains(
                    "duplicate key"
                )
                || normalizedMessage.contains(
                    "unique constraint"
                )
            ) {
                return true;
            }

            if (
                current.getCause()
                    == current
            ) {
                break;
            }

            current =
                current.getCause();
        }

        return false;
    }

    static LocalDateTime databaseTime(
        Instant value
    ) {
        return LocalDateTime.ofInstant(
            Objects.requireNonNull(
                value,
                "数据库时间不能为空。"
            ),
            ZoneOffset.UTC
        );
    }

    static Instant instantValue(
        Object value,
        String columnName
    ) {
        Instant instant =
            nullableInstantValue(
                value,
                columnName
            );

        if (instant == null) {
            throw new InvalidInstallationRecordException(
                "数据库字段不能为空："
                    + columnName
            );
        }

        return instant;
    }

    static Instant nullableInstantValue(
        Object value,
        String columnName
    ) {
        if (value == null) {
            return null;
        }

        if (value instanceof Instant instant) {
            return instant;
        }

        if (
            value
                instanceof
                LocalDateTime localDateTime
        ) {
            return localDateTime.toInstant(
                ZoneOffset.UTC
            );
        }

        if (
            value
                instanceof
                OffsetDateTime offsetDateTime
        ) {
            return offsetDateTime.toInstant();
        }

        if (
            value
                instanceof
                ZonedDateTime zonedDateTime
        ) {
            return zonedDateTime.toInstant();
        }

        if (value instanceof CharSequence text) {
            String normalized =
                text.toString().trim();

            try {
                return Instant.parse(
                    normalized
                );
            } catch (
                RuntimeException ignored
            ) {
                try {
                    return LocalDateTime
                        .parse(
                            normalized
                        )
                        .toInstant(
                            ZoneOffset.UTC
                        );
                } catch (
                    RuntimeException error
                ) {
                    throw new InvalidInstallationRecordException(
                        "数据库时间字段格式无效："
                            + columnName,
                        error
                    );
                }
            }
        }

        throw new InvalidInstallationRecordException(
            "数据库时间字段类型无效："
                + columnName
        );
    }

    static UUID uuidValue(
        Object value,
        String columnName
    ) {
        UUID uuid =
            nullableUuidValue(
                value,
                columnName
            );

        if (uuid == null) {
            throw new InvalidInstallationRecordException(
                "数据库 UUID 字段不能为空："
                    + columnName
            );
        }

        return uuid;
    }

    static UUID nullableUuidValue(
        Object value,
        String columnName
    ) {
        if (value == null) {
            return null;
        }

        if (value instanceof UUID uuid) {
            return uuid;
        }

        try {
            return UUID.fromString(
                value.toString().trim()
            );
        } catch (
            RuntimeException error
        ) {
            throw new InvalidInstallationRecordException(
                "数据库 UUID 字段格式无效："
                    + columnName,
                error
            );
        }
    }

    static long numberValue(
        Object value,
        String columnName
    ) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof CharSequence text) {
            try {
                return Long.parseLong(
                    text.toString().trim()
                );
            } catch (
                NumberFormatException error
            ) {
                throw new InvalidInstallationRecordException(
                    "数据库数字字段格式无效："
                        + columnName,
                    error
                );
            }
        }

        throw new InvalidInstallationRecordException(
            "数据库数字字段类型无效："
                + columnName
        );
    }

    static short shortValue(
        Object value,
        String columnName
    ) {
        long number =
            numberValue(
                value,
                columnName
            );

        if (
            number < Short.MIN_VALUE
            || number > Short.MAX_VALUE
        ) {
            throw new InvalidInstallationRecordException(
                "数据库短整数字段超出范围："
                    + columnName
            );
        }

        return (short) number;
    }

    static InstallationState stateValue(
        Object value
    ) {
        if (value == null) {
            throw new InvalidInstallationRecordException(
                "数据库安装状态不能为空。"
            );
        }

        try {
            return InstallationState.valueOf(
                value
                    .toString()
                    .trim()
                    .toUpperCase(
                        Locale.ROOT
                    )
            );
        } catch (
            RuntimeException error
        ) {
            throw new InvalidInstallationRecordException(
                "数据库安装状态值无效。",
                error
            );
        }
    }

    static String nullableText(
        Object value
    ) {
        if (value == null) {
            return null;
        }

        String text =
            value.toString().trim();

        return text.isEmpty()
            ? null
            : text;
    }

    private OperationContext context(
        DatabaseSettings settings
    ) {
        DatabaseSettings safeSettings =
            requireSettings(
                settings
            );

        /*
         * 安装流程传入的数据库配置必须驱动
         * 同一个运行时 R2DBC ConnectionFactory。
         */
        settingsService.useForInstallation(
            safeSettings
        );

        /*
         * RuntimeR2dbcConnectionFactory 会在真正创建连接时
         * 比较当前数据库配置指纹：配置未变化时复用连接池，
         * 配置变化时才切换并关闭旧连接池。
         *
         * 此处禁止主动 refresh()。状态读取可能与迁移事务并发，
         * 无条件刷新会关闭其他操作仍在使用的共享连接池。
         */

        DatabaseClient client =
            DatabaseClient.create(
                connectionFactory
            );

        R2dbcTransactionManager
            transactionManager =
            new R2dbcTransactionManager(
                connectionFactory
            );

        TransactionalOperator transactions =
            TransactionalOperator.create(
                transactionManager
            );

        return new OperationContext(
            safeSettings,
            R2dbcInstallationTableNames.from(
                safeSettings
            ),
            client,
            transactions
        );
    }

    private DatabaseSettings requireSettings(
        DatabaseSettings settings
    ) {
        if (settings == null) {
            throw new IllegalStateException(
                "尚未找到数据库安装配置。"
            );
        }

        DatabaseSettings safe =
            settings.normalized();

        if (!safe.hasRequiredFields()) {
            throw new IllegalStateException(
                "数据库安装配置不完整。"
            );
        }

        return safe;
    }

    private record OperationContext(
        DatabaseSettings settings,
        R2dbcInstallationTableNames tableNames,
        DatabaseClient client,
        TransactionalOperator transactions
    ) {
    }

    /**
     * 专门表示数据库记录内容无法安全解析。
     */
    static final class
        InvalidInstallationRecordException
        extends IllegalStateException {

        InvalidInstallationRecordException(
            String message
        ) {
            super(message);
        }

        InvalidInstallationRecordException(
            String message,
            Throwable cause
        ) {
            super(
                message,
                cause
            );
        }
    }
}
