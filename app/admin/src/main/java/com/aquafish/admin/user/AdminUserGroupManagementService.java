package com.aquafish.admin.user;

import com.aquafish.core.admin.auth.AdminAuthUser;
import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * 用户组与后台管理组的响应式写服务。
 *
 * <p>用户组控制前台会员身份，管理组控制后台管理权限，两套数据不能混用。本服务只允许
 * 超级管理员修改分组，并通过响应式事务保证“分组变更、关联清理、审计日志”同时成功或
 * 同时回滚。真实表名始终通过 {@link TableNameResolver} 和安装时表前缀解析。</p>
 */
@Service
public class AdminUserGroupManagementService {

    private static final Pattern GROUP_KEY_PATTERN =
        Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    private final DatabaseRuntimeSettingsService settingsService;
    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final AdminUserPrivilegeGuard privilegeGuard;
    private final AdminUserQueryService queryService;

    public AdminUserGroupManagementService(
        DatabaseRuntimeSettingsService settingsService,
        DatabaseClient databaseClient,
        TransactionalOperator transactionalOperator,
        AdminUserPrivilegeGuard privilegeGuard,
        AdminUserQueryService queryService
    ) {
        this.settingsService = settingsService;
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
        this.privilegeGuard = privilegeGuard;
        this.queryService = queryService;
    }

    /* ======================================================================
     * BEGIN：前台用户组生命周期
     * ====================================================================== */

    /** 创建前台用户组；勾选默认组时会在同一事务中取消旧默认组。 */
    public Mono<Map<String, Object>> createUserGroup(
        AdminAuthUser operator,
        Map<String, Object> request
    ) {
        privilegeGuard.requireSuperAdmin(operator, "创建用户组");
        DatabaseSettings settings = settings();
        GroupInput input = userGroupInput(request, null);

        Mono<Long> work = requireUniqueKey(
                settings,
                TableNames.USER_GROUPS,
                input.groupKey(),
                null,
                "用户组 Key 已存在。"
            )
            .then(input.flag()
                ? clearUserGroupDefault(settings, null)
                : Mono.empty())
            .then(insertUserGroup(settings, input))
            .flatMap(id -> log(
                settings,
                operator,
                "user_groups.create",
                "user_group",
                id,
                "创建用户组",
                input.groupKey()
            ).thenReturn(id));

        return complete(
            work,
            "用户组创建成功。",
            queryService::listUserGroups
        );
    }

    /**
     * 修改前台用户组。
     *
     * <p>当前默认组不能直接取消默认，管理员应先把另一个组设为默认；这样系统始终至少
     * 保留一个可供新注册用户使用的默认组。</p>
     */
    public Mono<Map<String, Object>> updateUserGroup(
        AdminAuthUser operator,
        long groupId,
        Map<String, Object> request
    ) {
        privilegeGuard.requireSuperAdmin(operator, "修改用户组");
        DatabaseSettings settings = settings();

        Mono<Long> work = requireGroup(
                settings,
                TableNames.USER_GROUPS,
                groupId,
                "用户组不存在。"
            )
            .flatMap(current -> {
                GroupInput input = userGroupInput(request, current);
                boolean currentDefault = bool(current.get("is_default"), false);
                if (currentDefault && !input.flag()) {
                    return Mono.error(new IllegalStateException(
                        "默认用户组不能直接取消，请先把其他用户组设为默认。"
                    ));
                }

                return requireUniqueKey(
                        settings,
                        TableNames.USER_GROUPS,
                        input.groupKey(),
                        groupId,
                        "用户组 Key 已存在。"
                    )
                    .then(input.flag()
                        ? clearUserGroupDefault(settings, groupId)
                        : Mono.empty())
                    .then(updateUserGroupRow(settings, groupId, input))
                    .then(log(
                        settings,
                        operator,
                        "user_groups.update",
                        "user_group",
                        groupId,
                        "修改用户组",
                        input.groupKey()
                    ))
                    .thenReturn(groupId);
            });

        return complete(
            work,
            "用户组修改成功。",
            queryService::listUserGroups
        );
    }

