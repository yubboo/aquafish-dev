package com.aquafish.core.database.migration.r2dbc;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSchemaTableStatus;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.migration.DatabaseMigrationPreview;
import com.aquafish.core.database.migration.DatabaseMigrationResult;
import com.aquafish.core.installation.InitializationClaim;
import com.aquafish.core.installation.InitializationClaimStatus;
import com.aquafish.core.installation.InstallationAttemptSession;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Aquafish 正式响应式数据库迁移入口。
 *
 * <p>本服务只使用 R2DBC 和 r2dbc-migrate。</p>
 */
@Service
public final class R2dbcDatabaseMigrationService {

    private final DatabaseRuntimeSettingsService
        settingsService;

    private final R2dbcMigrationStateInspector
        stateInspector;

    private final R2dbcMigrationExecutor
        migrationExecutor;

    private final InstallationAttemptSession
        installationAttemptSession;

    public R2dbcDatabaseMigrationService(
        DatabaseRuntimeSettingsService settingsService,
        R2dbcMigrationStateInspector stateInspector,
        R2dbcMigrationExecutor migrationExecutor,
        InstallationAttemptSession installationAttemptSession
    ) {
        this.settingsService =
            Objects.requireNonNull(
                settingsService,
                "数据库运行配置服务不能为空。"
            );

        this.stateInspector =
            Objects.requireNonNull(
                stateInspector,
                "R2DBC 迁移状态检查器不能为空。"
            );

        this.migrationExecutor =
            Objects.requireNonNull(
                migrationExecutor,
                "R2DBC 迁移执行器不能为空。"
            );

        this.installationAttemptSession =
            Objects.requireNonNull(
                installationAttemptSession,
                "安装尝试会话不能为空。"
            );
    }

    public Mono<DatabaseMigrationPreview> preview() {
        return Mono.defer(
            () ->
                preview(
                    settingsService.current()
                )
        );
    }

    public Mono<DatabaseMigrationPreview> preview(
        DatabaseSettings settings
    ) {
        return Mono.defer(
            () -> {
                DatabaseSettings safeSettings =
                    requireSettings(
                        settings
                    );

                return stateInspector
                    .inspect(
                        safeSettings
                    )
                    .map(
                        this::toPreview
                    );
            }
        );
    }

    public Mono<DatabaseMigrationResult> migrate() {
        return Mono.defer(
            () ->
                migrate(
                    settingsService.current()
                )
        );
    }

    public Mono<DatabaseMigrationResult> migrate(
        DatabaseSettings settings
    ) {
        return Mono.defer(
            () -> {
                DatabaseSettings safeSettings =
                    requireSettings(
                        settings
                    );

                return stateInspector
                    .inspect(
                        safeSettings
                    )
                    .flatMap(
                        before -> {
                            ensureReadyBeforeMigration(
                                before
                            );

                            return migrateIfRequired(
                                safeSettings,
                                before
                            );
                        }
                    )
                    .flatMap(
                        outcome ->
                            ensureInstallationAttempt(
                                safeSettings
                            )
                                .thenReturn(
                                    toResult(
                                        outcome
                                    )
                                )
                    );
            }
        );
    }

    /**
     * 升级已经完成首次安装的数据库。
     *
     * <p>该入口与安装迁移共用相同的状态检查和 r2dbc-migrate 执行器，
     * 但不会重新申领安装 attempt，也不会把 INSTALLED 实例误判为重复安装。
     * 它只允许把受 Aquafish 迁移历史管理的数据库推进到当前代码版本。</p>
     *
     * @return 只包含版本号和待执行数量的脱敏迁移结果
     */
    public Mono<DatabaseMigrationResult> upgradeInstalledDatabase() {
        return Mono.defer(
            () -> upgradeInstalledDatabase(
                settingsService.current()
            )
        );
    }

    /**
     * 使用指定运行配置升级已安装数据库，主要供启动编排和测试使用。
     */
    public Mono<DatabaseMigrationResult> upgradeInstalledDatabase(
        DatabaseSettings settings
    ) {
        return Mono.defer(
            () -> {
                DatabaseSettings safeSettings =
                    requireSettings(settings);

                return stateInspector
                    .inspect(safeSettings)
                    .flatMap(before -> {
                        ensureReadyBeforeMigration(before);

                        return migrateIfRequired(
                            safeSettings,
                            before
                        );
                    })
                    .map(this::toResult);
            }
        );
    }

    private Mono<MigrationOutcome> migrateIfRequired(
        DatabaseSettings settings,
        R2dbcMigrationDatabaseState before
    ) {
        if (
            before.pendingMigrations()
                == 0
        ) {
            ensureCompleteAfterMigration(
                before
            );

            return Mono.just(
                new MigrationOutcome(
                    before,
                    before,
                    false
                )
            );
        }

        return migrationExecutor
            .migrate(
                settings
            )
            .then(
                stateInspector.inspect(
                    settings
                )
            )
            .map(
                after -> {
                    ensureCompleteAfterMigration(
                        after
                    );

                    return new MigrationOutcome(
                        before,
                        after,
                        true
                    );
                }
            );
    }

