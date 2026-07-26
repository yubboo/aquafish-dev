package com.aquafish.core.admin.auth;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.install.AuthoritativeInstallStatusService;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 后台登录服务。
 *
 * Step 17-28-4：
 * 后台登录查询、登录状态更新和登录审计全部迁移到 R2DBC。
 *
 * 响应式规则：
 * 1. 在线登录链路只使用 DatabaseClient；
 * 2. 登录成功更新与成功审计处于同一个 R2DBC 事务；
 * 3. 登录失败审计独立写入，写入完成后再返回登录错误；
 * 4. BCrypt 校验放到 boundedElastic，避免占用 Netty 事件循环；
 * 5. 禁止阻塞式 JDBC 在线访问和显式阻塞调用。
 *
 * 当前 Token 策略仍保持内存 Token：
 * 1. 后端重启后 Token 自动失效；
 * 2. 后续可以升级为数据库 Token、Redis Token或 JWT。
 */
@Service
public class AdminAuthService {

    private static final long NORMAL_EXPIRE_HOURS = 12;

    private static final long REMEMBER_ME_EXPIRE_HOURS = 24 * 7;

    private final DatabaseRuntimeSettingsService
        databaseRuntimeSettingsService;

    private final DatabaseClient databaseClient;

    private final TransactionalOperator transactionalOperator;

    private final AuthoritativeInstallStatusService installStatusService;

    private final PasswordEncoder passwordEncoder =
        new BCryptPasswordEncoder();

    private final SecureRandom secureRandom =
        new SecureRandom();

    private final AdminAuthSessionStore sessionStore;

    @Autowired
    public AdminAuthService(
        DatabaseRuntimeSettingsService
            databaseRuntimeSettingsService,
        DatabaseClient databaseClient,
        TransactionalOperator transactionalOperator,
        AuthoritativeInstallStatusService installStatusService,
        AdminAuthSessionStore sessionStore
    ) {
        this.databaseRuntimeSettingsService =
            databaseRuntimeSettingsService;

        this.databaseClient =
            databaseClient;

        this.transactionalOperator =
            transactionalOperator;

        this.installStatusService =
            installStatusService;

        this.sessionStore = sessionStore;
    }

    public AdminAuthService(
        DatabaseRuntimeSettingsService databaseRuntimeSettingsService,
        DatabaseClient databaseClient,
        TransactionalOperator transactionalOperator,
        AuthoritativeInstallStatusService installStatusService
    ) {
        this(
            databaseRuntimeSettingsService,
            databaseClient,
            transactionalOperator,
            installStatusService,
            new InMemoryAdminAuthSessionStore()
        );
    }

    /**
     * 保留非 HTTP 场景的登录入口。
     */
    public Mono<AdminAuthToken> login(
        AdminLoginRequest request
    ) {
        return login(
            request,
            AdminLoginMetadata.empty()
        );
    }

    /**
     * 后台登录。
     *
     * 从订阅开始才读取安装状态和数据库配置，
     * 不会在 Spring 容器启动阶段主动访问业务表。
     */
    public Mono<AdminAuthToken> login(
        AdminLoginRequest request,
        AdminLoginMetadata metadata
    ) {
        return requireInstalled().then(Mono.defer(() -> {
            AdminLoginRequest safeRequest =
                safeRequest(request)
                    .normalized();

            AdminLoginMetadata safeMetadata =
                metadata == null
                    ? AdminLoginMetadata.empty()
                    : metadata.normalized();

            String validateMessage =
                safeRequest.validateMessage();

            if (validateMessage != null) {
                return Mono.error(
                    new IllegalStateException(
                        validateMessage
                    )
                );
            }

            DatabaseSettings settings =
                databaseRuntimeSettingsService
                    .current()
                    .normalized();

            return authenticate(
                settings,
                safeRequest,
                safeMetadata
            );
        })).onErrorMap(error -> {
            if (
                error instanceof
                    IllegalStateException
            ) {
                return error;
            }

            return new IllegalStateException(
                "后台登录失败："
                    + rootMessage(error),
                error
            );
        });
    }

