package com.aquafish.admin.user;

import com.aquafish.core.admin.auth.AdminAuthService;
import com.aquafish.core.admin.auth.AdminAuthUser;
import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import com.aquafish.core.user.UserUidAllocator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 后台用户响应式领域动作服务。
 *
 * <p>所有在线写操作使用当前实例唯一的 R2DBC 连接和响应式事务。调用入口是
 * {@code AdminUserDomainActionController}；高权限目标检查交给
 * {@link AdminUserPrivilegeGuard}；提交后通过 {@link AdminUserQueryService} 返回最新详情。
 * 密码、封禁及后台权限发生变化时，还会通过 {@link AdminAuthService} 撤销旧会话。</p>
 *
 * <p>本类同时写业务表与 {@code admin_operation_logs}，并由
 * {@link TransactionalOperator} 保证“业务变更 + 审计记录”一起成功或一起回滚。</p>
 */
@Service
public class AdminUserDomainActionService {

    private final DatabaseRuntimeSettingsService settingsService;
    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final AdminUserQueryService queryService;
    private final AdminAuthService adminAuthService;
    private final AdminUserPrivilegeGuard privilegeGuard;
    private final UserUidAllocator uidAllocator;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminUserDomainActionService(
        DatabaseRuntimeSettingsService settingsService,
        DatabaseClient databaseClient,
        TransactionalOperator transactionalOperator,
        AdminUserQueryService queryService,
        AdminAuthService adminAuthService,
        AdminUserPrivilegeGuard privilegeGuard,
        UserUidAllocator uidAllocator
    ) {
        this.settingsService = settingsService;
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
        this.queryService = queryService;
        this.adminAuthService = adminAuthService;
        this.privilegeGuard = privilegeGuard;
        this.uidAllocator = uidAllocator;
    }

    /**
     * 创建新用户：校验唯一性与格式、在线程池执行 BCrypt 加密、写入用户及配套资料，
     * 最终在同一响应式事务中记录操作者日志并返回完整用户详情。
     */
    public Mono<Map<String, Object>> createUser(
        AdminAuthUser operator,
        Map<String, Object> request
    ) {
        requireOperator(operator);
        DatabaseSettings settings = settings();
        String username = requireText(request, "username", "用户名不能为空。");
        String email = requireText(request, "email", "邮箱不能为空。");
        String password = requireText(request, "password", "密码不能为空。");
        String displayName = optionalText(request, "displayName", username);
        String status = optionalText(request, "status", "ACTIVE").toUpperCase();

        if (password.length() < 8) {
            return Mono.error(new IllegalStateException("密码长度不能少于 8 位。"));
        }
        if (!email.contains("@")) {
            return Mono.error(new IllegalStateException("邮箱格式不正确。"));
        }

        Mono<String> passwordHash = Mono.fromCallable(() -> passwordEncoder.encode(password))
            .subscribeOn(Schedulers.boundedElastic());

        Mono<Long> work = passwordHash.flatMap(hash ->
            requireUnique(settings, "username", username, null, "用户名已存在。")
                .then(requireUnique(settings, "email", email, null, "邮箱已存在。"))
                .then(resolveUserGroup(settings, request))
                .flatMap(groupId -> insertUser(
                    settings, username, email, hash, displayName, status, groupId
                ))
                .flatMap(userId -> initializeUser(settings, operator, userId)
                    .thenReturn(userId))
        );

        return complete(work, "用户创建成功。", false);
    }