    /**
     * 删除空的非默认用户组。
     *
     * <p>有关联用户时必须先迁移用户；删除组时同步清理该组的前台权限记录。</p>
     */
    public Mono<Map<String, Object>> deleteUserGroup(
        AdminAuthUser operator,
        long groupId
    ) {
        privilegeGuard.requireSuperAdmin(operator, "删除用户组");
        DatabaseSettings settings = settings();

        Mono<Long> work = requireGroup(
                settings,
                TableNames.USER_GROUPS,
                groupId,
                "用户组不存在。"
            )
            .flatMap(current -> {
                if (bool(current.get("is_default"), false)) {
                    return Mono.error(new IllegalStateException("默认用户组不能删除。"));
                }
                return requireNoRelation(
                        settings,
                        TableNames.USERS,
                        "group_id",
                        groupId,
                        "用户组仍有关联用户，请先迁移用户。"
                    )
                    .then(deleteByColumn(
                        settings,
                        TableNames.USER_GROUP_PERMISSIONS,
                        "group_id",
                        groupId
                    ))
                    .then(deleteGroup(settings, TableNames.USER_GROUPS, groupId))
                    .then(log(
                        settings,
                        operator,
                        "user_groups.delete",
                        "user_group",
                        groupId,
                        "删除用户组",
                        text(current.get("group_key"))
                    ))
                    .thenReturn(groupId);
            });

        return complete(
            work,
            "用户组删除成功。",
            queryService::listUserGroups
        );
    }

    /* END：前台用户组生命周期。 */

    /* ======================================================================
     * BEGIN：后台管理组生命周期
     * ====================================================================== */

    /** 创建自定义后台管理组；新建组永远不是内置组。 */
    public Mono<Map<String, Object>> createAdminGroup(
        AdminAuthUser operator,
        Map<String, Object> request
    ) {
        privilegeGuard.requireSuperAdmin(operator, "创建管理组");
        DatabaseSettings settings = settings();
        GroupInput input = adminGroupInput(request, null);

        Mono<Long> work = requireUniqueKey(
                settings,
                TableNames.ADMIN_GROUPS,
                input.groupKey(),
                null,
                "管理组 Key 已存在。"
            )
            .then(insertAdminGroup(settings, input))
            .flatMap(id -> log(
                settings,
                operator,
                "admin_groups.create",
                "admin_group",
                id,
                "创建管理组",
                input.groupKey()
            ).thenReturn(id));

        return complete(
            work,
            "管理组创建成功。",
            queryService::listAdminGroups
        );
    }

    /**
     * 修改后台管理组；内置组的稳定 Key 与启用状态受保护，但允许修改展示名称、说明和排序。
     */
    public Mono<Map<String, Object>> updateAdminGroup(
        AdminAuthUser operator,
        long groupId,
        Map<String, Object> request
    ) {
        privilegeGuard.requireSuperAdmin(operator, "修改管理组");
        DatabaseSettings settings = settings();

        Mono<Long> work = requireGroup(
                settings,
                TableNames.ADMIN_GROUPS,
                groupId,
                "管理组不存在。"
            )
            .flatMap(current -> {
                GroupInput requested = adminGroupInput(request, current);
                boolean builtIn = bool(current.get("built_in"), false);
                GroupInput input = builtIn
                    ? new GroupInput(
                        text(current.get("group_key")),
                        requested.name(),
                        requested.description(),
                        requested.sortOrder(),
                        true
                    )
                    : requested;

                return requireUniqueKey(
                        settings,
                        TableNames.ADMIN_GROUPS,
                        input.groupKey(),
                        groupId,
                        "管理组 Key 已存在。"
                    )
                    .then(updateAdminGroupRow(settings, groupId, input))
                    .then(log(
                        settings,
                        operator,
                        "admin_groups.update",
                        "admin_group",
                        groupId,
                        "修改管理组",
                        input.groupKey()
                    ))
                    .thenReturn(groupId);
            });

        return complete(
            work,
            "管理组修改成功。",
            queryService::listAdminGroups
        );
    }