    /**
     * 查询账号并依次完成状态、密码、角色和后台权限校验。
     */
    private Mono<AdminAuthToken> authenticate(
        DatabaseSettings settings,
        AdminLoginRequest request,
        AdminLoginMetadata metadata
    ) {
        return findUser(
            settings,
            request.username()
        )
            .switchIfEmpty(
                Mono.defer(() ->
                    rejectLogin(
                        settings,
                        null,
                        request.username(),
                        "USER_NOT_FOUND",
                        metadata,
                        "用户名或密码错误。"
                    )
                )
            )
            .flatMap(userRow -> {
                if (
                    !"ACTIVE".equalsIgnoreCase(
                        userRow.status()
                    )
                ) {
                    return rejectLogin(
                        settings,
                        userRow.id(),
                        request.username(),
                        "ACCOUNT_STATUS_"
                            + userRow.status(),
                        metadata,
                        "账号已被禁用，不能登录后台。"
                    );
                }

                return passwordMatches(
                    request.password(),
                    userRow.passwordHash()
                ).flatMap(matches -> {
                    if (!matches) {
                        return rejectLogin(
                            settings,
                            userRow.id(),
                            request.username(),
                            "INVALID_PASSWORD",
                            metadata,
                            "用户名或密码错误。"
                        );
                    }

                    return loadRoles(
                        settings,
                        userRow.id()
                    ).flatMap(roles -> {
                        AdminAuthUser user =
                            toAuthUser(
                                userRow,
                                roles
                            );

                        if (!user.hasAdminAccess()) {
                            return rejectLogin(
                                settings,
                                userRow.id(),
                                request.username(),
                                "NO_ADMIN_ACCESS",
                                metadata,
                                "当前账号没有后台登录权限。"
                            );
                        }

                        return recordSuccessfulLogin(
                            settings,
                            user,
                            request.username(),
                            metadata
                        ).then(
                            Mono.fromSupplier(() ->
                                createSessionToken(
                                    user,
                                    request.rememberMe()
                                )
                            )
                        );
                    });
                });
            });
    }

    /**
     * BCrypt 属于较重的 CPU 计算。
     *
     * 不在 Netty 事件循环线程中直接执行，
     * 避免并发登录时阻塞 WebFlux 请求处理。
     */
    private Mono<Boolean> passwordMatches(
        String rawPassword,
        String passwordHash
    ) {
        return Mono
            .fromCallable(() ->
                passwordEncoder.matches(
                    rawPassword,
                    passwordHash
                )
            )
            .subscribeOn(
                Schedulers.boundedElastic()
            );
    }

    /**
     * 登录成功：
     * 1. 更新 users 最近登录信息；
     * 2. 写入 SUCCESS 登录日志；
     * 3. 两个动作使用同一个响应式事务。
     */
    private Mono<Void> recordSuccessfulLogin(
        DatabaseSettings settings,
        AdminAuthUser user,
        String loginName,
        AdminLoginMetadata metadata
    ) {
        Mono<Void> transactionWork =
            updateLoginSuccess(
                settings,
                user.id(),
                metadata
            ).then(
                writeLoginLog(
                    settings,
                    user.id(),
                    loginName,
                    "SUCCESS",
                    "",
                    metadata
                )
            );

        return transactionalOperator
            .transactional(
                transactionWork
            );
    }

    /**
     * 登录失败日志必须先写入，再向 Controller 返回失败。
     *
     * 失败日志不放进会被业务错误回滚的事务，
     * 否则抛出登录异常时审计记录也会一起回滚。
     */
    private <T> Mono<T> rejectLogin(
        DatabaseSettings settings,
        Long userId,
        String loginName,
        String failureReason,
        AdminLoginMetadata metadata,
        String clientMessage
    ) {
        return writeLoginLog(
            settings,
            userId,
            loginName,
            "FAILED",
            failureReason,
            metadata
        )
            .onErrorMap(error ->
                new IllegalStateException(
                    clientMessage,
                    error
                )
            )
            .then(
                Mono.error(
                    new IllegalStateException(
                        clientMessage
                    )
                )
            );
    }