    /**
     * 更新基础资料；先通过权限守卫检查目标账号，再检查用户名/邮箱唯一性并写审计日志。
     */
    public Mono<Map<String, Object>> updateBasic(
        AdminAuthUser operator,
        long userId,
        Map<String, Object> request
    ) {
        DatabaseSettings settings = settings();

        Mono<Long> work = requireTarget(settings, operator, userId, "修改资料")
            .flatMap(current -> {
                String username = optionalText(request, "username", text(current.get("username")));
                String email = optionalText(request, "email", text(current.get("email")));
                String displayName = optionalText(
                    request, "displayName", text(current.get("display_name"))
                );
                String avatar = optionalText(request, "avatar", text(current.get("avatar")));

                if (!email.isBlank() && !email.contains("@")) {
                    return Mono.error(new IllegalStateException("邮箱格式不正确。"));
                }

                DatabaseClient.GenericExecuteSpec update = databaseClient.sql(
                        "update " + table(settings, "users")
                            + " set username = :username, email = :email, "
                            + "display_name = :displayName, avatar = :avatar, "
                            + "updated_at = current_timestamp where id = :id")
                    .bind("username", username)
                    .bind("displayName", displayName)
                    .bind("id", userId);
                update = bindNullable(
                    update, "email", email.isBlank() ? null : email, String.class
                );
                update = bindNullable(
                    update, "avatar", avatar.isBlank() ? null : avatar, String.class
                );

                return requireUnique(settings, "username", username, userId, "用户名已存在。")
                    .then(requireUnique(settings, "email", email, userId, "邮箱已存在。"))
                    .then(update.fetch().rowsUpdated())
                    .then(log(settings, operator, "users.update_basic", userId,
                        "修改用户基础资料", "username=" + username))
                    .thenReturn(userId);
            });

        return complete(work, "用户资料修改成功。", false);
    }

    /**
     * 修改前台用户组；用户组必须真实存在，更新和审计日志在同一事务中提交。
     */
    public Mono<Map<String, Object>> changeUserGroup(
        AdminAuthUser operator,
        long userId,
        Map<String, Object> request
    ) {
        DatabaseSettings settings = settings();
        long groupId = requireLong(request, "groupId", "用户组 ID 不能为空。");

        Mono<Long> work = requireTarget(settings, operator, userId, "修改用户组")
            .then(requireExists(settings, "user_groups", groupId, "用户组不存在。"))
            .then(databaseClient.sql("update " + table(settings, "users")
                    + " set group_id = :groupId, updated_at = current_timestamp where id = :id")
                .bind("groupId", groupId).bind("id", userId).fetch().rowsUpdated())
            .then(log(settings, operator, "users.change_group", userId,
                "修改用户组", "groupId=" + groupId))
            .thenReturn(userId);

        return complete(work, "用户组修改成功。", false);
    }

    /** 恢复用户为 ACTIVE 状态，并通过统一状态变更流程检查权限和记录日志。 */
    public Mono<Map<String, Object>> enableUser(AdminAuthUser operator, long userId) {
        return changeStatus(operator, userId, "ACTIVE", "users.enable", "用户已启用。", "");
    }

    /** 禁用用户；禁止自我禁用，成功后撤销目标账号的现有后台会话。 */
    public Mono<Map<String, Object>> disableUser(
        AdminAuthUser operator,
        long userId,
        Map<String, Object> request
    ) {
        requireNotSelf(operator, userId, "不能禁用当前登录账号。");
        return changeStatus(operator, userId, "DISABLED", "users.disable",
            "用户已禁用。", optionalText(request, "reason", ""));
    }