    /**
     * 删除没有成员的自定义管理组；内置管理组永远不能删除。
     */
    public Mono<Map<String, Object>> deleteAdminGroup(
        AdminAuthUser operator,
        long groupId
    ) {
        privilegeGuard.requireSuperAdmin(operator, "删除管理组");
        DatabaseSettings settings = settings();

        Mono<Long> work = requireGroup(
                settings,
                TableNames.ADMIN_GROUPS,
                groupId,
                "管理组不存在。"
            )
            .flatMap(current -> {
                if (bool(current.get("built_in"), false)) {
                    return Mono.error(new IllegalStateException("系统内置管理组不能删除。"));
                }
                return requireNoRelation(
                        settings,
                        TableNames.ADMIN_GROUP_USERS,
                        "group_id",
                        groupId,
                        "管理组仍有管理员成员，请先移除成员。"
                    )
                    .then(deleteByColumn(
                        settings,
                        TableNames.ADMIN_GROUP_PERMISSIONS,
                        "group_id",
                        groupId
                    ))
                    .then(deleteGroup(settings, TableNames.ADMIN_GROUPS, groupId))
                    .then(log(
                        settings,
                        operator,
                        "admin_groups.delete",
                        "admin_group",
                        groupId,
                        "删除管理组",
                        text(current.get("group_key"))
                    ))
                    .thenReturn(groupId);
            });

        return complete(
            work,
            "管理组删除成功。",
            queryService::listAdminGroups
        );
    }

    /* END：后台管理组生命周期。 */

    /* ======================================================================
     * BEGIN：数据库写入与校验工具
     * ====================================================================== */

    private Mono<Long> insertUserGroup(DatabaseSettings settings, GroupInput input) {
        return databaseClient.sql(
                "insert into " + table(settings, TableNames.USER_GROUPS)
                    + " (group_key, name, description, sort_order, is_default) "
                    + "values (:groupKey, :name, :description, :sortOrder, :isDefault)"
            )
            .bind("groupKey", input.groupKey())
            .bind("name", input.name())
            .bind("description", input.description())
            .bind("sortOrder", input.sortOrder())
            .bind("isDefault", input.flag() ? 1 : 0)
            .fetch()
            .rowsUpdated()
            .then(findGroupId(settings, TableNames.USER_GROUPS, input.groupKey()));
    }

    private Mono<Void> updateUserGroupRow(
        DatabaseSettings settings,
        long groupId,
        GroupInput input
    ) {
        return databaseClient.sql(
                "update " + table(settings, TableNames.USER_GROUPS)
                    + " set group_key = :groupKey, name = :name, description = :description, "
                    + "sort_order = :sortOrder, is_default = :isDefault, "
                    + "updated_at = current_timestamp where id = :id"
            )
            .bind("groupKey", input.groupKey())
            .bind("name", input.name())
            .bind("description", input.description())
            .bind("sortOrder", input.sortOrder())
            .bind("isDefault", input.flag() ? 1 : 0)
            .bind("id", groupId)
            .fetch()
            .rowsUpdated()
            .then();
    }

    private Mono<Void> clearUserGroupDefault(
        DatabaseSettings settings,
        Long exceptGroupId
    ) {
        String sql = "update " + table(settings, TableNames.USER_GROUPS)
            + " set is_default = 0, updated_at = current_timestamp where is_default = 1"
            + (exceptGroupId == null ? "" : " and id <> :id");
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        if (exceptGroupId != null) {
            spec = spec.bind("id", exceptGroupId);
        }
        return spec.fetch().rowsUpdated().then();
    }

    private Mono<Long> insertAdminGroup(DatabaseSettings settings, GroupInput input) {
        return databaseClient.sql(
                "insert into " + table(settings, TableNames.ADMIN_GROUPS)
                    + " (group_key, name, description, built_in, enabled, sort_order) "
                    + "values (:groupKey, :name, :description, 0, :enabled, :sortOrder)"
            )
            .bind("groupKey", input.groupKey())
            .bind("name", input.name())
            .bind("description", input.description())
            .bind("enabled", input.flag() ? 1 : 0)
            .bind("sortOrder", input.sortOrder())
            .fetch()
            .rowsUpdated()
            .then(findGroupId(settings, TableNames.ADMIN_GROUPS, input.groupKey()));
    }