    private Mono<UserRow> findUserById(
        DatabaseSettings settings,
        long userId
    ) {
        String sql =
            "select "
                + "id, "
                + "username, "
                + "email, "
                + "password_hash, "
                + "display_name, "
                + "avatar, "
                + "status "
                + "from "
                + usersTable(settings)
                + " where id = :userId "
                + "limit 1";

        return databaseClient
            .sql(sql)
            .bind("userId", userId)
            .map((row, rowMetadata) ->
                new UserRow(
                    requiredLong(row.get("id", Long.class), "用户 ID"),
                    emptyToString(row.get("username", String.class)),
                    emptyToString(row.get("email", String.class)),
                    emptyToString(row.get("password_hash", String.class)),
                    emptyToString(row.get("display_name", String.class)),
                    emptyToString(row.get("avatar", String.class)),
                    emptyToString(row.get("status", String.class))
                )
            )
            .one();
    }

    /**
     * 使用 R2DBC 查询用户账号。
     */
    private Mono<UserRow> findUser(
        DatabaseSettings settings,
        String usernameOrEmail
    ) {
        String sql =
            "select "
                + "id, "
                + "username, "
                + "email, "
                + "password_hash, "
                + "display_name, "
                + "avatar, "
                + "status "
                + "from "
                + usersTable(settings)
                + " where username = :loginName "
                + "or email = :loginName "
                + "limit 1";

        return databaseClient
            .sql(sql)
            .bind(
                "loginName",
                usernameOrEmail
            )
            .map((row, rowMetadata) ->
                new UserRow(
                    requiredLong(
                        row.get(
                            "id",
                            Long.class
                        ),
                        "用户 ID"
                    ),
                    emptyToString(
                        row.get(
                            "username",
                            String.class
                        )
                    ),
                    emptyToString(
                        row.get(
                            "email",
                            String.class
                        )
                    ),
                    emptyToString(
                        row.get(
                            "password_hash",
                            String.class
                        )
                    ),
                    emptyToString(
                        row.get(
                            "display_name",
                            String.class
                        )
                    ),
                    emptyToString(
                        row.get(
                            "avatar",
                            String.class
                        )
                    ),
                    emptyToString(
                        row.get(
                            "status",
                            String.class
                        )
                    )
                )
            )
            .one();
    }

    /**
     * 使用 R2DBC 查询用户角色。
     */
    private Mono<List<String>> loadRoles(
        DatabaseSettings settings,
        long userId
    ) {
        String sql =
            "select r.role_key "
                + "from "
                + rolesTable(settings)
                + " r "
                + "join "
                + userRolesTable(settings)
                + " ur "
                + "on ur.role_id = r.id "
                + "where ur.user_id = :userId "
                + "order by r.id asc";

        return databaseClient
            .sql(sql)
            .bind(
                "userId",
                userId
            )
            .map((row, rowMetadata) ->
                emptyToString(
                    row.get(
                        "role_key",
                        String.class
                    )
                )
            )
            .all()
            .filter(role ->
                !role.isBlank()
            )
            .collectList()
            .map(values ->
                List.copyOf(values)
            );
    }

    private AdminAuthUser toAuthUser(
        UserRow userRow,
        List<String> roles
    ) {
        List<String> safeRoles =
            roles == null
                ? List.of()
                : List.copyOf(roles);

        return new AdminAuthUser(
            userRow.id(),
            userRow.username(),
            userRow.email(),
            userRow.displayName(),
            userRow.avatar(),
            userRow.status(),
            safeRoles,
            safeRoles.contains(
                "super_admin"
            )
        );
    }