    /**
     * 安全删除用户并释放展示 UID。
     *
     * <p>内部主键 id 及文章、帖子、审计关系全部保留；账号凭据和可登录关系被撤销，
     * uid 置空后可由下一位注册用户复用。删除属于不可直接恢复的高权限动作，只允许
     * 超级管理员执行，且禁止删除当前登录账号。</p>
     */
    public Mono<Map<String, Object>> deleteUser(
        AdminAuthUser operator,
        long userId
    ) {
        privilegeGuard.requireSuperAdmin(operator, "删除用户");
        requireNotSelf(operator, userId, "不能删除当前登录账号。");
        DatabaseSettings settings = settings();

        /*
         * BEGIN：用户安全删除事务。
         *
         * 不执行 users 物理 DELETE，避免历史内容失去作者关系；删除登录凭据与权限关系，
         * 最后匿名化主表并释放 uid。任何一步失败都会由事务整体回滚。
         */
        Mono<Map<String, Object>> work = requireTarget(
                settings, operator, userId, "删除用户"
            )
            .flatMap(current -> {
                long releasedUid = optionalLong(current.get("uid"));
                if (releasedUid <= 0L) {
                    return Mono.error(new IllegalStateException("用户已经被删除。"));
                }

                String deletedUsername = "deleted_" + userId + "_"
                    + UUID.randomUUID().toString().replace("-", "");
                String revokedPassword = "!deleted:"
                    + UUID.randomUUID().toString().replace("-", "");

                return deleteByUserId(settings, TableNames.USER_SESSIONS, userId)
                    .then(deleteByUserId(settings, TableNames.USER_OAUTH_ACCOUNTS, userId))
                    .then(deleteByUserId(
                        settings, TableNames.USER_VERIFICATION_TOKENS, userId
                    ))
                    .then(deleteByUserId(settings, TableNames.USER_ROLES, userId))
                    .then(deleteByUserId(settings, TableNames.ADMIN_GROUP_USERS, userId))
                    .then(databaseClient.sql("delete from "
                            + table(settings, TableNames.USER_RELATIONSHIPS)
                            + " where source_user_id = :id or target_user_id = :id")
                        .bind("id", userId)
                        .fetch().rowsUpdated())
                    .then(databaseClient.sql("update "
                            + table(settings, TableNames.USER_BANS)
                            + " set enabled = 0, updated_at = current_timestamp "
                            + "where user_id = :id and enabled = 1")
                        .bind("id", userId)
                        .fetch().rowsUpdated())
                    .then(databaseClient.sql("update "
                            + table(settings, TableNames.USERS)
                            + " set uid = null, username = :username, email = null, "
                            + "password_hash = :passwordHash, display_name = :displayName, "
                            + "avatar = null, status = 'DELETED', group_id = null, "
                            + "updated_at = current_timestamp where id = :id and uid is not null")
                        .bind("username", deletedUsername)
                        .bind("passwordHash", revokedPassword)
                        .bind("displayName", "已删除用户 UID " + releasedUid)
                        .bind("id", userId)
                        .fetch().rowsUpdated())
                    .then(log(settings, operator, "users.delete", userId,
                        "安全删除用户", "releasedUid=" + releasedUid))
                    .thenReturn(deletedUserResult(userId, releasedUid));
            });

        return transactionalOperator.transactional(work)
            .doOnSuccess(ignored -> adminAuthService.revokeUserSessions(userId))
            .map(data -> result("用户已删除，UID " + data.get("uid") + " 已释放。", data));
        // END：用户安全删除事务。
    }

    /**
     * 封禁用户：新增可追溯封禁记录、同步 BANNED 状态、写审计日志并撤销现有会话。
     */
    public Mono<Map<String, Object>> banUser(
        AdminAuthUser operator,
        long userId,
        Map<String, Object> request
    ) {
        requireNotSelf(operator, userId, "不能封禁当前登录账号。");
        DatabaseSettings settings = settings();
        String banType = optionalText(request, "banType", "login");
        String reason = optionalText(request, "reason", "后台手动封禁");
        LocalDateTime expiredAt = optionalDateTime(request.get("expiredAt"));

        Mono<Long> work = requireTarget(settings, operator, userId, "封禁")
            .then(bindNullable(
                databaseClient.sql("insert into " + table(settings, "user_bans")
                    + " (user_id, ban_type, reason, operator_id, started_at, expired_at, enabled) "
                    + "values (:userId, :banType, :reason, :operatorId, current_timestamp, "
                    + ":expiredAt, 1)")
                    .bind("userId", userId)
                    .bind("banType", banType)
                    .bind("reason", reason)
                    .bind("operatorId", operator.id()),
                "expiredAt", expiredAt, LocalDateTime.class
            ).fetch().rowsUpdated())
            .then(updateStatus(settings, userId, "BANNED"))
            .then(log(settings, operator, "users.ban", userId,
                "封禁用户", "banType=" + banType + ", reason=" + reason))
            .thenReturn(userId);

        return complete(work, "用户已封禁。", true);
    }

