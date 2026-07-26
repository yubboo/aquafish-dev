package com.aquafish.user.auth;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import com.aquafish.core.install.AuthoritativeInstallStatusService;
import io.r2dbc.spi.Row;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 前台会员数据库会话认证服务。
 *
 * <p>保护资产是会员身份、用户组权限和论坛发布权。服务只信任数据库中的用户、
 * 用户组、封禁与 {@code user_sessions}；不信任浏览器提交的 userId、角色、
 * 权限或封禁状态。原始会话令牌只返回一次供 HttpOnly Cookie 使用，
 * 数据库只保存 SHA-256 摘要，日志不记录密码、令牌和摘要。</p>
 *
 * <p>失败时默认拒绝认证，不降级到匿名伪会员或内存管理员会话。
 * BCrypt 在 boundedElastic 执行；用户登录摘要、成功日志和会话创建位于同一个
 * R2DBC 事务。关联测试覆盖摘要存储、退出撤销、封禁和权限装配。</p>
 */
@Service
public class MemberAuthService {

    private static final Duration NORMAL_SESSION_TTL = Duration.ofHours(12);
    private static final Duration REMEMBERED_SESSION_TTL = Duration.ofDays(30);
    private static final String SESSION_TYPE = "WEB";

    private final DatabaseRuntimeSettingsService databaseSettings;
    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactions;
    private final AuthoritativeInstallStatusService installStatusService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public MemberAuthService(
        DatabaseRuntimeSettingsService databaseSettings,
        DatabaseClient databaseClient,
        TransactionalOperator transactions,
        AuthoritativeInstallStatusService installStatusService
    ) {
        this(
            databaseSettings,
            databaseClient,
            transactions,
            installStatusService,
            new BCryptPasswordEncoder(),
            new SecureRandom(),
            Clock.systemUTC()
        );
    }

    MemberAuthService(
        DatabaseRuntimeSettingsService databaseSettings,
        DatabaseClient databaseClient,
        TransactionalOperator transactions,
        AuthoritativeInstallStatusService installStatusService,
        PasswordEncoder passwordEncoder,
        SecureRandom secureRandom,
        Clock clock
    ) {
        this.databaseSettings = databaseSettings;
        this.databaseClient = databaseClient;
        this.transactions = transactions;
        this.installStatusService = installStatusService;
        this.passwordEncoder = passwordEncoder;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    /**
     * 校验用户名/邮箱和 BCrypt 密码，创建数据库会话。
     */
    public Mono<MemberAuthToken> login(
        MemberLoginRequest source,
        MemberLoginMetadata metadata
    ) {
        return installStatusService.requireInstalled()
            .then(Mono.defer(() -> {
                MemberLoginRequest request = source == null
                    ? new MemberLoginRequest("", "", false)
                    : source.normalized();
                String validation = request.validateMessage();
                if (validation != null) {
                    return Mono.error(new IllegalStateException(validation));
                }

                MemberLoginMetadata safeMetadata = metadata == null
                    ? MemberLoginMetadata.empty()
                    : metadata.normalized();
                DatabaseSettings settings = databaseSettings.current().normalized();

                return findLoginUser(settings, request.loginName())
                    .switchIfEmpty(rejectLogin(
                        settings,
                        null,
                        request.loginName(),
                        "USER_NOT_FOUND",
                        safeMetadata,
                        "用户名或密码错误。"
                    ))
                    .flatMap(user -> authenticateForLogin(
                        settings,
                        user,
                        request,
                        safeMetadata
                    ));
            }))
            .onErrorMap(error -> error instanceof IllegalStateException
                ? error
                : new IllegalStateException("会员登录暂时不可用，请稍后重试。", error)
            );
    }

    /**
     * 为已经通过服务端高可信认证的同一用户签发独立前台 WEB 会话。
     *
     * <p>该入口不接受浏览器身份结论，也不会复用或转换后台令牌；它会按内部用户 ID
     * 重新检查账号状态、完整身份字段和登录封禁，再写入新的数据库会话。</p>
     */
    public Mono<MemberAuthToken> issueTrustedWebSession(
        long userId,
        MemberLoginMetadata metadata,
        Duration maximumTtl
    ) {
        if (userId <= 0L) {
            return Mono.error(new IllegalStateException("可信用户 ID 无效。"));
        }
        Duration ttl = trustedSessionTtl(maximumTtl);
        MemberLoginMetadata safeMetadata = metadata == null
            ? MemberLoginMetadata.empty()
            : metadata.normalized();

        return installStatusService.requireInstalled()
            .then(Mono.defer(() -> {
                DatabaseSettings settings = databaseSettings.current().normalized();
                return findUserById(settings, userId)
                    .switchIfEmpty(Mono.error(new IllegalStateException("管理员账号不存在。")))
                    .flatMap(user -> {
                        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
                            return Mono.error(new IllegalStateException("当前管理员账号不可用。"));
                        }
                        if (user.uid() <= 0L || user.publicId().isBlank()) {
                            return Mono.error(new IllegalStateException(
                                "管理员会员身份不完整，请完成数据库升级后重试。"
                            ));
                        }
                        return hasActiveBan(settings, user.id(), Set.of("login", "all"))
                            .flatMap(banned -> banned
                                ? Mono.error(new IllegalStateException("当前管理员账号不可用。"))
                                : issueTrustedSession(settings, user, safeMetadata, ttl));
                    });
            }))
            .onErrorMap(error -> error instanceof IllegalStateException
                ? error
                : new IllegalStateException("前台会员会话签发失败。", error));
    }