    /**
     * 更新用户最后登录信息。
     *
     * Step 17-27 已经保证这些字段存在，
     * 在线登录阶段不再使用 JDBC Metadata 每次探测字段。
     */
    private Mono<Void> updateLoginSuccess(
        DatabaseSettings settings,
        long userId,
        AdminLoginMetadata metadata
    ) {
        String sql =
            "update "
                + usersTable(settings)
                + " set "
                + "last_login_at = current_timestamp, "
                + "last_login_ip = :lastLoginIp, "
                + "last_user_agent = :lastUserAgent, "
                + "login_count = "
                + "coalesce(login_count, 0) + 1 "
                + "where id = :userId";

        DatabaseClient.GenericExecuteSpec spec =
            databaseClient
                .sql(sql)
                .bind(
                    "userId",
                    userId
                );

        spec = bindNullableText(
            spec,
            "lastLoginIp",
            limitText(
                metadata.clientIp(),
                45
            )
        );

        spec = bindNullableText(
            spec,
            "lastUserAgent",
            limitText(
                metadata.userAgent(),
                500
            )
        );

        return spec
            .fetch()
            .rowsUpdated()
            .then();
    }

    /**
     * 写入登录成功或失败审计日志。
     */
    private Mono<Void> writeLoginLog(
        DatabaseSettings settings,
        Long userId,
        String loginName,
        String loginResult,
        String failureReason,
        AdminLoginMetadata metadata
    ) {
        String sql =
            "insert into "
                + loginLogsTable(settings)
                + " ("
                + "user_id, "
                + "login_name, "
                + "login_result, "
                + "failure_reason, "
                + "ip_address, "
                + "remote_address, "
                + "x_forwarded_for, "
                + "x_real_ip, "
                + "user_agent, "
                + "created_at"
                + ") values ("
                + ":userId, "
                + ":loginName, "
                + ":loginResult, "
                + ":failureReason, "
                + ":ipAddress, "
                + ":remoteAddress, "
                + ":xForwardedFor, "
                + ":xRealIp, "
                + ":userAgent, "
                + "current_timestamp"
                + ")";

        DatabaseClient.GenericExecuteSpec spec =
            databaseClient
                .sql(sql)
                .bind(
                    "loginName",
                    limitText(
                        loginName,
                        191
                    )
                )
                .bind(
                    "loginResult",
                    limitText(
                        loginResult,
                        32
                    )
                );

        spec = bindNullableLong(
            spec,
            "userId",
            userId
        );

        spec = bindNullableText(
            spec,
            "failureReason",
            limitText(
                failureReason,
                500
            )
        );

        spec = bindNullableText(
            spec,
            "ipAddress",
            limitText(
                metadata.clientIp(),
                45
            )
        );

        spec = bindNullableText(
            spec,
            "remoteAddress",
            limitText(
                metadata.remoteAddress(),
                45
            )
        );

        spec = bindNullableText(
            spec,
            "xForwardedFor",
            limitText(
                metadata.xForwardedFor(),
                500
            )
        );

        spec = bindNullableText(
            spec,
            "xRealIp",
            limitText(
                metadata.xRealIp(),
                45
            )
        );

        spec = bindNullableText(
            spec,
            "userAgent",
            limitText(
                metadata.userAgent(),
                500
            )
        );

        return spec
            .fetch()
            .rowsUpdated()
            .then();
    }

    private DatabaseClient.GenericExecuteSpec
        bindNullableLong(
            DatabaseClient.GenericExecuteSpec spec,
            String name,
            Long value
        ) {
        if (value == null) {
            return spec.bindNull(
                name,
                Long.class
            );
        }

        return spec.bind(
            name,
            value
        );
    }

    private DatabaseClient.GenericExecuteSpec
        bindNullableText(
            DatabaseClient.GenericExecuteSpec spec,
            String name,
            String value
        ) {
        if (
            value == null
                || value.isBlank()
        ) {
            return spec.bindNull(
                name,
                String.class
            );
        }

        return spec.bind(
            name,
            value
        );
    }