    /** 解除所有有效封禁记录、恢复 ACTIVE 状态，并保留解除原因的审计日志。 */
    public Mono<Map<String, Object>> unbanUser(
        AdminAuthUser operator,
        long userId,
        Map<String, Object> request
    ) {
        DatabaseSettings settings = settings();
        String reason = optionalText(request, "reason", "后台解除封禁");

        Mono<Long> work = requireTarget(settings, operator, userId, "解除封禁")
            .then(databaseClient.sql("update " + table(settings, "user_bans")
                    + " set enabled = 0, updated_at = current_timestamp "
                    + "where user_id = :id and enabled = 1")
                .bind("id", userId).fetch().rowsUpdated())
            .then(updateStatus(settings, userId, "ACTIVE"))
            .then(log(settings, operator, "users.unban", userId,
                "解除用户封禁", reason))
            .thenReturn(userId);

        return complete(work, "用户封禁已解除。", false);
    }

    /**
     * 重置密码：在弹性线程池执行 BCrypt 计算，事务写入新哈希并撤销所有旧会话。
     */
    public Mono<Map<String, Object>> resetPassword(
        AdminAuthUser operator,
        long userId,
        Map<String, Object> request
    ) {
        DatabaseSettings settings = settings();
        String password = requireText(request, "password", "新密码不能为空。");
        if (password.length() < 8) {
            return Mono.error(new IllegalStateException("新密码长度不能少于 8 位。"));
        }

        Mono<Long> work = requireTarget(settings, operator, userId, "重置密码")
            .then(Mono.fromCallable(() -> passwordEncoder.encode(password))
                .subscribeOn(Schedulers.boundedElastic()))
            .flatMap(hash -> databaseClient.sql("update " + table(settings, "users")
                    + " set password_hash = :hash, updated_at = current_timestamp where id = :id")
                .bind("hash", hash).bind("id", userId).fetch().rowsUpdated())
            .then(log(settings, operator, "users.reset_password", userId,
                "重置用户密码", "密码已重新加密写入"))
            .thenReturn(userId);

        return complete(work, "用户密码重置成功。", true);
    }

    /**
     * 给用户分配后台管理组；只允许超级管理员执行，并逐项验证管理组真实存在。
     */
    public Mono<Map<String, Object>> assignAdminGroups(
        AdminAuthUser operator,
        long userId,
        Map<String, Object> request
    ) {
        privilegeGuard.requireSuperAdmin(operator, "分配管理组");
        DatabaseSettings settings = settings();

        Mono<Long> work = requireUser(settings, userId)
            .then(resolveAdminGroupIds(settings, request))
            .flatMap(groupIds -> {
                if (groupIds.isEmpty()) {
                    return Mono.error(new IllegalStateException("管理组不能为空。"));
                }
                return Flux.fromIterable(groupIds)
                    .concatMap(groupId -> requireExists(settings, "admin_groups", groupId,
                            "管理组不存在：" + groupId)
                        .then(insertAdminGroupRelation(settings, groupId, userId)))
                    .then(log(settings, operator, "users.assign_admin_groups", userId,
                        "分配管理组", "groupIds=" + groupIds))
                    .thenReturn(userId);
            });

        return complete(work, "管理组分配成功。", true);
    }

