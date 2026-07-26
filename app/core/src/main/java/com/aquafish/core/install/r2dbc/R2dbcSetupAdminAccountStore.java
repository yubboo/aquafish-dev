package com.aquafish.core.install.r2dbc;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.r2dbc.RuntimeR2dbcConnectionFactory;
import com.aquafish.core.install.ReactiveSetupAdminAccountStore;
import com.aquafish.core.install.SetupAdminAccountRequest;
import com.aquafish.core.install.SetupAdminDatabaseState;
import com.aquafish.core.install.SetupFinishDatabaseResult;
import com.aquafish.core.install.SiteSettings;
import com.aquafish.core.installation.InstallationState;
import com.aquafish.core.installation.SystemInstallationSchema;
import com.aquafish.core.installation.r2dbc.R2dbcInstallationStateSql;
import io.r2dbc.spi.R2dbcException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 安装阶段超级管理员的 R2DBC 仓库。
 *
 * <p>创建流程会在同一个响应式事务内锁定 system_instances 单例行，
 * 从而把并发安装请求串行化。全部业务值使用命名参数绑定。</p>
 */
@Repository
public class R2dbcSetupAdminAccountStore
    implements ReactiveSetupAdminAccountStore {

    private static final String MEMBER_GROUP_KEY =
        "member";

    private static final String SUPER_ADMIN_ROLE_KEY =
        "super_admin";

    private final DatabaseRuntimeSettingsService
        settingsService;

    private final RuntimeR2dbcConnectionFactory
        connectionFactory;

    public R2dbcSetupAdminAccountStore(
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

    @Override
    public Mono<SetupAdminDatabaseState> inspect(
        DatabaseSettings settings
    ) {
        return Mono.defer(
            () -> {
                OperationContext context =
                    context(settings);

                return coreTablesReady(context)
                    .flatMap(ready -> {
                        if (!ready) {
                            return Mono.just(
                                new SetupAdminDatabaseState(
                                    false,
                                    false,
                                    false
                                )
                            );
                        }

                        return installationInitializing(
                            context
                        ).flatMap(initializing ->
                            superAdminExists(context)
                                .map(adminExists ->
                                    new SetupAdminDatabaseState(
                                        true,
                                        initializing,
                                        adminExists
                                    )
                                )
                        );
                    });
            }
        );
    }

    @Override
    public Mono<Long> create(
        DatabaseSettings settings,
        SetupAdminAccountRequest request,
        String passwordHash
    ) {
        return Mono.defer(
            () -> {
                OperationContext context =
                    context(settings);

                SetupAdminAccountRequest safeRequest =
                    Objects.requireNonNull(
                        request,
                        "管理员创建请求不能为空。"
                    ).normalized();

                String safePasswordHash =
                    requirePasswordHash(
                        passwordHash
                    );

                Mono<Long> action =
                    coreTablesReady(context)
                        .flatMap(ready -> {
                            if (ready) {
                                return Mono.<Void>empty();
                            }

                            return Mono.error(
                                businessError(
                                    "数据库核心表尚未初始化，"
                                        + "请先执行数据库初始化。"
                                )
                            );
                        })
                        .then(
                            lockInitializingInstance(
                                context
                            )
                        )
                        .then(
                            requireNoSuperAdmin(
                                context
                            )
                        )
                        .then(
                            requireUsernameAvailable(
                                context,
                                safeRequest.username()
                            )
                        )
                        .then(
                            requireEmailAvailable(
                                context,
                                safeRequest.email()
                            )
                        )
                        .then(
                            ensureDefaultGroup(context)
                        )
                        .flatMap(groupId ->
                            ensureSuperAdminRole(context)
                                .flatMap(roleId ->
                                    allocateUserUid(context)
                                        .flatMap(uid -> insertAdminUser(
                                            context,
                                            safeRequest,
                                            safePasswordHash,
                                            groupId,
                                            uid,
                                            createPublicId()
                                        ))
                                        .flatMap(userId ->
                                            bindUserRole(
                                            context,
                                            userId,
                                            roleId
                                        )
                                            .then(
                                                markAdminCreated(
                                                    context,
                                                    safeRequest.username(),
                                                    userId
                                                )
                                            )
                                            .then(
                                                writeInstallLog(
                                                    context,
                                                    safeRequest.username()
                                                )
                                            )
                                            .thenReturn(userId)
                                    )
                                )
                        );

                return context.transactions()
                    .transactional(action)
                    .onErrorMap(
                        R2dbcSetupAdminAccountStore
                            ::isDuplicateKey,
                        error -> businessError(
                            "管理员用户名或邮箱已经存在。",
                            error
                        )
                    )
                    .onErrorMap(
                        error ->
                            !(error
                                instanceof
                                SetupAdminStoreException),
                        error -> businessError(
                            "数据库操作失败，"
                                + "管理员账号未创建。",
                            error
                        )
                    );
            }
        );
    }

    @Override
    public Mono<SetupFinishDatabaseResult>
        finishInstallation(
            DatabaseSettings settings,
            SetupAdminAccountRequest request,
            String passwordHash,
            SiteSettings site,
            UUID attemptId,
            Instant installedAt,
            String installedVersion
        ) {

        return Mono.defer(() -> {
            OperationContext context = context(settings);
            SetupAdminAccountRequest safeRequest =
                Objects.requireNonNull(
                    request,
                    "管理员创建请求不能为空。"
                ).normalized();
            SiteSettings safeSite =
                Objects.requireNonNull(
                    site,
                    "站点设置不能为空。"
                ).normalized();
            String safePasswordHash =
                requirePasswordHash(passwordHash);
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
                requireInstalledVersion(installedVersion);

            Mono<SetupFinishDatabaseResult> action =
                lockInstallation(context)
                    .flatMap(current -> {
                        if (current.state() == InstallationState.INSTALLED) {
                            return existingInstalledResult(
                                context,
                                current
                            );
                        }

                        requireMatchingInitialization(
                            current,
                            safeAttemptId
                        );

                        return resolveAdminForFinish(
                            context,
                            safeRequest,
                            safePasswordHash
                        ).flatMap(userId ->
                            markAdminCreated(
                                context,
                                safeRequest.username(),
                                userId
                            )
                                .then(
                                    saveSiteSettings(
                                        context,
                                        safeSite
                                    )
                                )
                                .then(
                                    markInstallFinished(
                                        context,
                                        safeInstalledAt,
                                        safeInstalledVersion
                                    )
                                )
                                .then(
                                    writeInstallLog(
                                        context,
                                        "Aquafish 安装完成",
                                        "attemptId=" + safeAttemptId
                                    )
                                )
                                .then(
                                    updateToInstalled(
                                        context,
                                        current,
                                        safeAttemptId,
                                        safeInstalledAt,
                                        safeInstalledVersion
                                    )
                                )
                                .thenReturn(
                                    new SetupFinishDatabaseResult(
                                        userId,
                                        safeRequest.username(),
                                        safeInstalledAt,
                                        safeInstalledVersion,
                                        false
                                    )
                                )
                        );
                    });

            return context.transactions()
                .transactional(action)
                .onErrorMap(
                    R2dbcSetupAdminAccountStore::isDuplicateKey,
                    error -> businessError(
                        "管理员用户名或邮箱已经存在。",
                        error
                    )
                )
                .onErrorMap(
                    error ->
                        !(error instanceof SetupAdminStoreException),
                    error -> businessError(
                        "安装最终事务提交失败，数据库未完成安装。",
                        error
                    )
                );
        });
    }

    private Mono<InstallationRow> lockInstallation(
        OperationContext context
    ) {
        return context.client()
            .sql(
                finishLockSql(
                    context.settings().type(),
                    context.tables()
                )
            )
            .bind(
                "singletonId",
                SystemInstallationSchema.PRIMARY_SINGLETON_ID
            )
            .map((row, metadata) ->
                new InstallationRow(
                    InstallationState.valueOf(
                        textValue(
                            row.get("installation_state")
                        ).toUpperCase(Locale.ROOT)
                    ),
                    numberValue(
                        row.get("state_version"),
                        "state_version"
                    ),
                    nullableUuid(
                        row.get("initialization_attempt_id")
                    ),
                    nullableInstant(
                        row.get("installed_at")
                    ),
                    nullableText(
                        row.get("installed_version")
                    )
                )
            )
            .one()
            .switchIfEmpty(
                Mono.error(
                    businessError(
                        "数据库安装状态记录不存在，请重新初始化数据库。"
                    )
                )
            );
    }

    private Mono<SetupFinishDatabaseResult>
        existingInstalledResult(
            OperationContext context,
            InstallationRow current
        ) {

        return findSuperAdmin(context)
            .switchIfEmpty(
                Mono.error(
                    businessError(
                        "数据库已标记安装完成，但超级管理员不存在。"
                    )
                )
            )
            .map(admin ->
                new SetupFinishDatabaseResult(
                    admin.userId(),
                    admin.username(),
                    Objects.requireNonNull(
                        current.installedAt(),
                        "安装完成记录缺少时间。"
                    ),
                    Objects.requireNonNull(
                        current.installedVersion(),
                        "安装完成记录缺少版本。"
                    ),
                    true
                )
            );
    }

    private void requireMatchingInitialization(
        InstallationRow current,
        UUID attemptId
    ) {
        if (
            current.state() != InstallationState.INITIALIZING
                || !Objects.equals(
                    current.attemptId(),
                    attemptId
                )
        ) {
            throw businessError(
                "当前请求不拥有数据库初始化权，拒绝完成安装。"
            );
        }
    }

    private Mono<Long> resolveAdminForFinish(
        OperationContext context,
        SetupAdminAccountRequest request,
        String passwordHash
    ) {
        return findSuperAdmin(context)
            .flatMap(admin -> {
                if (admin.username().equals(request.username())) {
                    return Mono.just(admin.userId());
                }

                return Mono.error(
                    businessError(
                        "已经存在其他超级管理员，拒绝覆盖。"
                    )
                );
            })
            .switchIfEmpty(
                createAdminWithinTransaction(
                    context,
                    request,
                    passwordHash
                )
            );
    }

    private Mono<Long> createAdminWithinTransaction(
        OperationContext context,
        SetupAdminAccountRequest request,
        String passwordHash
    ) {
        return requireUsernameAvailable(
            context,
            request.username()
        ).then(
            requireEmailAvailable(
                context,
                request.email()
            )
        ).then(
            ensureDefaultGroup(context)
        ).flatMap(groupId ->
            ensureSuperAdminRole(context)
                .flatMap(roleId ->
                    allocateUserUid(context)
                        .flatMap(uid -> insertAdminUser(
                            context,
                            request,
                            passwordHash,
                            groupId,
                            uid,
                            createPublicId()
                        ))
                        .flatMap(userId ->
                            bindUserRole(
                                context,
                                userId,
                                roleId
                            ).thenReturn(userId)
                        )
                )
        );
    }

    private Mono<AdminIdentity> findSuperAdmin(
        OperationContext context
    ) {
        String sql =
            "select u.id, u.username from "
                + context.tables().users()
                + " u join "
                + context.tables().userRoles()
                + " ur on ur.user_id = u.id join "
                + context.tables().roles()
                + " r on r.id = ur.role_id "
                + "where r.role_key = :roleKey";

        return context.client()
            .sql(sql)
            .bind("roleKey", SUPER_ADMIN_ROLE_KEY)
            .map((row, metadata) ->
                new AdminIdentity(
                    numberValue(
                        row.get("id"),
                        "id"
                    ),
                    textValue(
                        row.get("username")
                    )
                )
            )
            .one();
    }

    private Mono<Void> saveSiteSettings(
        OperationContext context,
        SiteSettings site
    ) {
        return upsertOption(context, "site.name", site.name())
            .then(upsertOption(context, "site.url", site.url()))
            .then(upsertOption(context, "site.locale", site.locale()))
            .then(upsertOption(context, "site.timezone", site.timezone()));
    }

    private Mono<Void> markInstallFinished(
        OperationContext context,
        Instant installedAt,
        String installedVersion
    ) {
        return upsertOption(context, "install.finished", "true")
            .then(
                upsertOption(
                    context,
                    "install.finished_at",
                    installedAt.toString()
                )
            )
            .then(
                upsertOption(
                    context,
                    "install.version",
                    installedVersion
                )
            );
    }

    private Mono<Void> updateToInstalled(
        OperationContext context,
        InstallationRow current,
        UUID attemptId,
        Instant installedAt,
        String installedVersion
    ) {
        return context.client()
            .sql(
                finishInstalledSql(
                    context.settings().type(),
                    context.tables()
                )
            )
            .bind("newState", InstallationState.INSTALLED.name())
            .bind("newVersion", current.stateVersion() + 1L)
            .bind("installedAt", databaseTime(installedAt))
            .bind("installedVersion", installedVersion)
            .bind("updatedAt", databaseTime(installedAt))
            .bind(
                "singletonId",
                SystemInstallationSchema.PRIMARY_SINGLETON_ID
            )
            .bind("expectedVersion", current.stateVersion())
            .bind("expectedState", InstallationState.INITIALIZING.name())
            .bind("attemptId", attemptId.toString())
            .fetch()
            .rowsUpdated()
            .flatMap(rows -> {
                if (rows != null && rows.longValue() == 1L) {
                    return Mono.empty();
                }

                return Mono.error(
                    businessError(
                        "安装状态已被其他请求修改，事务已回滚。"
                    )
                );
            });
    }

    private Mono<Boolean> coreTablesReady(
        OperationContext context
    ) {
        return Flux.fromIterable(
            context.tables().required()
        ).concatMap(tableName ->
            tableExists(
                context,
                tableName
            )
        ).all(Boolean::booleanValue);
    }

    private Mono<Boolean> tableExists(
        OperationContext context,
        String tableName
    ) {
        String sql =
            switch (context.settings().type()) {
                case MYSQL, MARIADB ->
                    "select count(*) as record_count "
                        + "from information_schema.tables "
                        + "where table_schema = database() "
                        + "and table_name = :tableName";
                case POSTGRESQL ->
                    "select count(*) as record_count "
                        + "from information_schema.tables "
                        + "where table_schema = current_schema() "
                        + "and table_name = :tableName";
            };

        return context.client()
            .sql(sql)
            .bind("tableName", tableName)
            .map((row, metadata) ->
                numberValue(
                    row.get("record_count"),
                    "record_count"
                ) > 0
            )
            .one()
            .defaultIfEmpty(false);
    }

    private Mono<Void> lockInitializingInstance(
        OperationContext context
    ) {
        return context.client()
            .sql(creationLockSql(context.tables()))
            .bind("singletonId", (short) 1)
            .map((row, metadata) ->
                textValue(
                    row.get("installation_state")
                )
            )
            .one()
            .switchIfEmpty(
                Mono.error(
                    businessError(
                        "数据库安装状态记录不存在，"
                            + "请重新开始数据库初始化。"
                    )
                )
            )
            .flatMap(state -> {
                if ("INITIALIZING".equals(
                    state.toUpperCase(Locale.ROOT)
                )) {
                    return Mono.empty();
                }

                return Mono.error(
                    businessError(
                        "当前数据库不处于初始化状态，"
                            + "不能创建首个管理员。"
                    )
                );
            });
    }

    private Mono<Boolean> installationInitializing(
        OperationContext context
    ) {
        String sql =
            "select installation_state from "
                + context.tables().systemInstances()
                + " where singleton_id = :singletonId";

        return context.client()
            .sql(sql)
            .bind("singletonId", (short) 1)
            .map((row, metadata) ->
                "INITIALIZING".equalsIgnoreCase(
                    textValue(
                        row.get("installation_state")
                    )
                )
            )
            .one()
            .defaultIfEmpty(false);
    }

    private Mono<Void> requireNoSuperAdmin(
        OperationContext context
    ) {
        return superAdminExists(context)
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.empty();
                }

                return Mono.error(
                    businessError(
                        "已经存在超级管理员，不能重复创建。"
                    )
                );
            });
    }

    private Mono<Boolean> superAdminExists(
        OperationContext context
    ) {
        String sql =
            "select count(*) as record_count from "
                + context.tables().users()
                + " u join "
                + context.tables().userRoles()
                + " ur on ur.user_id = u.id join "
                + context.tables().roles()
                + " r on r.id = ur.role_id "
                + "where r.role_key = :roleKey";

        return countExists(
            context.client()
                .sql(sql)
                .bind(
                    "roleKey",
                    SUPER_ADMIN_ROLE_KEY
                )
        );
    }

    private Mono<Void> requireUsernameAvailable(
        OperationContext context,
        String username
    ) {
        return valueExists(
            context,
            context.tables().users(),
            "username",
            username
        ).flatMap(exists -> {
            if (!exists) {
                return Mono.empty();
            }

            return Mono.error(
                businessError(
                    "管理员用户名已经存在。"
                )
            );
        });
    }

    private Mono<Void> requireEmailAvailable(
        OperationContext context,
        String email
    ) {
        if (email == null || email.isBlank()) {
            return Mono.empty();
        }

        return valueExists(
            context,
            context.tables().users(),
            "email",
            email
        ).flatMap(exists -> {
            if (!exists) {
                return Mono.empty();
            }

            return Mono.error(
                businessError(
                    "管理员邮箱已经存在。"
                )
            );
        });
    }

    private Mono<Boolean> valueExists(
        OperationContext context,
        String tableName,
        String columnName,
        String value
    ) {
        String sql =
            "select count(*) as record_count from "
                + tableName
                + " where "
                + columnName
                + " = :value";

        return countExists(
            context.client()
                .sql(sql)
                .bind("value", value)
        );
    }

    private Mono<Boolean> countExists(
        DatabaseClient.GenericExecuteSpec spec
    ) {
        return spec
            .map((row, metadata) ->
                numberValue(
                    row.get("record_count"),
                    "record_count"
                ) > 0
            )
            .one()
            .defaultIfEmpty(false);
    }

    private Mono<Long> ensureDefaultGroup(
        OperationContext context
    ) {
        return findId(
            context,
            context.tables().userGroups(),
            "group_key",
            MEMBER_GROUP_KEY
        ).switchIfEmpty(
            Mono.defer(() ->
                insertDefaultGroup(context)
                    .then(
                        findId(
                            context,
                            context.tables().userGroups(),
                            "group_key",
                            MEMBER_GROUP_KEY
                        )
                    )
            )
        ).switchIfEmpty(
            Mono.error(
                businessError(
                    "默认用户组创建失败。"
                )
            )
        );
    }

    private Mono<Void> insertDefaultGroup(
        OperationContext context
    ) {
        String table =
            context.tables().userGroups();

        String sql =
            switch (context.settings().type()) {
                case MYSQL, MARIADB ->
                    "insert ignore into "
                        + table
                        + " (group_key, name, description, "
                        + "sort_order, is_default) values "
                        + "(:groupKey, :name, :description, 100, 1)";
                case POSTGRESQL ->
                    "insert into "
                        + table
                        + " (group_key, name, description, "
                        + "sort_order, is_default) values "
                        + "(:groupKey, :name, :description, 100, 1) "
                        + "on conflict (group_key) do nothing";
            };

        return context.client()
            .sql(sql)
            .bind("groupKey", MEMBER_GROUP_KEY)
            .bind("name", "普通用户")
            .bind("description", "系统默认普通用户组")
            .fetch()
            .rowsUpdated()
            .then();
    }

    private Mono<Long> ensureSuperAdminRole(
        OperationContext context
    ) {
        return findId(
            context,
            context.tables().roles(),
            "role_key",
            SUPER_ADMIN_ROLE_KEY
        ).switchIfEmpty(
            Mono.defer(() ->
                insertSuperAdminRole(context)
                    .then(
                        findId(
                            context,
                            context.tables().roles(),
                            "role_key",
                            SUPER_ADMIN_ROLE_KEY
                        )
                    )
            )
        ).switchIfEmpty(
            Mono.error(
                businessError(
                    "超级管理员角色创建失败。"
                )
            )
        );
    }

    private Mono<Void> insertSuperAdminRole(
        OperationContext context
    ) {
        String table =
            context.tables().roles();

        String sql =
            switch (context.settings().type()) {
                case MYSQL, MARIADB ->
                    "insert ignore into "
                        + table
                        + " (role_key, name, description, built_in) "
                        + "values (:roleKey, :name, :description, 1)";
                case POSTGRESQL ->
                    "insert into "
                        + table
                        + " (role_key, name, description, built_in) "
                        + "values (:roleKey, :name, :description, 1) "
                        + "on conflict (role_key) do nothing";
            };

        return context.client()
            .sql(sql)
            .bind("roleKey", SUPER_ADMIN_ROLE_KEY)
            .bind("name", "超级管理员")
            .bind("description", "拥有 Aquafish 全部后台权限")
            .fetch()
            .rowsUpdated()
            .then();
    }

    private Mono<Long> findId(
        OperationContext context,
        String tableName,
        String keyColumn,
        String keyValue
    ) {
        String sql =
            "select id from "
                + tableName
                + " where "
                + keyColumn
                + " = :keyValue";

        return context.client()
            .sql(sql)
            .bind("keyValue", keyValue)
            .map((row, metadata) ->
                numberValue(
                    row.get("id"),
                    "id"
                )
            )
            .one();
    }

    private Mono<Long> allocateUserUid(
        OperationContext context
    ) {
        Mono<Long> lock = context.client()
            .sql(
                "select id from "
                    + context.tables().userUidAllocator()
                    + " where id = 1 for update"
            )
            .map((row, metadata) -> numberValue(row.get("id"), "id"))
            .one()
            .filter(value -> value == 1L)
            .switchIfEmpty(Mono.error(businessError(
                "用户 UID 分配锁尚未初始化，请先执行数据库迁移。"
            )));

        String users = context.tables().users();
        String smallestGapSql = "select case "
            + "when exists (select 1 from " + users + " where uid = 1) then "
            + "coalesce((select min(uid_source.uid + 1) from " + users
            + " uid_source left join " + users
            + " occupied on occupied.uid = uid_source.uid + 1 "
            + "where uid_source.uid is not null and occupied.uid is null), 1) "
            + "else 1 end as next_uid";

        return lock.then(context.client().sql(smallestGapSql)
                .map((row, metadata) -> numberValue(row.get("next_uid"), "next_uid"))
                .one())
            .filter(uid -> uid > 0L)
            .switchIfEmpty(Mono.error(businessError("无法分配有效的用户 UID。")));
    }

    private String createPublicId() {
        return "AQUA_" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .toUpperCase(Locale.ROOT);
    }

    private Mono<Long> insertAdminUser(
        OperationContext context,
        SetupAdminAccountRequest request,
        String passwordHash,
        long groupId,
        long uid,
        String publicId
    ) {
        String sql =
            "insert into "
                + context.tables().users()
                + " (uid, public_id, username, email, password_hash, "
                + "display_name, status, group_id, register_source) values "
                + "(:uid, :publicId, :username, :email, :passwordHash, "
                + ":displayName, 'ACTIVE', :groupId, 'setup')";

        DatabaseClient.GenericExecuteSpec spec =
            context.client()
                .sql(sql)
                .bind("uid", uid)
                .bind("publicId", publicId)
                .bind("username", request.username())
                .bind("passwordHash", passwordHash)
                .bind("displayName", request.displayName())
                .bind("groupId", groupId);

        if (request.email().isBlank()) {
            spec = spec.bindNull(
                "email",
                String.class
            );
        } else {
            spec = spec.bind(
                "email",
                request.email()
            );
        }

        return spec.fetch()
            .rowsUpdated()
            .flatMap(rows -> {
                if (rows == 1) {
                    return Mono.empty();
                }

                return Mono.error(
                    businessError(
                        "管理员用户创建失败。"
                    )
                );
            })
            .then(
                findId(
                    context,
                    context.tables().users(),
                    "username",
                    request.username()
                )
            )
            .switchIfEmpty(
                Mono.error(
                    businessError(
                        "管理员用户创建失败，"
                            + "未能获取用户 ID。"
                    )
                )
            );
    }

    private Mono<Void> bindUserRole(
        OperationContext context,
        long userId,
        long roleId
    ) {
        String sql =
            switch (context.settings().type()) {
                case MYSQL, MARIADB ->
                    "insert ignore into "
                        + context.tables().userRoles()
                        + " (user_id, role_id) "
                        + "values (:userId, :roleId)";
                case POSTGRESQL ->
                    "insert into "
                        + context.tables().userRoles()
                        + " (user_id, role_id) "
                        + "values (:userId, :roleId) "
                        + "on conflict (user_id, role_id) do nothing";
            };

        return context.client()
            .sql(sql)
            .bind("userId", userId)
            .bind("roleId", roleId)
            .fetch()
            .rowsUpdated()
            .then();
    }

    private Mono<Void> markAdminCreated(
        OperationContext context,
        String username,
        long userId
    ) {
        return upsertOption(
            context,
            "install.admin_created",
            "true"
        ).then(
            upsertOption(
                context,
                "install.admin_username",
                username
            )
        ).then(
            upsertOption(
                context,
                "install.admin_user_id",
                Long.toString(userId)
            )
        ).then(
            upsertOption(
                context,
                "install.admin_created_at",
                Instant.now().toString()
            )
        );
    }

    private Mono<Void> upsertOption(
        OperationContext context,
        String key,
        String value
    ) {
        return context.client()
            .sql(
                optionUpsertSql(
                    context.settings().type(),
                    context.tables().options()
                )
            )
            .bind("optionKey", key)
            .bind("optionValue", value)
            .fetch()
            .rowsUpdated()
            .then();
    }

    private Mono<Void> writeInstallLog(
        OperationContext context,
        String username
    ) {
        return writeInstallLog(
            context,
            "超级管理员账号创建完成",
            "username=" + username
        );
    }

    private Mono<Void> writeInstallLog(
        OperationContext context,
        String message,
        String logContext
    ) {
        String sql =
            "insert into "
                + context.tables().installLogs()
                + " (level, message, context) "
                + "values (:level, :message, :context)";

        return context.client()
            .sql(sql)
            .bind("level", "INFO")
            .bind("message", message)
            .bind("context", logContext)
            .fetch()
            .rowsUpdated()
            .then();
    }

    static String creationLockSql(
        TableNames tables
    ) {
        return "select installation_state from "
            + tables.systemInstances()
            + " where singleton_id = :singletonId for update";
    }

    static String finishLockSql(
        DatabaseType databaseType,
        TableNames tables
    ) {
        return R2dbcInstallationStateSql.selectCurrent(
            databaseType,
            tables.systemInstances(),
            true
        );
    }

    static String finishInstalledSql(
        DatabaseType databaseType,
        TableNames tables
    ) {
        return R2dbcInstallationStateSql.updateToInstalled(
            databaseType,
            tables.systemInstances()
        );
    }

    static String optionUpsertSql(
        DatabaseType databaseType,
        String tableName
    ) {
        String insert =
            "insert into "
                + tableName
                + " (option_key, option_value, "
                + "option_group, autoload) values "
                + "(:optionKey, :optionValue, 'install', 1) ";

        if (
            databaseType == DatabaseType.MYSQL
                || databaseType == DatabaseType.MARIADB
        ) {
            return insert
                + "on duplicate key update "
                + "option_value = values(option_value), "
                + "updated_at = current_timestamp";
        }

        return insert
            + "on conflict (option_key) do update set "
            + "option_value = excluded.option_value, "
            + "updated_at = current_timestamp";
    }

    static boolean isDuplicateKey(
        Throwable error
    ) {
        Throwable current = error;

        while (current != null) {
            if (current instanceof DuplicateKeyException) {
                return true;
            }

            if (current instanceof R2dbcException r2dbc) {
                if (
                    "23505".equals(r2dbc.getSqlState())
                        || r2dbc.getErrorCode() == 1062
                ) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }

    private OperationContext context(
        DatabaseSettings settings
    ) {
        DatabaseSettings safeSettings =
            requireSettings(settings);

        settingsService.useForInstallation(
            safeSettings
        );

        DatabaseClient client =
            DatabaseClient.create(
                connectionFactory
            );

        TransactionalOperator transactions =
            TransactionalOperator.create(
                new R2dbcTransactionManager(
                    connectionFactory
                )
            );

        return new OperationContext(
            safeSettings,
            TableNames.from(safeSettings),
            client,
            transactions
        );
    }

    private DatabaseSettings requireSettings(
        DatabaseSettings settings
    ) {
        if (settings == null) {
            throw businessError(
                "尚未找到数据库安装配置。"
            );
        }

        DatabaseSettings safe =
            settings.normalized();

        if (!safe.hasRequiredFields()) {
            throw businessError(
                "数据库安装配置不完整。"
            );
        }

        return safe;
    }

    private String requirePasswordHash(
        String passwordHash
    ) {
        if (
            passwordHash == null
                || passwordHash.isBlank()
                || !passwordHash.startsWith("$2")
        ) {
            throw businessError(
                "管理员密码哈希无效。"
            );
        }

        return passwordHash;
    }

    private String requireInstalledVersion(
        String installedVersion
    ) {
        if (
            installedVersion == null
                || installedVersion.isBlank()
                || installedVersion.length() > 64
        ) {
            throw businessError(
                "安装版本无效。"
            );
        }

        return installedVersion.trim();
    }

    private static LocalDateTime databaseTime(
        Instant value
    ) {
        return LocalDateTime.ofInstant(
            value,
            ZoneOffset.UTC
        );
    }

    private static UUID nullableUuid(
        Object value
    ) {
        if (value == null) {
            return null;
        }

        if (value instanceof UUID uuid) {
            return uuid;
        }

        return UUID.fromString(
            value.toString().trim()
        );
    }

    private static Instant nullableInstant(
        Object value
    ) {
        if (value == null) {
            return null;
        }

        if (value instanceof Instant instant) {
            return instant;
        }

        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }

        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }

        return Instant.parse(
            value.toString().trim()
        );
    }

    private static String nullableText(
        Object value
    ) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }

        return value.toString().trim();
    }

    private static long numberValue(
        Object value,
        String columnName
    ) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value != null) {
            try {
                return Long.parseLong(
                    value.toString()
                );
            } catch (NumberFormatException ignored) {
                // 统一在下方抛出安全错误。
            }
        }

        throw businessError(
            "数据库数字字段格式无效："
                + columnName
        );
    }

    private static String textValue(
        Object value
    ) {
        if (value == null || value.toString().isBlank()) {
            throw businessError(
                "数据库安装状态记录格式无效。"
            );
        }

        return value.toString().trim();
    }

    private static SetupAdminStoreException
        businessError(String message) {
        return new SetupAdminStoreException(
            message,
            null
        );
    }

    private static SetupAdminStoreException
        businessError(
            String message,
            Throwable cause
        ) {
        return new SetupAdminStoreException(
            message,
            cause
        );
    }

    record TableNames(
        String users,
        String roles,
        String userRoles,
        String options,
        String userGroups,
        String userUidAllocator,
        String installLogs,
        String systemInstances
    ) {
        static TableNames from(
            DatabaseSettings settings
        ) {
            String prefix =
                settings.tablePrefix();

            return new TableNames(
                table(prefix, "users"),
                table(prefix, "roles"),
                table(prefix, "user_roles"),
                table(prefix, "options"),
                table(prefix, "user_groups"),
                table(prefix, "user_uid_allocator"),
                table(prefix, "install_logs"),
                table(prefix, "system_instances")
            );
        }

        List<String> required() {
            return List.of(
                users,
                roles,
                userRoles,
                options,
                userGroups,
                userUidAllocator,
                installLogs,
                systemInstances
            );
        }

        private static String table(
            String prefix,
            String logicalName
        ) {
            return TableNameResolver.tableName(
                prefix,
                logicalName
            );
        }
    }

    private record OperationContext(
        DatabaseSettings settings,
        TableNames tables,
        DatabaseClient client,
        TransactionalOperator transactions
    ) {
    }

    private record InstallationRow(
        InstallationState state,
        long stateVersion,
        UUID attemptId,
        Instant installedAt,
        String installedVersion
    ) {
    }

    private record AdminIdentity(
        long userId,
        String username
    ) {
    }

    private static final class
        SetupAdminStoreException
        extends IllegalStateException {

        private SetupAdminStoreException(
            String message,
            Throwable cause
        ) {
            super(message, cause);
        }
    }
}
