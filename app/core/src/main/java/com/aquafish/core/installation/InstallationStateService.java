package com.aquafish.core.installation;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.installation.r2dbc.ReactiveInstallationStateStore;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Aquafish 统一响应式数据库安装状态服务。
 *
 * <p>
 * 数据库中的 system_instances 是首次安装状态的最终事实来源。
 * 本服务只依赖响应式仓库，不允许回退到同步 JDBC 存储。
 * </p>
 */
@Service
public class InstallationStateService {

    private final DatabaseRuntimeSettingsService
        databaseRuntimeSettingsService;

    private final ReactiveInstallationStateStore
        installationStateStore;

    public InstallationStateService(
        DatabaseRuntimeSettingsService
            databaseRuntimeSettingsService,
        ReactiveInstallationStateStore
            installationStateStore
    ) {
        this.databaseRuntimeSettingsService =
            Objects.requireNonNull(
                databaseRuntimeSettingsService,
                "数据库运行配置服务不能为空。"
            );

        this.installationStateStore =
            Objects.requireNonNull(
                installationStateStore,
                "响应式数据库安装状态存储不能为空。"
            );
    }

    /**
     * 使用当前运行配置读取数据库安装状态。
     */
    public Mono<InstallationStateSnapshot> current() {
        return Mono.defer(
            () ->
                current(
                    currentSettings()
                )
        );
    }

    /**
     * 使用明确数据库配置读取安装状态。
     */
    public Mono<InstallationStateSnapshot> current(
        DatabaseSettings settings
    ) {
        return Mono.defer(
            () ->
                installationStateStore.read(
                    requireSettings(settings)
                )
        );
    }

    /**
     * 使用当前数据库配置和新尝试 ID 抢占初始化权。
     */
    public Mono<InitializationClaim>
        tryStartInitialization() {

        return Mono.defer(
            () ->
                tryStartInitialization(
                    currentSettings(),
                    UUID.randomUUID(),
                    Instant.now()
                )
        );
    }

    /**
     * 使用当前数据库配置和明确尝试参数抢占初始化权。
     */
    public Mono<InitializationClaim>
        tryStartInitialization(
            UUID attemptId,
            Instant startedAt
        ) {

        return Mono.defer(
            () ->
                tryStartInitialization(
                    currentSettings(),
                    attemptId,
                    startedAt
                )
        );
    }

    /**
     * 使用明确数据库配置和尝试参数抢占初始化权。
     */
    public Mono<InitializationClaim>
        tryStartInitialization(
            DatabaseSettings settings,
            UUID attemptId,
            Instant startedAt
        ) {

        return Mono.defer(
            () ->
                installationStateStore
                    .tryStartInitialization(
                        requireSettings(settings),
                        attemptId,
                        startedAt
                    )
        );
    }

    /**
     * 使用当前数据库配置完成安装。
     */
    public Mono<SystemInstallationRecord>
        completeInstallation(
            UUID attemptId
        ) {

        return Mono.defer(
            () ->
                completeInstallation(
                    currentSettings(),
                    attemptId,
                    Instant.now(),
                    SystemInstallationSchema
                        .CURRENT_INSTALLATION_VERSION
                )
        );
    }

    /**
     * 使用当前数据库配置和明确参数完成安装。
     */
    public Mono<SystemInstallationRecord>
        completeInstallation(
            UUID attemptId,
            Instant installedAt,
            String installedVersion
        ) {

        return Mono.defer(
            () ->
                completeInstallation(
                    currentSettings(),
                    attemptId,
                    installedAt,
                    installedVersion
                )
        );
    }

    /**
     * 使用明确数据库配置完成安装。
     */
    public Mono<SystemInstallationRecord>
        completeInstallation(
            DatabaseSettings settings,
            UUID attemptId,
            Instant installedAt,
            String installedVersion
        ) {

        return Mono.defer(
            () ->
                installationStateStore
                    .markInstalled(
                        requireSettings(settings),
                        attemptId,
                        installedAt,
                        installedVersion
                    )
        );
    }

    /**
     * 使用当前数据库配置记录初始化失败。
     */
    public Mono<SystemInstallationRecord>
        failInitialization(
            UUID attemptId,
            String errorCode,
            String errorMessage
        ) {

        return Mono.defer(
            () ->
                failInitialization(
                    currentSettings(),
                    attemptId,
                    Instant.now(),
                    errorCode,
                    errorMessage
                )
        );
    }

    /**
     * 使用当前数据库配置和明确时间记录失败。
     */
    public Mono<SystemInstallationRecord>
        failInitialization(
            UUID attemptId,
            Instant failedAt,
            String errorCode,
            String errorMessage
        ) {

        return Mono.defer(
            () ->
                failInitialization(
                    currentSettings(),
                    attemptId,
                    failedAt,
                    errorCode,
                    errorMessage
                )
        );
    }

    /**
     * 使用明确数据库配置记录初始化失败。
     */
    public Mono<SystemInstallationRecord>
        failInitialization(
            DatabaseSettings settings,
            UUID attemptId,
            Instant failedAt,
            String errorCode,
            String errorMessage
        ) {

        return Mono.defer(
            () ->
                installationStateStore
                    .markFailed(
                        requireSettings(settings),
                        attemptId,
                        failedAt,
                        errorCode,
                        errorMessage
                    )
        );
    }

    private DatabaseSettings currentSettings() {
        return requireSettings(
            databaseRuntimeSettingsService
                .current()
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
}