    /**
     * 移除用户后台管理组；禁止操作者移除自己的管理组，变更后撤销目标会话使权限立即生效。
     */
    public Mono<Map<String, Object>> removeAdminGroups(
        AdminAuthUser operator,
        long userId,
        Map<String, Object> request
    ) {
        privilegeGuard.requireSuperAdmin(operator, "移除管理组");
        requireNotSelf(operator, userId, "不能移除当前登录账号的管理组。");
        DatabaseSettings settings = settings();

        Mono<Long> work = requireUser(settings, userId)
            .then(resolveAdminGroupIds(settings, request))
            .flatMap(groupIds -> {
                if (groupIds.isEmpty()) {
                    return Mono.error(new IllegalStateException("管理组不能为空。"));
                }
                return Flux.fromIterable(groupIds)
                    .concatMap(groupId -> databaseClient.sql("delete from "
                            + table(settings, "admin_group_users")
                            + " where group_id = :groupId and user_id = :userId")
                        .bind("groupId", groupId).bind("userId", userId)
                        .fetch().rowsUpdated())
                    .then(log(settings, operator, "users.remove_admin_groups", userId,
                        "移除管理组", "groupIds=" + groupIds))
                    .thenReturn(userId);
            });

        return complete(work, "管理组移除成功。", true);
    }

    /**
     * 原子调整用户积分：同步余额、调整记录、积分流水和后台审计日志，任一步失败全部回滚。
     */
    public Mono<Map<String, Object>> adjustPoints(
        AdminAuthUser operator,
        long userId,
        Map<String, Object> request
    ) {
        DatabaseSettings settings = settings();
        long delta = requireNonZeroLong(request, "pointsDelta",
            "积分变动值不能为空且不能为 0。");
        String reason = optionalText(request, "reason", "后台手动调整积分");

        Mono<Long> work = requireTarget(settings, operator, userId, "调整积分")
            .then(ensureStatistics(settings, userId))
            .then(currentPoints(settings, userId))
            .flatMap(current -> {
                long balance = current + delta;
                return databaseClient.sql("update " + table(settings, "user_statistics")
                        + " set points = :balance, updated_at = current_timestamp where user_id = :id")
                    .bind("balance", balance).bind("id", userId).fetch().rowsUpdated()
                    .then(databaseClient.sql("insert into " + table(settings, "points_adjustments")
                            + " (user_id, operator_id, points_delta, reason) "
                            + "values (:userId, :operatorId, :delta, :reason)")
                        .bind("userId", userId).bind("operatorId", operator.id())
                        .bind("delta", delta).bind("reason", reason).fetch().rowsUpdated())
                    .then(databaseClient.sql("insert into " + table(settings, "points_logs")
                            + " (user_id, rule_key, points_delta, balance_after, source_type, "
                            + "source_id, remark) values (:userId, 'admin.adjust', :delta, "
                            + ":balance, 'admin', :operatorId, :reason)")
                        .bind("userId", userId).bind("delta", delta).bind("balance", balance)
                        .bind("operatorId", operator.id()).bind("reason", reason)
                        .fetch().rowsUpdated())
                    .then(log(settings, operator, "users.adjust_points", userId,
                        "调整用户积分", "delta=" + delta + ", balance=" + balance))
                    .thenReturn(userId);
            });

        return complete(work, "用户积分调整成功。", false);
    }

    private Mono<Map<String, Object>> changeStatus(
        AdminAuthUser operator,
        long userId,
        String status,
        String action,
        String message,
        String detail
    ) {
        DatabaseSettings settings = settings();
        Mono<Long> work = requireTarget(settings, operator, userId, "修改状态")
            .then(updateStatus(settings, userId, status))
            .then(log(settings, operator, action, userId, message, detail))
            .thenReturn(userId);
        return complete(work, message, !"ACTIVE".equals(status));
    }