    /**
     * 使用原始 Cookie 令牌查找有效会话和当前用户安全状态。
     *
     * <p>用户状态、登录封禁和用户组权限每次从数据库重新读取；
     * 管理员禁用账号、撤销会话或修改权限后无需等待 Token 自身过期。</p>
     */
    public Mono<MemberAuthUser> authenticate(String rawToken) {
        String token = normalizeToken(rawToken);
        if (token == null) {
            return Mono.empty();
        }

        return installStatusService.requireInstalled()
            .then(Mono.defer(() -> {
                DatabaseSettings settings = databaseSettings.current().normalized();
                return findSessionUser(settings, tokenHash(token))
                    .flatMap(user -> assembleUser(settings, user));
            }));
    }

    /**
     * 当前会员接口的语义别名。
     */
    public Mono<MemberAuthUser> me(String rawToken) {
        return authenticate(rawToken);
    }

    /**
     * 撤销当前前台会话；退出请求重复执行仍保持幂等。
     */
    public Mono<MemberLogoutResult> logout(String rawToken) {
        String token = normalizeToken(rawToken);
        if (token == null) {
            return Mono.just(new MemberLogoutResult(false));
        }

        DatabaseSettings settings = databaseSettings.current().normalized();
        String sql = "update " + table(settings, TableNames.USER_SESSIONS)
            + " set revoked_at = current_timestamp(3), revoke_reason = :reason "
            + "where token_hash = :tokenHash and session_type = :sessionType "
            + "and revoked_at is null";

        return databaseClient.sql(sql)
            .bind("reason", "MEMBER_LOGOUT")
            .bind("tokenHash", tokenHash(token))
            .bind("sessionType", SESSION_TYPE)
            .fetch()
            .rowsUpdated()
            .map(rows -> new MemberLogoutResult(rows != null && rows > 0L));
    }

    private Mono<MemberAuthToken> authenticateForLogin(
        DatabaseSettings settings,
        LoginUserRow user,
        MemberLoginRequest request,
        MemberLoginMetadata metadata
    ) {
        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            return rejectLogin(
                settings,
                user.id(),
                request.loginName(),
                "ACCOUNT_STATUS_" + user.status(),
                metadata,
                "当前账号不可用。"
            );
        }

