package com.aquafish.core.install;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.installation.InstallationAttemptSession;
import com.aquafish.core.installation.InstallationState;
import com.aquafish.core.installation.InstallationStateReadStatus;
import com.aquafish.core.installation.InstallationStateService;
import com.aquafish.core.installation.InstallationStateSnapshot;
import com.aquafish.core.installation.SystemInstallationRecord;
import com.aquafish.core.installation.SystemInstallationSchema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Aquafish 安装最终提交服务。
 *
 * <p>管理员、站点选项和数据库 INSTALLED 状态由响应式仓库放在
 * 同一个数据库事务中提交。application.yaml 与 install.lock 是数据库
 * 提交后的兼容文件；文件写入失败时允许使用同一请求安全重试。</p>
 */
@Service
public class SetupFinishService {

    private final DatabaseRuntimeSettingsService settingsService;
    private final ReactiveSetupAdminAccountStore accountStore;
    private final InstallationStateService installationStateService;
    private final InstallationAttemptSession installationAttemptSession;
    private final InstallLockService installLockService;
    private final PasswordEncoder passwordEncoder =
        new BCryptPasswordEncoder();

    public SetupFinishService(
        DatabaseRuntimeSettingsService settingsService,
        ReactiveSetupAdminAccountStore accountStore,
        InstallationStateService installationStateService,
        InstallationAttemptSession installationAttemptSession,
        InstallLockService installLockService
    ) {
        this.settingsService = Objects.requireNonNull(
            settingsService,
            "数据库运行配置服务不能为空。"
        );
        this.accountStore = Objects.requireNonNull(
            accountStore,
            "安装响应式仓库不能为空。"
        );
        this.installationStateService = Objects.requireNonNull(
            installationStateService,
            "安装状态服务不能为空。"
        );
        this.installationAttemptSession = Objects.requireNonNull(
            installationAttemptSession,
            "安装尝试会话不能为空。"
        );
        this.installLockService = Objects.requireNonNull(
            installLockService,
            "安装锁服务不能为空。"
        );
    }

    /**
     * 响应式检查安装最终提交条件。
     */
    public Mono<SetupFinishPreview> preview() {
        return Mono.defer(() -> {
            DatabaseSettings settings = currentSettings();
            Path configFile = applicationConfigFile();

            return installationStateService.current(settings)
                .flatMap(snapshot -> {
                    if (snapshot.installed()) {
                        return fileExists(configFile)
                            .zipWith(fileExists(installLockService.lockFile()))
                            .map(files -> previewResult(
                                true,
                                true,
                                true,
                                true,
                                files.getT1(),
                                files.getT2(),
                                false,
                                "系统已经安装，不能重复完成安装。",
                                null
                            ));
                    }

                    requireInitializing(snapshot);

                    return accountStore.inspect(settings)
                        .zipWith(fileExists(configFile))
                        .zipWith(fileExists(installLockService.lockFile()))
                        .map(result -> {
                            SetupAdminDatabaseState database =
                                result.getT1().getT1();
                            boolean configExists =
                                result.getT1().getT2();
                            boolean lockExists = result.getT2();
                            boolean canFinish =
                                database.coreTablesReady()
                                    && database.initializing()
                                    && configExists;

                            return previewResult(
                                false,
                                true,
                                database.coreTablesReady(),
                                database.adminExists(),
                                configExists,
                                lockExists,
                                canFinish,
                                canFinish
                                    ? "可以完成安装。"
                                    : "安装条件尚未满足。",
                                null
                            );
                        });
                });
        }).onErrorResume(error ->
            fileExists(applicationConfigFile())
                .zipWith(fileExists(installLockService.lockFile()))
                .map(files ->
                    previewResult(
                        false,
                        false,
                        false,
                        false,
                        files.getT1(),
                        files.getT2(),
                        false,
                        "安装完成预览失败。",
                        safeMessage(error)
                    )
                )
        );
    }

    /**
     * 原子提交管理员、站点设置与数据库安装状态。
     */
    public Mono<SetupFinishResult> finish(
        SetupFinishRequest request
    ) {
        return Mono.defer(() -> {
            SetupFinishRequest safeRequest =
                Objects.requireNonNull(
                    request,
                    "安装完成请求不能为空。"
                ).normalized();

            String validationMessage =
                safeRequest.admin().validateMessage();
            if (validationMessage != null) {
                return Mono.error(
                    new IllegalStateException(validationMessage)
                );
            }

            DatabaseSettings settings = currentSettings();
            Instant installedAt = Instant.now();

            return requireApplicationConfig()
                .then(installationStateService.current(settings))
                .map(this::attemptIdForFinish)
                .flatMap(attemptId ->
                    encodePassword(safeRequest.admin().password())
                        .flatMap(passwordHash ->
                            accountStore.finishInstallation(
                                settings,
                                safeRequest.admin(),
                                passwordHash,
                                safeRequest.site(),
                                attemptId,
                                installedAt,
                                SystemInstallationSchema
                                    .CURRENT_INSTALLATION_VERSION
                            )
                        )
                        .flatMap(databaseResult ->
                            finalizeCompatibilityFiles(
                                settings,
                                databaseResult
                            )
                        )
                        .doOnSuccess(result ->
                            installationAttemptSession.clear(attemptId)
                        )
                );
        }).onErrorMap(error -> {
            if (error instanceof IllegalStateException) {
                return error;
            }

            return new IllegalStateException(
                "安装最终提交失败，请安全重试。",
                error
            );
        });
    }