    private Mono<Void> updateAdminGroupRow(
        DatabaseSettings settings,
        long groupId,
        GroupInput input
    ) {
        return databaseClient.sql(
                "update " + table(settings, TableNames.ADMIN_GROUPS)
                    + " set group_key = :groupKey, name = :name, description = :description, "
                    + "enabled = :enabled, sort_order = :sortOrder, "
                    + "updated_at = current_timestamp where id = :id"
            )
            .bind("groupKey", input.groupKey())
            .bind("name", input.name())
            .bind("description", input.description())
            .bind("enabled", input.flag() ? 1 : 0)
            .bind("sortOrder", input.sortOrder())
            .bind("id", groupId)
            .fetch()
            .rowsUpdated()
            .then();
    }

    private Mono<Map<String, Object>> requireGroup(
        DatabaseSettings settings,
        String logicalTable,
        long groupId,
        String message
    ) {
        return databaseClient.sql(
                "select * from " + table(settings, logicalTable) + " where id = :id"
            )
            .bind("id", groupId)
            .map(this::rowMap)
            .one()
            .switchIfEmpty(Mono.error(new IllegalStateException(message)));
    }

    private Mono<Void> requireUniqueKey(
        DatabaseSettings settings,
        String logicalTable,
        String groupKey,
        Long excludedId,
        String message
    ) {
        String sql = "select count(1) as total from " + table(settings, logicalTable)
            + " where group_key = :groupKey"
            + (excludedId == null ? "" : " and id <> :id");
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
            .bind("groupKey", groupKey);
        if (excludedId != null) {
            spec = spec.bind("id", excludedId);
        }
        return spec.map((row, metadata) -> number(row.get("total")))
            .one()
            .flatMap(total -> total > 0
                ? Mono.error(new IllegalStateException(message))
                : Mono.empty());
    }

    private Mono<Void> requireNoRelation(
        DatabaseSettings settings,
        String logicalTable,
        String column,
        long groupId,
        String message
    ) {
        return databaseClient.sql(
                "select count(1) as total from " + table(settings, logicalTable)
                    + " where " + column + " = :id"
            )
            .bind("id", groupId)
            .map((row, metadata) -> number(row.get("total")))
            .one()
            .flatMap(total -> total > 0
                ? Mono.error(new IllegalStateException(message))
                : Mono.empty());
    }

    private Mono<Void> deleteByColumn(
        DatabaseSettings settings,
        String logicalTable,
        String column,
        long id
    ) {
        return databaseClient.sql(
                "delete from " + table(settings, logicalTable)
                    + " where " + column + " = :id"
            )
            .bind("id", id)
            .fetch()
            .rowsUpdated()
            .then();
    }

    private Mono<Void> deleteGroup(
        DatabaseSettings settings,
        String logicalTable,
        long groupId
    ) {
        return databaseClient.sql(
                "delete from " + table(settings, logicalTable) + " where id = :id"
            )
            .bind("id", groupId)
            .fetch()
            .rowsUpdated()
            .then();
    }

    private Mono<Long> findGroupId(
        DatabaseSettings settings,
        String logicalTable,
        String groupKey
    ) {
        return databaseClient.sql(
                "select id from " + table(settings, logicalTable)
                    + " where group_key = :groupKey"
            )
            .bind("groupKey", groupKey)
            .map((row, metadata) -> number(row.get("id")))
            .one()
            .switchIfEmpty(Mono.error(new IllegalStateException("分组写入后无法读取。")));
    }

