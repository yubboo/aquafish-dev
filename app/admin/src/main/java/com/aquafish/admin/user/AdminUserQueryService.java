package com.aquafish.admin.user;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.R2dbcPaginationSql;
import com.aquafish.core.database.TableNameResolver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 后台用户响应式查询服务。
 *
 * <p>表结构完全由版本迁移负责，本服务只执行在线查询，不探测或修改结构。
 * {@code AdminUserQueryController} 负责 HTTP 参数和响应包装，本类使用当前运行时数据库
 * 配置、安装时确定的表前缀以及 R2DBC 参数绑定完成分页和关联数据聚合。</p>
 */
@Service
public class AdminUserQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final DatabaseRuntimeSettingsService settingsService;
    private final DatabaseClient databaseClient;

    public AdminUserQueryService(
        DatabaseRuntimeSettingsService settingsService,
        DatabaseClient databaseClient
    ) {
        this.settingsService = settingsService;
        this.databaseClient = databaseClient;
    }

    /**
     * 分页查询用户并补充角色、管理组、用户组和积分汇总。
     *
     * <p>所有筛选值使用 R2DBC 参数绑定；页大小最大 100，避免后台误操作形成超大查询。</p>
     */
    public Mono<Map<String, Object>> listUsers(
        Integer page,
        Integer pageSize,
        String keyword,
        String status,
        Boolean adminOnly
    ) {
        DatabaseSettings settings = settings();
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safePageSize = pageSize == null || pageSize < 1
            ? DEFAULT_PAGE_SIZE
            : Math.min(pageSize, MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safePageSize;
        String safeKeyword = text(keyword);
        String safeStatus = text(status).toUpperCase();
        boolean onlyAdmins = Boolean.TRUE.equals(adminOnly);
        Query query = userQuery(settings, safeKeyword, safeStatus, onlyAdmins);

        Mono<Long> total = bind(
            databaseClient.sql("select count(1) as total from "
                + table(settings, "users") + " u " + query.where()),
            query
        )
            .map((row, metadata) -> number(row.get("total")))
            .one()
            .defaultIfEmpty(0L);

        String selectSql = R2dbcPaginationSql.limitOffset(
            "select u.id, u.uid, u.public_id, u.username, u.email, u.display_name, "
            + "u.avatar, u.status, u.group_id, u.created_at, u.updated_at, "
            + "u.last_login_at, u.last_login_ip, u.login_count "
            + "from " + table(settings, "users") + " u "
            + query.where() + " order by u.id desc",
            safePageSize,
            offset
        );

        DatabaseClient.GenericExecuteSpec select = bind(
            databaseClient.sql(selectSql),
            query
        );

        Mono<List<Map<String, Object>>> items = select
            .map((row, metadata) -> rowMap(row, metadata))
            .all()
            .flatMapSequential(user -> enrichSummary(settings, user))
            .collectList();

        return Mono.zip(total, items).map(result -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("page", safePage);
            data.put("pageSize", safePageSize);
            data.put("total", result.getT1());
            data.put("totalPages", (result.getT1() + safePageSize - 1) / safePageSize);
            data.put("keyword", safeKeyword);
            data.put("status", safeStatus);
            data.put("adminOnly", onlyAdmins);
            data.put("items", result.getT2());
            return data;
        });
    }

    /** 查询单个用户，并聚合资料、标签、封禁历史和最近积分流水。 */
    public Mono<Map<String, Object>> userDetail(long id) {
        DatabaseSettings settings = settings();

        return databaseClient
            .sql("select * from " + table(settings, "users")
                + " where id = :id and uid is not null")
            .bind("id", id)
            .map((row, metadata) -> rowMap(row, metadata))
            .one()
            .switchIfEmpty(Mono.error(new NoSuchElementException("用户不存在：" + id)))
            .flatMap(user -> enrichDetail(settings, user));
    }

    /** 返回前台用户组字典；实际表名自动带安装时配置的前缀。 */
    public Mono<Map<String, Object>> listUserGroups() {
        return listReferenceTable("user_groups", 200);
    }

    /** 返回角色字典，用于解释用户通过哪些角色获得权限。 */
    public Mono<Map<String, Object>> listRoles() {
        return listReferenceTable("roles", 200);
    }

    /** 返回后台管理组字典，用于超级管理员授权。 */
    public Mono<Map<String, Object>> listAdminGroups() {
        return listReferenceTable("admin_groups", 200);
    }

    private Mono<Map<String, Object>> listReferenceTable(String logicalName, int limit) {
        DatabaseSettings settings = settings();
        String table = table(settings, logicalName);

        return databaseClient.sql(R2dbcPaginationSql.limit(
                "select * from " + table + " order by sort_order asc, id asc",
                limit
            ))
            .map((row, metadata) -> rowMap(row, metadata))
            .all()
            .collectList()
            .map(items -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("table", table);
                data.put("total", items.size());
                data.put("items", items);
                return data;
            });
    }

    private Mono<Map<String, Object>> enrichSummary(
        DatabaseSettings settings,
        Map<String, Object> user
    ) {
        long userId = number(user.get("id"));
        Mono<List<Map<String, Object>>> roles = roles(settings, userId);
        Mono<List<Map<String, Object>>> adminGroups = adminGroups(settings, userId);
        Mono<Map<String, Object>> group = optionalOne(
            "select * from " + table(settings, "user_groups") + " where id = :id",
            "id",
            user.get("group_id")
        );
        Mono<Map<String, Object>> statistics = optionalOne(
            "select * from " + table(settings, "user_statistics")
                + " where user_id = :id order by id desc limit 1",
            "id",
            userId
        );

        return Mono.zip(roles, adminGroups, group, statistics)
            .map(result -> {
                user.put("roles", result.getT1());
                user.put("adminGroups", result.getT2());
                user.put("userGroup", emptyToNull(result.getT3()));
                user.put("statistics", emptyToNull(result.getT4()));
                addCompatibilityFields(user);
                return user;
            });
    }

    private Mono<Map<String, Object>> enrichDetail(
        DatabaseSettings settings,
        Map<String, Object> user
    ) {
        long userId = number(user.get("id"));

        return Mono.zip(
            enrichSummary(settings, user),
            optionalOne("select * from " + table(settings, "user_profiles")
                + " where user_id = :id order by id desc limit 1", "id", userId),
            list("select t.* from " + table(settings, "user_tags") + " t join "
                + table(settings, "user_tag_relations")
                + " r on r.tag_id = t.id where r.user_id = :id order by t.sort_order, t.id", userId),
            list("select * from " + table(settings, "user_bans")
                + " where user_id = :id order by id desc limit 20", userId),
            list("select * from " + table(settings, "points_logs")
                + " where user_id = :id order by id desc limit 20", userId)
        ).map(result -> {
            Map<String, Object> enriched = result.getT1();
            enriched.put("profile", emptyToNull(result.getT2()));
            enriched.put("tags", result.getT3());
            enriched.put("bans", result.getT4());
            enriched.put("recentPointsLogs", result.getT5());
            return enriched;
        });
    }

    private Mono<List<Map<String, Object>>> roles(DatabaseSettings settings, long userId) {
        return list("select r.* from " + table(settings, "roles") + " r join "
            + table(settings, "user_roles")
            + " ur on ur.role_id = r.id where ur.user_id = :id order by r.id", userId);
    }

    private Mono<List<Map<String, Object>>> adminGroups(DatabaseSettings settings, long userId) {
        return list("select g.* from " + table(settings, "admin_groups") + " g join "
            + table(settings, "admin_group_users")
            + " gu on gu.group_id = g.id where gu.user_id = :id "
            + "order by g.sort_order, g.id", userId);
    }

    private Mono<List<Map<String, Object>>> list(String sql, long id) {
        return databaseClient.sql(sql)
            .bind("id", id)
            .map((row, metadata) -> rowMap(row, metadata))
            .all()
            .collectList();
    }

    private Mono<Map<String, Object>> optionalOne(
        String sql,
        String parameter,
        Object value
    ) {
        if (value == null) {
            return Mono.just(new LinkedHashMap<>());
        }

        return databaseClient.sql(sql)
            .bind(parameter, value)
            .map((row, metadata) -> rowMap(row, metadata))
            .one()
            .defaultIfEmpty(new LinkedHashMap<>());
    }

    private Query userQuery(
        DatabaseSettings settings,
        String keyword,
        String status,
        boolean adminOnly
    ) {
        StringBuilder where = new StringBuilder("where u.uid is not null");
        Map<String, Object> parameters = new LinkedHashMap<>();

        if (!keyword.isBlank()) {
            where.append(" and (lower(u.username) like :keyword")
                .append(" or lower(coalesce(u.email, '')) like :keyword")
                .append(" or lower(coalesce(u.display_name, '')) like :keyword)");
            parameters.put("keyword", "%" + keyword.toLowerCase() + "%");
        }

        if (!status.isBlank()) {
            where.append(" and u.status = :status");
            parameters.put("status", status);
        }

        if (adminOnly) {
            where.append(" and (exists (select 1 from ")
                .append(table(settings, "user_roles")).append(" ur join ")
                .append(table(settings, "roles"))
                .append(" r on r.id = ur.role_id where ur.user_id = u.id ")
                .append("and r.role_key in ('admin', 'super_admin')) or exists (select 1 from ")
                .append(table(settings, "admin_group_users"))
                .append(" gu where gu.user_id = u.id))");
        }

        return new Query(where.toString(), parameters);
    }

    private DatabaseClient.GenericExecuteSpec bind(
        DatabaseClient.GenericExecuteSpec spec,
        Query query
    ) {
        DatabaseClient.GenericExecuteSpec bound = spec;
        for (Map.Entry<String, Object> entry : query.parameters().entrySet()) {
            bound = bound.bind(entry.getKey(), entry.getValue());
        }
        return bound;
    }

    private Map<String, Object> rowMap(
        io.r2dbc.spi.Row row,
        io.r2dbc.spi.RowMetadata metadata
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        metadata.getColumnMetadatas().forEach(column ->
            result.put(column.getName(), row.get(column.getName()))
        );
        return result;
    }

    private void addCompatibilityFields(Map<String, Object> user) {
        user.put("displayName", user.get("display_name"));
        user.put("groupId", user.get("group_id"));
        user.put("createdAt", user.get("created_at"));
        user.put("updatedAt", user.get("updated_at"));
        user.put("lastLoginAt", user.get("last_login_at"));
    }

    private Object emptyToNull(Map<String, Object> value) {
        return value.isEmpty() ? null : value;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
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

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private record Query(String where, Map<String, Object> parameters) {
    }
}