    private Mono<String> encodePassword(String password) {
        return Mono.fromCallable(
            () -> passwordEncoder.encode(password)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> requireApplicationConfig() {
        return fileExists(applicationConfigFile())
            .flatMap(exists ->
                exists
                    ? Mono.empty()
                    : Mono.error(
                        new IllegalStateException(
                            "application.yaml 不存在，请先写入安装配置。"
                        )
                    )
            );
    }

    private UUID attemptIdForFinish(
        InstallationStateSnapshot snapshot
    ) {
        if (
            snapshot.status()
                != InstallationStateReadStatus.RECORD_FOUND
        ) {
            throw new IllegalStateException(
                "数据库安装状态记录不存在，请先初始化数据库。"
            );
        }

        SystemInstallationRecord record =
            snapshot.recordOptional().orElseThrow();

        if (
            record.state() != InstallationState.INITIALIZING
                && record.state() != InstallationState.INSTALLED
        ) {
            throw new IllegalStateException(
                "当前数据库安装状态不能完成安装："
                    + record.state()
            );
        }

        return Objects.requireNonNull(
            record.initializationAttemptId(),
            "数据库安装状态缺少 attemptId。"
        );
    }

    private void requireInitializing(
        InstallationStateSnapshot snapshot
    ) {
        if (
            snapshot.status()
                != InstallationStateReadStatus.RECORD_FOUND
                || snapshot.recordOptional()
                    .map(SystemInstallationRecord::state)
                    .orElse(null)
                    != InstallationState.INITIALIZING
        ) {
            throw new IllegalStateException(
                "当前数据库不处于初始化状态。"
            );
        }
    }

    private Mono<SetupFinishResult> finalizeCompatibilityFiles(
        DatabaseSettings settings,
        SetupFinishDatabaseResult databaseResult
    ) {
        return Mono.fromCallable(() -> {
            boolean configUpdated = updateApplicationYamlLocked();
            installLockService.writeInstallLock(
                buildInstallLockContent(
                    settings,
                    databaseResult.installedAt(),
                    configUpdated
                )
            );

            return new SetupFinishResult(
                true,
                true,
                configUpdated,
                normalizePath(installLockService.workDir()),
                normalizePath(applicationConfigFile()),
                normalizePath(installLockService.lockFile()),
                databaseResult.installedAt().toString(),
                databaseResult.alreadyInstalled()
                    ? "数据库已经完成安装，兼容文件已恢复。"
                    : "Aquafish 安装完成。"
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Boolean> fileExists(Path path) {
        return Mono.fromCallable(
            () -> Files.exists(path)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    private SetupFinishPreview previewResult(
        boolean installed,
        boolean connected,
        boolean coreTablesReady,
        boolean adminExists,
        boolean configExists,
        boolean lockExists,
        boolean canFinish,
        String note,
        String errorMessage
    ) {
        return new SetupFinishPreview(
            installed,
            connected,
            coreTablesReady,
            adminExists,
            configExists,
            lockExists,
            canFinish,
            normalizePath(installLockService.workDir()),
            normalizePath(applicationConfigFile()),
            normalizePath(installLockService.lockFile()),
            note,
            errorMessage
        );
    }

    private DatabaseSettings currentSettings() {
        DatabaseSettings settings = settingsService.current();
        if (settings == null) {
            throw new IllegalStateException(
                "尚未找到数据库安装配置。"
            );
        }

        DatabaseSettings safe = settings.normalized();
        if (!safe.hasRequiredFields()) {
            throw new IllegalStateException(
                "数据库安装配置不完整。"
            );
        }

        return safe;
    }

    private Path applicationConfigFile() {
        return installLockService.workDir()
            .resolve("application.yaml");
    }

    private boolean updateApplicationYamlLocked() {
        Path file = applicationConfigFile();
        try {
            String yaml = Files.readString(
                file,
                StandardCharsets.UTF_8
            );
            String updated = yaml;

            if (updated.contains("    locked: false")) {
                updated = updated.replace(
                    "    locked: false",
                    "    locked: true"
                );
            } else if (!updated.contains("  install:")) {
                updated = updated.trim()
                    + "\n\n  install:\n    locked: true\n";
            } else if (!updated.contains("locked:")) {
                updated = updated.trim()
                    + "\n    locked: true\n";
            }

            if (!updated.equals(yaml)) {
                Files.writeString(
                    file,
                    updated,
                    StandardCharsets.UTF_8
                );
            }

            return true;
        } catch (IOException error) {
            throw new IllegalStateException(
                "更新 application.yaml 安装锁状态失败："
                    + safeMessage(error),
                error
            );
        }
    }

    private String buildInstallLockContent(
        DatabaseSettings settings,
        Instant installedAt,
        boolean configUpdated
    ) {
        String safePrefix =
            TableNameResolver.normalizeConfiguredPrefix(
                settings.tablePrefix()
            );

        return "installed=true\n"
            + "installedAt=" + installedAt + "\n"
            + "version="
            + SystemInstallationSchema.CURRENT_INSTALLATION_VERSION
            + "\n"
            + "databaseType=" + settings.type().value() + "\n"
            + "databaseName=" + settings.name() + "\n"
            + "tablePrefix=" + safePrefix + "\n"
            + "applicationConfigUpdated=" + configUpdated + "\n";
    }

    private String normalizePath(Path path) {
        return path == null
            ? ""
            : path.toAbsolutePath().normalize().toString();
    }

    private String safeMessage(Throwable error) {
        if (
            error == null
                || error.getMessage() == null
                || error.getMessage().isBlank()
        ) {
            return "未知错误";
        }

        return error.getMessage();
    }
}
