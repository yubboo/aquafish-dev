package com.aquafish.user.auth;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import com.aquafish.core.install.AuthoritativeInstallStatusService;
import com.aquafish.core.user.UserUidAllocator;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 前台用户自主注册服务。
 *
 * <p>注册在一个 R2DBC 事务中写入用户主表、资料表、统计表和普通用户角色。
 * 所有表名都由运行配置中的真实前缀解析，因此注册完成后后台用户列表会立即从
 * 同一套数据表中读到该用户。</p>
 */
@Service
public class MemberRegistrationService {

    private static final String DEFAULT_GROUP_KEY = "member";
    private static final String DEFAULT_ROLE_KEY = "user";

    private final DatabaseRuntimeSettingsService databaseSettings;
    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactions;
    private final AuthoritativeInstallStatusService installStatusService;
    private final UserUidAllocator uidAllocator;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public MemberRegistrationService(
        DatabaseRuntimeSettingsService databaseSettings,
        DatabaseClient databaseClient,
        TransactionalOperator transactions,
        AuthoritativeInstallStatusService installStatusService,
        UserUidAllocator uidAllocator
    ) {
        this(
            databaseSettings,
            databaseClient,
            transactions,
            installStatusService,
            uidAllocator,
            new BCryptPasswordEncoder()
        );
    }