    /**
     * 数据库迁移完成后，通过正式响应式状态仓库
     * 取得或恢复当前安装尝试。
     */
    private Mono<Void> ensureInstallationAttempt(
        DatabaseSettings settings
    ) {
        return installationAttemptSession
            .claimAfterMigration(
                settings
            )
            .doOnNext(
                this::validateClaim
            )
            .then();
    }

    private void validateClaim(
        InitializationClaim claim
    ) {
        if (claim == null) {
            throw new IllegalStateException(
                "数据库迁移完成，"
                    + "但安装状态机没有返回初始化结果。"
            );
        }

        if (
            claim.status()
                == InitializationClaimStatus
                    .ALREADY_INSTALLED
        ) {
            throw new IllegalStateException(
                "Aquafish 已经完成首次安装，"
                    + "禁止重新执行安装数据库初始化。"
            );
        }

        if (claim.attemptId() == null) {
            throw new IllegalStateException(
                "数据库迁移完成，"
                    + "但没有取得有效的安装 attemptId。"
            );
        }
    }

    private void ensureReadyBeforeMigration(
        R2dbcMigrationDatabaseState state
    ) {
        if (state == null) {
            throw new IllegalStateException(
                "数据库迁移状态不能为空。"
            );
        }

        if (state.unmanagedDatabase()) {
            throw new IllegalStateException(
                "检测到非空且没有 R2DBC 迁移历史的数据库，"
                    + "开发阶段请清空数据库后重新初始化。"
            );
        }

        if (state.databaseAhead()) {
            throw new IllegalStateException(
                "数据库版本高于当前 Aquafish 代码版本，"
                    + "禁止使用旧程序继续迁移。"
            );
        }

        if (!state.historyConsistent()) {
            throw new IllegalStateException(
                "数据库迁移历史存在未知版本或版本缺口，"
                    + "禁止自动迁移。"
            );
        }

        if (!state.canMigrate()) {
            throw new IllegalStateException(
                state.note().isBlank()
                    ? "当前数据库状态不允许执行迁移。"
                    : state.note()
            );
        }
    }

    private void ensureCompleteAfterMigration(
        R2dbcMigrationDatabaseState state
    ) {
        if (state == null) {
            throw new IllegalStateException(
                "迁移后无法读取数据库状态。"
            );
        }

        if (!state.migrationsTableExists()) {
            throw new IllegalStateException(
                "数据库迁移完成后没有找到 migrations 历史表。"
            );
        }

        if (
            state.databaseAhead()
            || !state.historyConsistent()
        ) {
            throw new IllegalStateException(
                "数据库迁移完成后历史状态校验失败。"
            );
        }

        if (
            state.pendingMigrations()
                != 0
        ) {
            throw new IllegalStateException(
                "数据库迁移完成后仍有 "
                    + state.pendingMigrations()
                    + " 个版本未执行。"
            );
        }

        if (
            state.currentVersion()
                != state.latestVersion()
        ) {
            throw new IllegalStateException(
                "数据库当前版本与代码最高版本不一致。"
            );
        }
    }

    private DatabaseMigrationPreview toPreview(
        R2dbcMigrationDatabaseState state
    ) {
        return new DatabaseMigrationPreview(
            true,
            state.canMigrate(),
            state.migrationsTableExists(),
            state.unmanagedDatabase(),
            state
                .tableNames()
                .migrationsTable(),
            versionText(
                state.currentVersion()
            ),
            state.pendingMigrations(),
            List
                .<DatabaseSchemaTableStatus>of(),
            state.note(),
            ""
        );
    }

    private DatabaseMigrationResult toResult(
        MigrationOutcome outcome
    ) {
        String message =
            outcome.migrated()
                ? "数据库迁移完成。"
                : "数据库已经是最新版本。";

        return new DatabaseMigrationResult(
            outcome
                .before()
                .databaseType(),
            versionText(
                outcome
                    .before()
                    .currentVersion()
            ),
            versionText(
                outcome
                    .after()
                    .currentVersion()
            ),
            outcome
                .before()
                .pendingMigrations(),
            outcome
                .after()
                .pendingMigrations(),
            outcome.migrated(),
            message
        );
    }

    private String versionText(
        long version
    ) {
        return version <= 0
            ? ""
            : Long.toString(
                version
            );
    }

    private DatabaseSettings requireSettings(
        DatabaseSettings settings
    ) {
        if (settings == null) {
            throw new IllegalStateException(
                "尚未找到数据库运行配置。"
            );
        }

        DatabaseSettings normalized =
            settings.normalized();

        if (!normalized.hasRequiredFields()) {
            throw new IllegalStateException(
                "数据库连接配置不完整。"
            );
        }

        return normalized;
    }

    private record MigrationOutcome(
        R2dbcMigrationDatabaseState before,
        R2dbcMigrationDatabaseState after,
        boolean migrated
    ) {
    }
}