    /**
     * 创建并保存内存登录会话。
     *
     * 只有数据库成功事务提交后才执行。
     */
    private AdminAuthToken createSessionToken(
        AdminAuthUser user,
        boolean rememberMe
    ) {
        cleanExpiredSessions();

        String token =
            generateToken();

        long hours =
            rememberMe
                ? REMEMBER_ME_EXPIRE_HOURS
                : NORMAL_EXPIRE_HOURS;

        LocalDateTime expiresAt =
            LocalDateTime
                .now()
                .plusHours(hours);

        sessionStore.save(
            new AdminAuthSession(
                token,
                user,
                expiresAt
            )
        );

        return new AdminAuthToken(
            "Bearer",
            token,
            expiresAt.toString(),
            Math.max(
                0,
                Duration
                    .between(
                        LocalDateTime.now(),
                        expiresAt
                    )
                    .toSeconds()
            ),
            user
        );
    }

    /**
     * 后台接口统一鉴权使用。
     */
    public Mono<Boolean> isAuthorized(
        String authorization
    ) {
        return requireInstalled()
            .then(
                Mono.fromSupplier(() -> {
                    AdminAuthSession session =
                        resolveSession(authorization);

                    return session != null
                        && session.user() != null
                        && session.user().hasAdminAccess();
                })
            )
            .onErrorReturn(false);
    }

    /**
     * 受信签发：基于已验证的管理员用户 ID 和角色，跳过密码校验直接创建后台会话。
     *
     * <p>该入口不接受浏览器提交的身份结论，内部会按 userId 重新查询数据库确认
     * 账号状态和管理权限。只有状态为 ACTIVE 且拥有 admin/super_admin 角色时
     * 才会签发 Token。</p>
     *
     * @param userId       已验证的管理员用户数据库主键
     * @param loginName   登录审计用的登录名称
     * @param metadata    客户端网络与设备信息
     * @param rememberMe  是否使用长有效期
     * @return 新签发的后台会话 Token
     */
    public Mono<AdminAuthToken> issueTrustedToken(
        long userId,
        String loginName,
        AdminLoginMetadata metadata,
        boolean rememberMe
    ) {
        if (userId <= 0L) {
            return Mono.error(new IllegalStateException("受信用户 ID 无效。"));
        }

        AdminLoginMetadata safeMetadata = metadata == null
            ? AdminLoginMetadata.empty()
            : metadata.normalized();
        String safeLoginName = loginName == null || loginName.isBlank()
            ? "trusted_bridge"
            : loginName.trim();

        return requireInstalled().then(Mono.defer(() -> {
            DatabaseSettings settings = databaseRuntimeSettingsService.current().normalized();
            return findUserById(settings, userId)
                .switchIfEmpty(Mono.error(new IllegalStateException("受信用户不存在。")))
                .flatMap(userRow -> {
                    if (!"ACTIVE".equalsIgnoreCase(userRow.status())) {
                        return Mono.error(new IllegalStateException("当前账号被禁用，无法进入后台。"));
                    }
                    return loadRoles(settings, userId)
                        .flatMap(roles -> {
                            AdminAuthUser user = toAuthUser(userRow, roles);
                            if (!user.hasAdminAccess()) {
                                return Mono.error(new IllegalStateException("当前账号没有后台管理权限。"));
                            }
                            return recordSuccessfulLogin(settings, user, safeLoginName, safeMetadata)
                                .then(Mono.fromSupplier(() ->
                                    createSessionToken(user, rememberMe)));
                        });
                });
        })).onErrorMap(error -> error instanceof IllegalStateException
            ? error
            : new IllegalStateException("受信后台会话签发失败。", error));
    }

    /**
     * 当前登录用户。
     */
    public Mono<AdminAuthUser> me(
        String authorization
    ) {
        return requireInstalled()
            .then(Mono.fromCallable(() -> {
                AdminAuthSession session =
                    resolveSession(authorization);

                if (session == null) {
                    throw new IllegalStateException(
                        "登录已过期，请重新登录。"
                    );
                }

                return session.user();
            }));
    }

    /**
     * 立即撤销指定用户的全部后台登录会话。
     */
    public int revokeUserSessions(
        long userId
    ) {
        if (userId <= 0) {
            return 0;
        }

        return sessionStore.deleteByUserId(userId);
    }