    MemberRegistrationService(
        DatabaseRuntimeSettingsService databaseSettings,
        DatabaseClient databaseClient,
        TransactionalOperator transactions,
        AuthoritativeInstallStatusService installStatusService,
        UserUidAllocator uidAllocator,
        PasswordEncoder passwordEncoder
    ) {
        this.databaseSettings = databaseSettings;
        this.databaseClient = databaseClient;
        this.transactions = transactions;
        this.installStatusService = installStatusService;
        this.uidAllocator = uidAllocator;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 校验并创建普通用户。注册成功不授予 admin 或 super_admin 角色。
     */
    public Mono<MemberRegistrationResult> register(
        MemberRegistrationRequest source,
        MemberLoginMetadata metadata
    ) {
        return installStatusService.requireInstalled()
            .then(Mono.defer(() -> {
                MemberRegistrationRequest request = source == null
                    ? new MemberRegistrationRequest("", "", "", "", "", false)
                    : source.normalized();
                String validation = request.validateMessage();
                if (validation != null) {
                    return Mono.error(new MemberRegistrationException(
                        "MEMBER_REGISTER_INVALID",
                        validation,
                        false
                    ));
                }

                DatabaseSettings settings = databaseSettings.current().normalized();
                MemberLoginMetadata safeMetadata = metadata == null
                    ? MemberLoginMetadata.empty()
                    : metadata.normalized();

                return requireAvailableIdentity(settings, request)
                    .then(Mono.zip(
                        requireGroupId(settings),
                        requireRoleId(settings),
                        encodePassword(request.password())
                    ))
                    .flatMap(values -> createRegistration(
                        settings,
                        request,
                        safeMetadata,
                        values.getT1(),
                        values.getT2(),
                        values.getT3()
                    ));
            }));
    }

    private Mono<Void> requireAvailableIdentity(
        DatabaseSettings settings,
        MemberRegistrationRequest request
    ) {
        return Mono.zip(
                exists(settings, "lower(username) = lower(:value)", request.username()),
                exists(settings, "lower(coalesce(email, '')) = lower(:value)", request.email())
            )
            .flatMap(values -> {
                if (values.getT1()) {
                    return Mono.error(new MemberRegistrationException(
                        "MEMBER_USERNAME_EXISTS",
                        "该用户名已被使用。",
                        true
                    ));
                }
                if (values.getT2()) {
                    return Mono.error(new MemberRegistrationException(
                        "MEMBER_EMAIL_EXISTS",
                        "该邮箱已被注册。",
                        true
                    ));
                }
                return Mono.empty();
            });
    }

    private Mono<Boolean> exists(
        DatabaseSettings settings,
        String condition,
        String value
    ) {
        return databaseClient.sql(
                "select count(*) as row_count from "
                    + table(settings, TableNames.USERS)
                    + " where " + condition
            )
            .bind("value", value)
            .map((row, metadata) -> {
                Number count = row.get("row_count", Number.class);
                return count != null && count.longValue() > 0L;
            })
            .one()
            .defaultIfEmpty(false);
    }

    private Mono<Long> requireGroupId(DatabaseSettings settings) {
        return lookupId(
                settings,
                TableNames.USER_GROUPS,
                "group_key",
                DEFAULT_GROUP_KEY
            )
            .switchIfEmpty(Mono.error(new MemberRegistrationException(
                "MEMBER_DEFAULT_GROUP_MISSING",
                "系统普通用户组尚未初始化，请联系管理员。",
                false
            )));
    }

    private Mono<Long> requireRoleId(DatabaseSettings settings) {
        return lookupId(
                settings,
                TableNames.ROLES,
                "role_key",
                DEFAULT_ROLE_KEY
            )
            .switchIfEmpty(Mono.error(new MemberRegistrationException(
                "MEMBER_DEFAULT_ROLE_MISSING",
                "系统普通用户角色尚未初始化，请联系管理员。",
                false
            )));
    }

    private Mono<Long> lookupId(
        DatabaseSettings settings,
        String logicalTable,
        String keyColumn,
        String keyValue
    ) {
        return databaseClient.sql(
                "select id from " + table(settings, logicalTable)
                    + " where " + keyColumn + " = :keyValue limit 1"
            )
            .bind("keyValue", keyValue)
            .map((row, metadata) -> {
                Number value = row.get("id", Number.class);
                return value == null ? 0L : value.longValue();
            })
            .one()
            .filter(value -> value > 0L);
    }

    private Mono<String> encodePassword(String rawPassword) {
        return Mono.fromCallable(() -> passwordEncoder.encode(rawPassword))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<MemberRegistrationResult> createRegistration(
        DatabaseSettings settings,
        MemberRegistrationRequest request,
        MemberLoginMetadata metadata,
        long groupId,
        long roleId,
        String passwordHash
    ) {
        String publicId = "AQUA_" + UUID.randomUUID().toString()
            .replace("-", "")
            .toUpperCase();

        /*
         * BEGIN：注册账号与可复用 UID 的原子写入。
         *
         * allocate() 会锁定数据库单例分配行；事务提交前锁不会释放，因此并发注册
         * 不会拿到相同 UID。id 仍是内部关系主键，uid 只用于用户可见编号。
         */
        Mono<MemberRegistrationResult> work = uidAllocator.allocate()
            .flatMap(uid -> insertUser(
                    settings,
                    request,
                    metadata,
                    groupId,
                    passwordHash,
                    publicId,
                    uid
                )
                .flatMap(userId -> insertProfile(
                        settings,
                        userId,
                        request.displayName()
                    )
                    .then(insertStatistics(settings, userId))
                    .then(insertRole(settings, userId, roleId))
                    .thenReturn(new MemberRegistrationResult(
                        userId,
                        uid,
                        publicId,
                        request.username()
                    ))));
        // END：注册账号与可复用 UID 的原子写入。

        return transactions.transactional(work)
            .onErrorMap(
                DataIntegrityViolationException.class,
                error -> new MemberRegistrationException(
                    "MEMBER_REGISTER_CONFLICT",
                    "用户名或邮箱已被注册，请更换后重试。",
                    true
                )
            )
            .onErrorMap(error ->
                error instanceof MemberRegistrationException
                    ? error
                    : new MemberRegistrationException(
                        "MEMBER_REGISTER_FAILED",
                        "注册暂时不可用，请稍后重试。",
                        false
                    )
            );
    }

    private Mono<Long> insertUser(
        DatabaseSettings settings,
        MemberRegistrationRequest request,
        MemberLoginMetadata metadata,
        long groupId,
        String passwordHash,
        String publicId,
        long uid
    ) {
        return databaseClient.sql(
                "insert into " + table(settings, TableNames.USERS)
                    + " (uid, public_id, username, email, password_hash, display_name, avatar, "
                    + "status, group_id, register_source, register_ip) values "
                    + "(:uid, :publicId, :username, :email, :passwordHash, :displayName, '', "
                    + "'ACTIVE', :groupId, 'self_service', :registerIp)"
            )
            .bind("uid", uid)
            .bind("publicId", publicId)
            .bind("username", request.username())
            .bind("email", request.email())
            .bind("passwordHash", passwordHash)
            .bind("displayName", request.displayName())
            .bind("groupId", groupId)
            .bind("registerIp", metadata.ipAddress())
            .filter(statement -> statement.returnGeneratedValues("id"))
            .map((row, rowMetadata) -> {
                Number value = row.get(0, Number.class);
                return value == null ? 0L : value.longValue();
            })
            .one()
            .filter(value -> value > 0L)
            .switchIfEmpty(Mono.error(new MemberRegistrationException(
                "MEMBER_REGISTER_FAILED",
                "创建用户账号失败，请稍后重试。",
                false
            )));
    }

    private Mono<Void> insertProfile(
        DatabaseSettings settings,
        long userId,
        String displayName
    ) {
        return requireWrite(databaseClient.sql(
                "insert into " + table(settings, TableNames.USER_PROFILES)
                    + " (user_id, nickname) values (:userId, :nickname)"
            )
            .bind("userId", userId)
            .bind("nickname", displayName), "初始化用户资料失败。");
    }

    private Mono<Void> insertStatistics(
        DatabaseSettings settings,
        long userId
    ) {
        return requireWrite(databaseClient.sql(
                "insert into " + table(settings, TableNames.USER_STATISTICS)
                    + " (user_id) values (:userId)"
            )
            .bind("userId", userId), "初始化用户统计失败。");
    }

    private Mono<Void> insertRole(
        DatabaseSettings settings,
        long userId,
        long roleId
    ) {
        return requireWrite(databaseClient.sql(
                "insert into " + table(settings, TableNames.USER_ROLES)
                    + " (user_id, role_id) values (:userId, :roleId)"
            )
            .bind("userId", userId)
            .bind("roleId", roleId), "初始化普通用户角色失败。");
    }

    private Mono<Void> requireWrite(
        DatabaseClient.GenericExecuteSpec spec,
        String message
    ) {
        return spec.fetch()
            .rowsUpdated()
            .flatMap(rows -> rows != null && rows > 0L
                ? Mono.empty()
                : Mono.error(new MemberRegistrationException(
                    "MEMBER_REGISTER_FAILED",
                    message,
                    false
                )));
    }

    private String table(DatabaseSettings settings, String logicalName) {
        return TableNameResolver.tableName(settings.tablePrefix(), logicalName);
    }
}