    private Mono<Long> insertUser(
        DatabaseSettings settings,
        String username,
        String email,
        String hash,
        String displayName,
        String status,
        long groupId
    ) {
        String publicId = "AQUA_" + UUID.randomUUID().toString()
            .replace("-", "").toUpperCase();
        return uidAllocator.allocate().flatMap(uid ->
            databaseClient.sql("insert into " + table(settings, TableNames.USERS)
                    + " (uid, public_id, username, email, password_hash, display_name, status, "
                    + "group_id, register_source) values (:uid, :publicId, :username, :email, "
                    + ":hash, :displayName, :status, :groupId, 'admin')")
                .bind("uid", uid)
                .bind("publicId", publicId)
                .bind("username", username)
                .bind("email", email)
                .bind("hash", hash)
                .bind("displayName", displayName)
                .bind("status", status)
                .bind("groupId", groupId)
                .fetch().rowsUpdated()
                .then(databaseClient.sql("select id from "
                        + table(settings, TableNames.USERS)
                        + " where public_id = :publicId")
                    .bind("publicId", publicId)
                    .map((row, metadata) -> ((Number) row.get("id")).longValue())
                    .one())
        );
    }

    private Mono<Void> initializeUser(
        DatabaseSettings settings,
        AdminAuthUser operator,
        long userId
    ) {
        Mono<Long> role = databaseClient.sql("select id from " + table(settings, "roles")
                + " where role_key = 'user'")
            .map((row, metadata) -> ((Number) row.get("id")).longValue())
            .one();

        return databaseClient.sql("insert into " + table(settings, "user_statistics")
                + " (user_id) values (:id)").bind("id", userId).fetch().rowsUpdated()
            .then(role.flatMap(roleId -> databaseClient.sql("insert into "
                    + table(settings, "user_roles") + " (user_id, role_id) values (:userId, :roleId)")
                .bind("userId", userId).bind("roleId", roleId).fetch().rowsUpdated()))
            .then(log(settings, operator, "users.create", userId,
                "创建用户", "后台创建用户"));
    }

    private Mono<Map<String, Object>> requireTarget(
        DatabaseSettings settings,
        AdminAuthUser operator,
        long userId,
        String operation
    ) {
        requireOperator(operator);
        return requireUser(settings, userId).flatMap(user ->
            databaseClient.sql("select r.role_key from " + table(settings, "roles") + " r join "
                    + table(settings, "user_roles")
                    + " ur on ur.role_id = r.id where ur.user_id = :id")
                .bind("id", userId)
                .map((row, metadata) -> String.valueOf(row.get("role_key")))
                .all().collectList()
                .doOnNext(roles -> privilegeGuard.requireCanManageTarget(
                    operator, userId, roles, operation
                ))
                .thenReturn(user)
        );
    }

    private Mono<Map<String, Object>> requireUser(DatabaseSettings settings, long userId) {
        return databaseClient.sql("select * from " + table(settings, TableNames.USERS)
                + " where id = :id and uid is not null")
            .bind("id", userId)
            .map((row, metadata) -> {
                Map<String, Object> user = new LinkedHashMap<>();
                metadata.getColumnMetadatas().forEach(column ->
                    user.put(column.getName(), row.get(column.getName()))
                );
                return user;
            }).one()
            .switchIfEmpty(Mono.error(new IllegalStateException("用户不存在：" + userId)));
    }

