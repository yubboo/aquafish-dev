package com.aquafish.core.install;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import java.util.Objects;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 首次安装超级管理员的响应式策略服务。
 *
 * <p>该类只处理安装锁兼容检查、输入校验和 BCrypt；全部数据库
 * 读写与事务由 {@link ReactiveSetupAdminAccountStore} 负责。</p>
 */
@Service
public class SetupAdminAccountInitializer {

    private static final String ROLE_KEY =
        "super_admin";

    private final DatabaseRuntimeSettingsService
        settingsService;

    private final ReactiveSetupAdminAccountStore
        accountStore;

    private final AuthoritativeInstallStatusService
        installStatusService;

    private final PasswordEncoder passwordEncoder =
        new BCryptPasswordEncoder();

    public SetupAdminAccountInitializer(
        DatabaseRuntimeSettingsService settingsService,
        ReactiveSetupAdminAccountStore accountStore,
        AuthoritativeInstallStatusService installStatusService
    ) {
        this.settingsService =
            Objects.requireNonNull(
                settingsService,
                "数据库运行配置服务不能为空。"
            );
        this.accountStore =
            Objects.requireNonNull(
                accountStore,
                "管理员响应式仓库不能为空。"
            );
        this.installStatusService =
            Objects.requireNonNull(
                installStatusService,
                "权威安装状态服务不能为空。"
            );
    }

    /**
     * 响应式预览当前是否可以创建超级管理员。
     */
    public Mono<SetupAdminPreview> preview(
        SetupAdminAccountRequest request
    ) {
        return Mono.defer(
            () -> {
                SetupAdminAccountRequest safeRequest =
                    safeRequest(request).normalized();

                return installStatus()
                    .flatMap(status -> {
                        if (status.installed()) {
                            return Mono.just(
                                installedPreview(
                                    safeRequest
                                )
                            );
                        }

                        if (!status.stateAvailable()) {
                            return Mono.just(
                                unavailablePreview(
                                    safeRequest,
                                    null
                                )
                            );
                        }

                        String validationMessage =
                            safeRequest.validateMessage();

                        if (validationMessage != null) {
                            return Mono.just(
                                invalidPreview(
                                    safeRequest,
                                    validationMessage
                                )
                            );
                        }

                        DatabaseSettings settings =
                            settingsService
                                .current()
                                .normalized();

                        return accountStore
                            .inspect(settings)
                            .map(state ->
                                databasePreview(
                                    safeRequest,
                                    settings,
                                    state
                                )
                            )
                            .onErrorResume(
                                error -> Mono.just(
                                    unavailablePreview(
                                        safeRequest,
                                        settings
                                    )
                                )
                            );
                    });
            }
        ).onErrorResume(
            error -> Mono.just(
                unavailablePreview(
                    safeRequest(request)
                        .normalized(),
                    null
                )
            )
        );
    }

    /**
     * 响应式创建超级管理员。
     *
     * <p>BCrypt 属于 CPU 密集型操作，会在 Reactor 专用线程池执行；
     * 密码哈希完成后才进入数据库响应式事务，避免占用事务连接。</p>
     */
    public Mono<SetupAdminCreateResult> create(
        SetupAdminAccountRequest request
    ) {
        return Mono.defer(
            () -> {
                SetupAdminAccountRequest safeRequest =
                    safeRequest(request).normalized();

                return installStatus()
                    .flatMap(status -> {
                        if (status.installed()) {
                            return Mono.error(
                                new IllegalStateException(
                                    "系统已经安装，禁止重复创建管理员。"
                                )
                            );
                        }

                        if (!status.stateAvailable()) {
                            return Mono.error(
                                new IllegalStateException(
                                    "数据库安装状态暂时不可用，不能创建管理员。"
                                )
                            );
                        }

                        String validationMessage =
                            safeRequest.validateMessage();

                        if (validationMessage != null) {
                            return Mono.error(
                                new IllegalStateException(
                                    validationMessage
                                )
                            );
                        }

                        DatabaseSettings settings =
                            settingsService
                                .current()
                                .normalized();

                        return encodePassword(
                            safeRequest.password()
                        ).flatMap(passwordHash ->
                            accountStore.create(
                                settings,
                                safeRequest,
                                passwordHash
                            )
                        );
                    })
                    .map(userId ->
                        new SetupAdminCreateResult(
                            false,
                            true,
                            userId,
                            safeRequest.username(),
                            safeRequest.email(),
                            safeRequest.displayName(),
                            ROLE_KEY,
                            "超级管理员账号创建完成。"
                                + "下一步可以提交安装完成状态。"
                        )
                    );
            }
        ).onErrorMap(
            error -> {
                if (error instanceof IllegalStateException) {
                    return error;
                }

                return new IllegalStateException(
                    "管理员账号创建失败，请稍后重试。",
                    error
                );
            }
        );
    }

