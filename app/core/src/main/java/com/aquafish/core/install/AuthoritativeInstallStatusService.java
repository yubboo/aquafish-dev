package com.aquafish.core.install;

import com.aquafish.core.installation.InstallationStateReadStatus;
import com.aquafish.core.installation.InstallationStateService;
import com.aquafish.core.installation.InstallationStateSnapshot;
import com.aquafish.core.installation.SystemInstallationRecord;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Aquafish 权威安装状态服务。
 *
 * <p>数据库 system_instances 是唯一事实来源。install.lock 只用于
 * 兼容展示，并在数据库已经 INSTALLED 但文件缺失时自动恢复。</p>
 */
@Service
public class AuthoritativeInstallStatusService {

    private final InstallationStateService installationStateService;
    private final InstallLockService installLockService;

    private final SetupConfigurationSource databaseSource;

    public AuthoritativeInstallStatusService(
        InstallationStateService installationStateService,
        InstallLockService installLockService,
        @Value("${aquafish.setup.database-source:installer}") String databaseSource
    ) {
        this.installationStateService = Objects.requireNonNull(
            installationStateService,
            "安装状态服务不能为空。"
        );
        this.installLockService = Objects.requireNonNull(
            installLockService,
            "安装锁兼容服务不能为空。"
        );
        this.databaseSource = SetupConfigurationSource.fromValue(databaseSource);
    }

    /**
     * 每次订阅读取数据库真实状态，不使用 install.lock 推断已安装。
     */
    public Mono<AuthoritativeInstallStatus> current() {
        return compatibilityStatus()
            .flatMap(files -> {
                /*
                 * 分发包尚未写入 application.yaml 时没有真实数据库配置，直接返回
                 * NOT_CONFIGURED，避免每次打开安装页都探测默认 127.0.0.1:3306。
                 * 1Panel/Docker 的数据库来自环境变量，即使没有配置文件也必须读取。
                 */
                if (!files.applicationConfigExists()
                    && databaseSource != SetupConfigurationSource.ENVIRONMENT) {
                    return notConfigured(files);
                }

                return installationStateService.current()
                    .flatMap(snapshot -> fromDatabase(snapshot, files))
                    .onErrorResume(error ->
                        databaseReadFailure(files)
                    );
            });
    }

    /**
     * 仅当数据库明确为 INSTALLED 时通过。
     */
    public Mono<Void> requireInstalled() {
        return current().flatMap(status -> {
            if (status.installed()) {
                return Mono.empty();
            }

            return Mono.error(
                new IllegalStateException(
                    status.stateAvailable()
                        ? "系统尚未完成安装。"
                        : "数据库安装状态暂时不可用。"
                )
            );
        });
    }

    private Mono<AuthoritativeInstallStatus> fromDatabase(
        InstallationStateSnapshot snapshot,
        InstallStatus files
    ) {
        if (
            snapshot.status()
                == InstallationStateReadStatus.DATABASE_UNAVAILABLE
        ) {
            if (!files.applicationConfigExists()) {
                return notConfigured(files);
            }

            return Mono.just(
                new AuthoritativeInstallStatus(
                    false,
                    files.locked(),
                    false,
                    false,
                    snapshot.status().name(),
                    files.applicationConfigExists(),
                    null,
                    safeMessage(snapshot)
                )
            );
        }

        if (
            snapshot.status()
                == InstallationStateReadStatus.INVALID_RECORD
        ) {
            return Mono.just(
                new AuthoritativeInstallStatus(
                    false,
                    files.locked(),
                    false,
                    false,
                    snapshot.status().name(),
                    files.applicationConfigExists(),
                    null,
                    safeMessage(snapshot)
                )
            );
        }

        if (
            snapshot.status()
                != InstallationStateReadStatus.RECORD_FOUND
        ) {
            return Mono.just(
                new AuthoritativeInstallStatus(
                    false,
                    files.locked(),
                    files.workDirWritable(),
                    true,
                    snapshot.status().name(),
                    files.applicationConfigExists(),
                    null,
                    files.locked()
                        ? "数据库未完成安装，旧安装锁已被忽略。"
                        : null
                )
            );
        }

        SystemInstallationRecord record =
            snapshot.recordOptional().orElseThrow();

        if (!record.installed()) {
            return Mono.just(
                new AuthoritativeInstallStatus(
                    false,
                    files.locked(),
                    files.workDirWritable(),
                    true,
                    record.state().name(),
                    files.applicationConfigExists(),
                    null,
                    files.locked()
                        ? "数据库未完成安装，旧安装锁已被忽略。"
                        : null
                )
            );
        }

        AuthoritativeInstallStatus installed =
            new AuthoritativeInstallStatus(
                true,
                files.locked(),
                false,
                true,
                record.state().name(),
                files.applicationConfigExists(),
                record.installedAt().toString(),
                null
            );

        if (files.locked()) {
            return Mono.just(installed);
        }

        return recoverCompatibilityLock(record)
            .thenReturn(
                new AuthoritativeInstallStatus(
                    true,
                    true,
                    false,
                    true,
                    record.state().name(),
                    files.applicationConfigExists(),
                    record.installedAt().toString(),
                    "数据库已安装，缺失的 install.lock 已自动恢复。"
                )
            )
            .onErrorReturn(
                new AuthoritativeInstallStatus(
                    true,
                    false,
                    false,
                    true,
                    record.state().name(),
                    files.applicationConfigExists(),
                    record.installedAt().toString(),
                    "数据库已安装，但兼容安装锁恢复失败。"
                )
            );
    }

    private Mono<Void> recoverCompatibilityLock(
        SystemInstallationRecord record
    ) {
        return Mono.fromRunnable(() ->
            installLockService.writeInstallLock(
                "installed=true\n"
                    + "installedAt=" + record.installedAt() + "\n"
                    + "version=" + record.installedVersion() + "\n"
                    + "source=database-recovery\n"
            )
        ).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Mono<InstallStatus> compatibilityStatus() {
        return Mono.fromCallable(installLockService::status)
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<AuthoritativeInstallStatus> databaseReadFailure(
        InstallStatus files
    ) {
        if (!files.applicationConfigExists()) {
            return notConfigured(files);
        }

        return Mono.just(
            new AuthoritativeInstallStatus(
                false,
                files.locked(),
                false,
                false,
                InstallationStateReadStatus.DATABASE_UNAVAILABLE.name(),
                true,
                null,
                "数据库安装状态暂时不可用。"
            )
        );
    }

    private Mono<AuthoritativeInstallStatus> notConfigured(
        InstallStatus files
    ) {
        return Mono.just(
            new AuthoritativeInstallStatus(
                false,
                files.locked(),
                files.workDirWritable(),
                true,
                "NOT_CONFIGURED",
                false,
                null,
                files.locked()
                    ? "检测到旧安装锁，但数据库尚未配置，安装锁不会作为安装依据。"
                    : null
            )
        );
    }

    private String safeMessage(
        InstallationStateSnapshot snapshot
    ) {
        if (
            snapshot.safeMessage() == null
                || snapshot.safeMessage().isBlank()
        ) {
            return "数据库安装状态暂时不可用。";
        }

        return snapshot.safeMessage();
    }
}