    private Mono<Void> requireUnique(
        DatabaseSettings settings,
        String column,
        String value,
        Long excludedId,
        String message
    ) {
        String sql = "select count(1) as total from " + table(settings, "users")
            + " where " + column + " = :value"
            + (excludedId == null ? "" : " and id <> :excludedId");
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql).bind("value", value);
        if (excludedId != null) {
            spec = spec.bind("excludedId", excludedId);
        }
        return spec.map((row, metadata) -> ((Number) row.get("total")).longValue())
            .one().flatMap(total -> total > 0
                ? Mono.error(new IllegalStateException(message))
                : Mono.empty());
    }

    private Mono<Void> requireExists(
        DatabaseSettings settings,
        String logicalTable,
        long id,
        String message
    ) {
        return databaseClient.sql("select count(1) as total from "
                + table(settings, logicalTable) + " where id = :id")
            .bind("id", id)
            .map((row, metadata) -> ((Number) row.get("total")).longValue())
            .one().flatMap(total -> total > 0
                ? Mono.empty()
                : Mono.error(new IllegalStateException(message)));
    }

    private Mono<Long> resolveUserGroup(DatabaseSettings settings, Map<String, Object> request) {
        long requested = optionalLong(request.get("groupId"));
        if (requested > 0) {
            return requireExists(settings, "user_groups", requested, "用户组不存在。")
                .thenReturn(requested);
        }
        return databaseClient.sql("select id from " + table(settings, "user_groups")
                + " order by is_default desc, sort_order, id limit 1")
            .map((row, metadata) -> ((Number) row.get("id")).longValue())
            .one().switchIfEmpty(Mono.error(new IllegalStateException("尚未配置默认用户组。")));
    }

    private Mono<List<Long>> resolveAdminGroupIds(
        DatabaseSettings settings,
        Map<String, Object> request
    ) {
        List<Long> ids = new ArrayList<>();
        addIds(ids, request.get("groupIds"));
        long singleId = optionalLong(request.get("groupId"));
        if (singleId > 0 && !ids.contains(singleId)) {
            ids.add(singleId);
        }

        Object keysValue = request.get("groupKeys");
        List<String> keys = new ArrayList<>();
        if (keysValue instanceof List<?> list) {
            list.forEach(value -> keys.add(String.valueOf(value)));
        }
        Object singleKey = request.get("groupKey");
        if (singleKey != null) {
            keys.add(String.valueOf(singleKey));
        }

        return Flux.fromIterable(keys)
            .concatMap(key -> databaseClient.sql("select id from " + table(settings, "admin_groups")
                    + " where group_key = :key")
                .bind("key", key.trim())
                .map((row, metadata) -> ((Number) row.get("id")).longValue())
                .one()
                .switchIfEmpty(Mono.error(new IllegalStateException("管理组不存在：" + key))))
            .doOnNext(id -> {
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }).then(Mono.fromCallable(() -> List.copyOf(ids)));
    }

    private Mono<Void> insertAdminGroupRelation(
        DatabaseSettings settings,
        long groupId,
        long userId
    ) {
        return databaseClient.sql("select count(1) as total from "
                + table(settings, "admin_group_users")
                + " where group_id = :groupId and user_id = :userId")
            .bind("groupId", groupId).bind("userId", userId)
            .map((row, metadata) -> ((Number) row.get("total")).longValue())
            .one().flatMap(total -> total > 0
                ? Mono.empty()
                : databaseClient.sql("insert into " + table(settings, "admin_group_users")
                        + " (group_id, user_id) values (:groupId, :userId)")
                    .bind("groupId", groupId).bind("userId", userId)
                    .fetch().rowsUpdated().then());
    }

    private Mono<Void> ensureStatistics(DatabaseSettings settings, long userId) {
        return databaseClient.sql("select count(1) as total from "
                + table(settings, "user_statistics") + " where user_id = :id")
            .bind("id", userId)
            .map((row, metadata) -> ((Number) row.get("total")).longValue())
            .one().flatMap(total -> total > 0
                ? Mono.empty()
                : databaseClient.sql("insert into " + table(settings, "user_statistics")
                        + " (user_id) values (:id)").bind("id", userId)
                    .fetch().rowsUpdated().then());
    }

    private Mono<Long> currentPoints(DatabaseSettings settings, long userId) {
        return databaseClient.sql("select points from " + table(settings, "user_statistics")
                + " where user_id = :id")
            .bind("id", userId)
            .map((row, metadata) -> ((Number) row.get("points")).longValue())
            .one();
    }

    private Mono<Void> updateStatus(DatabaseSettings settings, long userId, String status) {
        return databaseClient.sql("update " + table(settings, "users")
                + " set status = :status, updated_at = current_timestamp where id = :id")
            .bind("status", status).bind("id", userId).fetch().rowsUpdated().then();
    }

    private Mono<Void> deleteByUserId(
        DatabaseSettings settings,
        String logicalTable,
        long userId
    ) {
        return databaseClient.sql("delete from " + table(settings, logicalTable)
                + " where user_id = :id")
            .bind("id", userId)
            .fetch()
            .rowsUpdated()
            .then();
    }

    private Map<String, Object> deletedUserResult(long userId, long uid) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", userId);
        data.put("uid", uid);
        data.put("status", "DELETED");
        return data;
    }

    private Mono<Void> log(
        DatabaseSettings settings,
        AdminAuthUser operator,
        String action,
        long userId,
        String summary,
        String detail
    ) {
        return databaseClient.sql("insert into " + table(settings, "admin_operation_logs")
                + " (operator_id, action_key, target_type, target_id, summary, detail) "
                + "values (:operatorId, :action, 'user', :targetId, :summary, :detail)")
            .bind("operatorId", operator.id()).bind("action", action)
            .bind("targetId", userId).bind("summary", summary).bind("detail", detail)
            .fetch().rowsUpdated().then();
    }

    private Mono<Map<String, Object>> complete(
        Mono<Long> work,
        String message,
        boolean revokeSessions
    ) {
        return transactionalOperator.transactional(work)
            .doOnNext(userId -> {
                if (revokeSessions) {
                    adminAuthService.revokeUserSessions(userId);
                }
            })
            .flatMap(userId -> queryService.userDetail(userId)
                .map(user -> result(message, user)));
    }

    private DatabaseClient.GenericExecuteSpec bindNullable(
        DatabaseClient.GenericExecuteSpec spec,
        String name,
        Object value,
        Class<?> type
    ) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private Map<String, Object> result(String message, Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", message);
        result.put("data", data);
        return result;
    }

    private DatabaseSettings settings() {
        DatabaseSettings settings = settingsService.current();
        if (settings == null) {
            throw new IllegalStateException("尚未找到数据库运行配置。");
        }
        return settings.normalized();
    }

    private String table(DatabaseSettings settings, String logicalName) {
        return TableNameResolver.tableName(settings.tablePrefix(), logicalName);
    }

    private void requireOperator(AdminAuthUser operator) {
        if (operator == null || !operator.hasAdminAccess()) {
            throw new IllegalStateException("当前登录账号没有后台管理权限。");
        }
    }

    private void requireNotSelf(AdminAuthUser operator, long userId, String message) {
        requireOperator(operator);
        if (operator.id() == userId) {
            throw new IllegalStateException(message);
        }
    }

    private String requireText(Map<String, Object> request, String key, String message) {
        String value = optionalText(request, key, "");
        if (value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private String optionalText(Map<String, Object> request, String key, String fallback) {
        Object value = request == null ? null : request.get(key);
        return value == null || String.valueOf(value).isBlank()
            ? fallback
            : String.valueOf(value).trim();
    }

    private long requireLong(Map<String, Object> request, String key, String message) {
        long value = optionalLong(request == null ? null : request.get(key));
        if (value <= 0) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private long requireNonZeroLong(Map<String, Object> request, String key, String message) {
        long value = optionalLong(request == null ? null : request.get(key));
        if (value == 0) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private long optionalLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void addIds(List<Long> target, Object value) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                long id = optionalLong(item);
                if (id > 0 && !target.contains(id)) {
                    target.add(id);
                }
            }
        }
    }

    private LocalDateTime optionalDateTime(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (Exception error) {
            throw new IllegalStateException("封禁到期时间格式不正确，应使用 ISO 日期时间。", error);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