        return hasActiveBan(settings, user.id(), Set.of("login", "all"))
            .flatMap(loginBanned -> {
                if (loginBanned) {
                    return rejectLogin(
                        settings,
                        user.id(),
                        request.loginName(),
                        "LOGIN_BANNED",
                        metadata,
                        "当前账号不可用。"
                    );
                }
                return passwordMatches(request.password(), user.passwordHash())
                    .flatMap(matches -> matches
                        ? issueSession(settings, user, request, metadata)
                        : rejectLogin(
                            settings,
                            user.id(),
                            request.loginName(),
                            "INVALID_PASSWORD",
                            metadata,
                            "用户名或密码错误。"
                        )
                    );
            });
    }

    private Mono<MemberAuthToken> issueSession(
        DatabaseSettings settings,
        LoginUserRow user,
        MemberLoginRequest request,
        MemberLoginMetadata metadata
    ) {
        Duration ttl = request.rememberMe()
            ? REMEMBERED_SESSION_TTL
            : NORMAL_SESSION_TTL;
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plus(ttl);
        String rawToken = createRawToken();

        return assembleUser(settings, user)
            .flatMap(authUser -> {
                Mono<Void> transaction = updateLoginSuccess(
                        settings,
                        user.id(),
                        metadata
                    )
                    .then(insertSession(
                        settings,
                        user.id(),
                        rawToken,
                        metadata,
                        expiresAt
                    ))
                    .then(writeLoginLog(
                        settings,
                        user.id(),
                        request.loginName(),
                        "SUCCESS",
                        "",
                        metadata
                    ));

                return transactions.transactional(transaction)
                    .thenReturn(new MemberAuthToken(
                        rawToken,
                        expiresAt.toString(),
                        ttl.toSeconds(),
                        authUser
                    ));
            });
    }

    private Mono<MemberAuthUser> assembleUser(
        DatabaseSettings settings,
        LoginUserRow user
    ) {
        return Mono.zip(
                loadPermissions(settings, user.groupId()),
                hasActiveBan(settings, user.id(), Set.of("post", "all")),
                loadRoles(settings, user.id())
            )
            .map(values -> new MemberAuthUser(
                user.id(),
                user.uid(),
                user.publicId(),
                user.username(),
                user.displayName().isBlank() ? user.username() : user.displayName(),
                user.avatar(),
                user.groupId(),
                user.groupKey(),
                values.getT3(),
                values.getT1(),
                values.getT2()
            ));
    }

    private Mono<MemberAuthToken> issueTrustedSession(
        DatabaseSettings settings,
        LoginUserRow user,
        MemberLoginMetadata metadata,
        Duration ttl
    ) {
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(ttl);
        String rawToken = createRawToken();
        return assembleUser(settings, user)
            .flatMap(authUser -> transactions.transactional(
                    insertSession(settings, user.id(), rawToken, metadata, expiresAt)
                        .then(writeLoginLog(
                            settings,
                            user.id(),
                            user.username(),
                            "SUCCESS",
                            "ADMIN_SESSION_BRIDGE",
                            metadata
                        ))
                )
                .thenReturn(new MemberAuthToken(
                    rawToken,
                    expiresAt.toString(),
                    ttl.toSeconds(),
                    authUser
                )));
    }

    private Duration trustedSessionTtl(Duration maximumTtl) {
        if (maximumTtl == null || maximumTtl.isZero() || maximumTtl.isNegative()) {
            throw new IllegalStateException("后台会话有效期无效。前台会话未创建。");
        }
        return maximumTtl.compareTo(NORMAL_SESSION_TTL) > 0
            ? NORMAL_SESSION_TTL
            : maximumTtl;
    }

    private Mono<LoginUserRow> findUserById(
        DatabaseSettings settings,
        long userId
    ) {
        String sql = "select u.id, u.uid, u.public_id, u.username, u.password_hash, "
            + "coalesce(u.display_name, '') as display_name, "
            + "coalesce(u.avatar, '') as avatar, u.status, u.group_id, "
            + "coalesce(g.group_key, '') as group_key "
            + "from " + table(settings, TableNames.USERS) + " u "
            + "left join " + table(settings, TableNames.USER_GROUPS)
            + " g on g.id = u.group_id where u.id = :userId limit 1";

        return databaseClient.sql(sql)
            .bind("userId", userId)
            .map((row, metadata) -> mapLoginUser(row))
            .one();
    }

    private Mono<LoginUserRow> findLoginUser(
        DatabaseSettings settings,
        String loginName
    ) {
        String sql = "select u.id, u.uid, u.public_id, u.username, u.password_hash, "
            + "coalesce(u.display_name, '') as display_name, "
            + "coalesce(u.avatar, '') as avatar, u.status, u.group_id, "
            + "coalesce(g.group_key, '') as group_key "
            + "from " + table(settings, TableNames.USERS) + " u "
            + "left join " + table(settings, TableNames.USER_GROUPS)
            + " g on g.id = u.group_id "
            + "where u.uid is not null and (lower(u.username) = lower(:loginName) "
            + "or lower(coalesce(u.email, '')) = lower(:loginName)) "
            + "order by u.id asc limit 1";

        return databaseClient.sql(sql)
            .bind("loginName", loginName)
            .map((row, metadata) -> mapLoginUser(row))
            .one();
    }

    private Mono<LoginUserRow> findSessionUser(
        DatabaseSettings settings,
        String hash
    ) {
        String users = table(settings, TableNames.USERS);
        String groups = table(settings, TableNames.USER_GROUPS);
        String sessions = table(settings, TableNames.USER_SESSIONS);
        String bans = table(settings, TableNames.USER_BANS);
        String sql = "select u.id, u.uid, u.public_id, u.username, u.password_hash, "
            + "coalesce(u.display_name, '') as display_name, "
            + "coalesce(u.avatar, '') as avatar, u.status, u.group_id, "
            + "coalesce(g.group_key, '') as group_key "
            + "from " + sessions + " s join " + users + " u on u.id = s.user_id "
            + "left join " + groups + " g on g.id = u.group_id "
            + "where s.token_hash = :tokenHash and s.session_type = :sessionType "
            + "and s.revoked_at is null and s.expires_at > current_timestamp(3) "
            + "and u.uid is not null and upper(u.status) = 'ACTIVE' "
            + "and not exists (select 1 from " + bans + " b "
            + "where b.user_id = u.id and b.enabled = 1 "
            + "and lower(b.ban_type) in ('login', 'all') "
            + "and b.started_at <= current_timestamp(3) "
            + "and (b.expired_at is null or b.expired_at > current_timestamp(3))) "
            + "limit 1";

        return databaseClient.sql(sql)
            .bind("tokenHash", hash)
            .bind("sessionType", SESSION_TYPE)
            .map((row, metadata) -> mapLoginUser(row))
            .one();
    }

    private Mono<Set<String>> loadPermissions(
        DatabaseSettings settings,
        Long groupId
    ) {
        if (groupId == null || groupId <= 0L) {
            return Mono.just(Set.of());
        }

        String sql = "select permission_key from "
            + table(settings, TableNames.USER_GROUP_PERMISSIONS)
            + " where group_id = :groupId order by permission_key";

        return databaseClient.sql(sql)
            .bind("groupId", groupId)
            .map((row, metadata) -> text(row, "permission_key"))
            .all()
            .filter(value -> !value.isBlank())
            .collectList()
            .map(values -> Set.copyOf(values));
    }

    /**
     * 读取当前用户的角色稳定标识，用于服务端判断后台入口和管理权限。
     *
     * <p>角色来自 users → user_roles → roles 真实关系，不接受浏览器提交的角色名。</p>
     */
    private Mono<Set<String>> loadRoles(
        DatabaseSettings settings,
        long userId
    ) {
        String sql = "select distinct r.role_key from "
            + table(settings, TableNames.USER_ROLES) + " ur join "
            + table(settings, TableNames.ROLES) + " r on r.id = ur.role_id "
            + "where ur.user_id = :userId order by r.role_key";

        return databaseClient.sql(sql)
            .bind("userId", userId)
            .map((row, metadata) -> text(row, "role_key"))
            .all()
            .filter(value -> !value.isBlank())
            .collectList()
            .map(Set::copyOf);
    }

    private Mono<Boolean> hasActiveBan(
        DatabaseSettings settings,
        long userId,
        Set<String> types
    ) {
        String allowedTypes = types.contains("login")
            ? "('login', 'all')"
            : "('post', 'all')";
        String sql = "select count(*) as row_count from "
            + table(settings, TableNames.USER_BANS)
            + " where user_id = :userId and enabled = 1 "
            + "and lower(ban_type) in " + allowedTypes
            + " and started_at <= current_timestamp(3) "
            + "and (expired_at is null or expired_at > current_timestamp(3))";

        return databaseClient.sql(sql)
            .bind("userId", userId)
            .map((row, metadata) -> number(row, "row_count") > 0L)
            .one()
            .defaultIfEmpty(false);
    }

    private Mono<Void> updateLoginSuccess(
        DatabaseSettings settings,
        long userId,
        MemberLoginMetadata metadata
    ) {
        String sql = "update " + table(settings, TableNames.USERS)
            + " set last_login_at = current_timestamp(3), last_login_ip = :ipAddress, "
            + "last_user_agent = :userAgent, login_count = login_count + 1, "
            + "updated_at = current_timestamp(3) where id = :userId and upper(status) = 'ACTIVE'";

        return databaseClient.sql(sql)
            .bind("ipAddress", metadata.ipAddress())
            .bind("userAgent", metadata.userAgent())
            .bind("userId", userId)
            .fetch()
            .rowsUpdated()
            .flatMap(rows -> rows != null && rows > 0L
                ? Mono.empty()
                : Mono.error(new IllegalStateException("当前账号状态已变化，请重新登录。"))
            );
    }

    private Mono<Void> insertSession(
        DatabaseSettings settings,
        long userId,
        String rawToken,
        MemberLoginMetadata metadata,
        LocalDateTime expiresAt
    ) {
        String sql = "insert into " + table(settings, TableNames.USER_SESSIONS)
            + " (user_id, session_type, token_hash, ip_address, user_agent, expires_at) "
            + "values (:userId, :sessionType, :tokenHash, :ipAddress, :userAgent, :expiresAt)";

        return databaseClient.sql(sql)
            .bind("userId", userId)
            .bind("sessionType", SESSION_TYPE)
            .bind("tokenHash", tokenHash(rawToken))
            .bind("ipAddress", metadata.ipAddress())
            .bind("userAgent", metadata.userAgent())
            .bind("expiresAt", expiresAt)
            .fetch()
            .rowsUpdated()
            .flatMap(rows -> rows != null && rows > 0L
                ? Mono.empty()
                : Mono.error(new IllegalStateException("会员会话创建失败。"))
            );
    }

    private <T> Mono<T> rejectLogin(
        DatabaseSettings settings,
        Long userId,
        String loginName,
        String reason,
        MemberLoginMetadata metadata,
        String clientMessage
    ) {
        return writeLoginLog(
            settings,
            userId,
            loginName,
            "FAILED",
            reason,
            metadata
        ).then(Mono.error(new IllegalStateException(clientMessage)));
    }

    private Mono<Void> writeLoginLog(
        DatabaseSettings settings,
        Long userId,
        String loginName,
        String result,
        String reason,
        MemberLoginMetadata metadata
    ) {
        String sql = "insert into " + table(settings, TableNames.USER_LOGIN_LOGS)
            + " (user_id, login_name, login_result, failure_reason, ip_address, "
            + "remote_address, x_forwarded_for, x_real_ip, user_agent) "
            + "values (:userId, :loginName, :result, :reason, :ipAddress, "
            + ":remoteAddress, :forwardedFor, :realIp, :userAgent)";

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        spec = userId == null
            ? spec.bindNull("userId", Long.class)
            : spec.bind("userId", userId);
        return spec
            .bind("loginName", safe(loginName, 191))
            .bind("result", result)
            .bind("reason", safe(reason, 500))
            .bind("ipAddress", metadata.ipAddress())
            .bind("remoteAddress", metadata.ipAddress())
            .bind("forwardedFor", "")
            .bind("realIp", "")
            .bind("userAgent", metadata.userAgent())
            .fetch()
            .rowsUpdated()
            .then();
    }

    /**
     * BCrypt 是 CPU 密集型操作，不能占用 Netty 事件循环。
     */
    private Mono<Boolean> passwordMatches(String rawPassword, String passwordHash) {
        return Mono.fromCallable(() -> passwordEncoder.matches(rawPassword, passwordHash))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private LoginUserRow mapLoginUser(Row row) {
        return new LoginUserRow(
            number(row, "id"),
            number(row, "uid"),
            text(row, "public_id"),
            text(row, "username"),
            text(row, "password_hash"),
            text(row, "display_name"),
            text(row, "avatar"),
            text(row, "status"),
            nullableNumber(row, "group_id"),
            text(row, "group_key")
        );
    }

    private String createRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String tokenHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                digest.digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("当前 Java 运行环境不支持 SHA-256。", error);
        }
    }

    private String normalizeToken(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        String token = rawToken.strip();
        return token.isEmpty() || token.length() > 256 ? null : token;
    }

    private String table(DatabaseSettings settings, String logicalName) {
        return TableNameResolver.tableName(settings.tablePrefix(), logicalName);
    }

    private long number(Row row, String column) {
        Number value = row.get(column, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private Long nullableNumber(Row row, String column) {
        Number value = row.get(column, Number.class);
        return value == null ? null : value.longValue();
    }

    private String text(Row row, String column) {
        String value = row.get(column, String.class);
        return value == null ? "" : value;
    }

    private String safe(String value, int maxLength) {
        String result = value == null ? "" : value
            .replace("\r", "")
            .replace("\n", "")
            .strip();
        return result.length() <= maxLength
            ? result
            : result.substring(0, maxLength);
    }

    private record LoginUserRow(
        long id,
        long uid,
        String publicId,
        String username,
        String passwordHash,
        String displayName,
        String avatar,
        String status,
        Long groupId,
        String groupKey
    ) {
    }
}