    private Mono<AuthoritativeInstallStatus> installStatus() {
        return installStatusService.current();
    }

    private Mono<String> encodePassword(
        String password
    ) {
        return Mono.fromCallable(
            () -> passwordEncoder.encode(password)
        ).subscribeOn(
            Schedulers.boundedElastic()
        );
    }

    private SetupAdminPreview databasePreview(
        SetupAdminAccountRequest request,
        DatabaseSettings settings,
        SetupAdminDatabaseState state
    ) {
        boolean canCreate =
            state.coreTablesReady()
                && state.initializing()
                && !state.adminExists();

        String note;

        if (!state.coreTablesReady()) {
            note =
                "数据库核心表尚未初始化，"
                    + "请先执行数据库初始化。";
        } else if (!state.initializing()) {
            note =
                "当前数据库不处于初始化状态，"
                    + "不能创建首个管理员。";
        } else if (state.adminExists()) {
            note =
                "已经存在超级管理员，不能重复创建。";
        } else {
            note =
                "可以创建超级管理员账号。";
        }

        return preview(
            request,
            settings,
            true,
            state.coreTablesReady(),
            state.adminExists(),
            canCreate,
            note,
            null
        );
    }

    private SetupAdminPreview installedPreview(
        SetupAdminAccountRequest request
    ) {
        return new SetupAdminPreview(
            true,
            false,
            false,
            false,
            false,
            request.username(),
            request.email(),
            "",
            "",
            "",
            "系统已经安装，禁止重复创建管理员。",
            null
        );
    }

    private SetupAdminPreview invalidPreview(
        SetupAdminAccountRequest request,
        String message
    ) {
        return new SetupAdminPreview(
            false,
            false,
            false,
            false,
            false,
            request.username(),
            request.email(),
            "",
            "",
            "",
            message,
            message
        );
    }

    private SetupAdminPreview unavailablePreview(
        SetupAdminAccountRequest request,
        DatabaseSettings settings
    ) {
        return preview(
            request,
            settings,
            false,
            false,
            false,
            false,
            "管理员创建预览失败。",
            "数据库暂时不可用，"
                + "请检查数据库服务和连接配置。"
        );
    }

    private SetupAdminPreview preview(
        SetupAdminAccountRequest request,
        DatabaseSettings settings,
        boolean connected,
        boolean coreTablesReady,
        boolean adminExists,
        boolean canCreate,
        String note,
        String errorMessage
    ) {
        return new SetupAdminPreview(
            false,
            connected,
            coreTablesReady,
            adminExists,
            canCreate,
            request.username(),
            request.email(),
            table(settings, "users"),
            table(settings, "roles"),
            table(settings, "user_roles"),
            note,
            errorMessage
        );
    }

    private String table(
        DatabaseSettings settings,
        String logicalName
    ) {
        if (settings == null) {
            return "";
        }

        return TableNameResolver.tableName(
            settings.tablePrefix(),
            logicalName
        );
    }

    private SetupAdminAccountRequest safeRequest(
        SetupAdminAccountRequest request
    ) {
        if (request != null) {
            return request;
        }

        return new SetupAdminAccountRequest(
            "admin",
            "",
            "",
            "管理员"
        );
    }
}