    private Mono<Void> log(
        DatabaseSettings settings,
        AdminAuthUser operator,
        String action,
        String targetType,
        long targetId,
        String summary,
        String detail
    ) {
        return databaseClient.sql(
                "insert into " + table(settings, TableNames.ADMIN_OPERATION_LOGS)
                    + " (operator_id, action_key, target_type, target_id, summary, detail) "
                    + "values (:operatorId, :action, :targetType, :targetId, :summary, :detail)"
            )
            .bind("operatorId", operator.id())
            .bind("action", action)
            .bind("targetType", targetType)
            .bind("targetId", targetId)
            .bind("summary", summary)
            .bind("detail", detail)
            .fetch()
            .rowsUpdated()
            .then();
    }

    private Mono<Map<String, Object>> complete(
        Mono<Long> work,
        String message,
        Supplier<Mono<Map<String, Object>>> refresh
    ) {
        return transactionalOperator.transactional(work)
            .then(refresh.get())
            .map(data -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("message", message);
                result.put("data", data);
                return result;
            });
    }

    /* END：数据库写入与校验工具。 */

    /* ======================================================================
     * BEGIN：输入规范化
     * ====================================================================== */

    private GroupInput userGroupInput(
        Map<String, Object> request,
        Map<String, Object> current
    ) {
        return groupInput(request, current, "isDefault", false);
    }

    private GroupInput adminGroupInput(
        Map<String, Object> request,
        Map<String, Object> current
    ) {
        return groupInput(request, current, "enabled", true);
    }

    private GroupInput groupInput(
        Map<String, Object> request,
        Map<String, Object> current,
        String flagKey,
        boolean defaultFlag
    ) {
        String currentKey = current == null ? "" : text(current.get("group_key"));
        String currentName = current == null ? "" : text(current.get("name"));
        String currentDescription = current == null
            ? ""
            : text(current.get("description"));
        int currentSort = current == null ? 100 : integer(current.get("sort_order"), 100);
        boolean currentFlag = current == null
            ? defaultFlag
            : bool(
                current.get("isDefault".equals(flagKey) ? "is_default" : "enabled"),
                defaultFlag
            );

        String groupKey = requestText(request, "groupKey", currentKey);
        String name = requestText(request, "name", currentName);
        String description = requestText(request, "description", currentDescription);
        int sortOrder = integer(request == null ? null : request.get("sortOrder"), currentSort);
        boolean flag = bool(request == null ? null : request.get(flagKey), currentFlag);

        if (!GROUP_KEY_PATTERN.matcher(groupKey).matches()) {
            throw new IllegalStateException(
                "分组 Key 必须以小写字母开头，只能包含小写字母、数字和下划线。"
            );
        }
        if (name.isBlank() || name.length() > 100) {
            throw new IllegalStateException("分组名称不能为空且不能超过 100 个字符。");
        }
        if (description.length() > 500) {
            throw new IllegalStateException("分组说明不能超过 500 个字符。");
        }
        if (sortOrder < 0 || sortOrder > 1_000_000) {
            throw new IllegalStateException("排序值必须在 0 到 1000000 之间。");
        }

        return new GroupInput(groupKey, name, description, sortOrder, flag);
    }

    private Map<String, Object> rowMap(Row row, RowMetadata metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        metadata.getColumnMetadatas().forEach(column ->
            result.put(column.getName(), row.get(column.getName()))
        );
        return result;
    }

    private String requestText(
        Map<String, Object> request,
        String key,
        String fallback
    ) {
        Object value = request == null ? null : request.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private boolean bool(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return "true".equalsIgnoreCase(String.valueOf(value))
            || "1".equals(String.valueOf(value));
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String table(DatabaseSettings settings, String logicalName) {
        return TableNameResolver.tableName(settings.tablePrefix(), logicalName);
    }

    private DatabaseSettings settings() {
        DatabaseSettings settings = settingsService.current();
        if (settings == null) {
            throw new IllegalStateException("尚未找到数据库运行配置。");
        }
        return settings.normalized();
    }

    private record GroupInput(
        String groupKey,
        String name,
        String description,
        int sortOrder,
        boolean flag
    ) {
    }

    /* END：输入规范化。 */
}