    /**
     * 退出登录。
     */
    public AdminLogoutResult logout(
        String authorization
    ) {
        String token =
            optionalBearerToken(
                authorization
            );

        if (
            token == null
                || token.isBlank()
        ) {
            return new AdminLogoutResult(
                false,
                "当前没有有效登录会话。"
            );
        }

        AdminAuthSession removed =
            sessionStore.delete(token);

        return new AdminLogoutResult(
            removed != null,
            removed != null
                ? "退出登录成功。"
                : "当前登录会话不存在或已过期。"
        );
    }

    private AdminAuthSession resolveSession(
        String authorization
    ) {
        String token =
            requireBearerToken(
                authorization
            );

        AdminAuthSession session =
            sessionStore.find(token);

        if (session == null) {
            return null;
        }

        if (session.expired()) {
            sessionStore.delete(token);
            return null;
        }

        return session;
    }

    private Mono<Void> requireInstalled() {
        return installStatusService.requireInstalled()
            .onErrorMap(error ->
                new IllegalStateException(
                    "系统尚未完成安装，不能登录后台。",
                    error
                )
            );
    }

    private AdminLoginRequest safeRequest(
        AdminLoginRequest request
    ) {
        if (request == null) {
            return new AdminLoginRequest(
                "",
                "",
                false
            );
        }

        return request;
    }

    private String requireBearerToken(
        String authorization
    ) {
        String token =
            optionalBearerToken(
                authorization
            );

        if (
            token == null
                || token.isBlank()
        ) {
            throw new IllegalStateException(
                "未登录，请先登录。"
            );
        }

        return token;
    }

    private String optionalBearerToken(
        String authorization
    ) {
        if (
            authorization == null
                || authorization.isBlank()
        ) {
            return null;
        }

        String value =
            authorization.trim();

        if (
            value.regionMatches(
                true,
                0,
                "Bearer ",
                0,
                7
            )
        ) {
            return value
                .substring(7)
                .trim();
        }

        return value;
    }

    private String generateToken() {
        byte[] bytes =
            new byte[32];

        secureRandom.nextBytes(bytes);

        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes);
    }

    private void cleanExpiredSessions() {
        sessionStore.deleteExpired();
    }

    private String tablePrefix(
        DatabaseSettings settings
    ) {
        return TableNameResolver
            .normalizeConfiguredPrefix(
                settings.tablePrefix()
            );
    }

    private String usersTable(
        DatabaseSettings settings
    ) {
        return tablePrefix(settings)
            + "users";
    }

    private String rolesTable(
        DatabaseSettings settings
    ) {
        return tablePrefix(settings)
            + "roles";
    }

    private String userRolesTable(
        DatabaseSettings settings
    ) {
        return tablePrefix(settings)
            + "user_roles";
    }

    private String loginLogsTable(
        DatabaseSettings settings
    ) {
        return tablePrefix(settings)
            + "user_login_logs";
    }

    private long requiredLong(
        Long value,
        String fieldName
    ) {
        if (value == null) {
            throw new IllegalStateException(
                fieldName
                    + "不能为空。"
            );
        }

        return value;
    }

    private String emptyToString(
        String value
    ) {
        return value == null
            ? ""
            : value;
    }

    private String limitText(
        String value,
        int maxLength
    ) {
        if (value == null) {
            return "";
        }

        String result =
            value.trim();

        if (
            result.length()
                <= maxLength
        ) {
            return result;
        }

        return result.substring(
            0,
            maxLength
        );
    }

    private String rootMessage(
        Throwable error
    ) {
        if (error == null) {
            return "未知错误";
        }

        Throwable current =
            error;

        while (
            current.getCause() != null
        ) {
            current =
                current.getCause();
        }

        String message =
            current.getMessage();

        if (
            message == null
                || message.isBlank()
        ) {
            return current
                .getClass()
                .getName();
        }

        return message;
    }

    private record UserRow(
        long id,
        String username,
        String email,
        String passwordHash,
        String displayName,
        String avatar,
        String status
    ) {
    }
}
