package com.aquafish.admin.workspace;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.R2dbcPaginationSql;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 后台初版工作台只读查询服务。
 *
 * <p>本服务负责控制台统计和用户、论坛、内容管理中的跨表只读投影。所有可访问
 * 资源都在 {@link #RESOURCES} 中使用逻辑表名和显式列白名单登记，URL 不能直接
 * 变成表名或 SQL；密码摘要、会话令牌、认证密文等敏感列从未进入查询结果。</p>
 *
 * <p>领域写操作仍由 user、forum、content 各自的服务处理，本读模型不会在
 * Controller 中执行 DDL，也不会建立第二套 JDBC 数据访问。</p>
 */
@Service
public class AdminWorkspaceQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final Map<String, ResourceSpec> RESOURCES = Map.ofEntries(
        entry("users/profile-fields", TableNames.USER_PROFILE_FIELDS,
            "用户栏目", "id, field_key, name, field_type, required_flag, editable_flag, "
                + "public_flag, audit_required, sort_order, enabled, updated_at", "", "sort_order, id"),
        entry("users/statistics", TableNames.USER_STATISTICS,
            "资料统计", "id, user_id, posts_count, threads_count, comments_count, "
                + "followers_count, following_count, friends_count, points, credits, last_active_at",
            "", "id desc"),
        entry("users/tags", TableNames.USER_TAGS,
            "用户标签", "id, tag_key, name, color, description, built_in, enabled, sort_order, updated_at",
            "", "sort_order, id"),
        entry("users/bans", TableNames.USER_BANS,
            "禁止用户", "id, user_id, ban_type, reason, operator_id, started_at, expired_at, enabled, created_at",
            "", "id desc"),
        entry("users/ip-bans", TableNames.IP_BANS,
            "禁止 IP", "id, ip_value, ip_version, ban_type, reason, operator_id, "
                + "started_at, expired_at, enabled, created_at", "", "id desc"),
        entry("users/points", TableNames.POINTS_LOGS,
            "积分奖惩", "id, user_id, rule_key, points_delta, balance_after, source_type, "
                + "source_id, remark, created_at", "", "id desc"),
        entry("users/relationships", TableNames.USER_RELATIONSHIPS,
            "用户关系", "id, source_user_id, target_user_id, relationship_type, status, created_at, updated_at",
            "", "id desc"),
        entry("users/profile-audits", TableNames.USER_PROFILE_AUDITS,
            "资料审核", "id, user_id, field_key, audit_status, audit_message, operator_id, created_at, updated_at",
            "", "id desc"),
        entry("users/verifications", TableNames.USER_VERIFICATIONS,
            "认证记录", "id, user_id, verification_type, status, reviewer_user_id, review_note, "
                + "verified_at, expires_at, created_at, updated_at", "", "id desc"),

        entry("forum/threads", TableNames.FORUM_THREADS,
            "帖子管理", "id, section_id, author_user_id, title, status, moderation_status, "
                + "pinned_level, featured_level, reply_count, view_count, last_reply_at, created_at",
            "", "id desc"),
        entry("forum/replies", TableNames.FORUM_POSTS,
            "回帖管理", "id, thread_id, section_id, author_user_id, floor_number, content_text, "
                + "status, moderation_status, quoted_post_id, edited_at, created_at",
            "floor_number > 1", "id desc"),
        entry("forum/reports", TableNames.FORUM_REPORTS,
            "举报管理", "id, reporter_user_id, section_id, target_type, target_id, reason_code, "
                + "reason_text, status, assignee_user_id, resolution, resolved_at, created_at",
            "", "id desc"),
        entry("forum/moderation", TableNames.FORUM_MODERATION_ACTIONS,
            "审核记录", "id, section_id, target_type, target_id, action_type, operator_user_id, "
                + "reason, created_at", "", "id desc"),

        entry("content/categories", TableNames.CONTENT_CATEGORIES,
            "分类管理", "id, parent_id, category_key, name, slug, description, sort_order, "
                + "article_count, enabled, updated_at", "", "sort_order, id"),
        entry("content/tags", TableNames.CONTENT_TAGS,
            "标签管理", "id, tag_key, name, slug, description, article_count, enabled, updated_at",
            "", "id"),
        entry("content/pages", TableNames.CONTENT_PAGES,
            "单页管理", "id, parent_id, author_user_id, title, slug, template_key, status, "
                + "visibility, sort_order, published_at, updated_at", "", "id desc"),
        entry("content/comments", TableNames.CONTENT_COMMENTS,
            "评论管理", "id, target_type, target_id, parent_id, author_user_id, guest_name, "
                + "content_text, status, reviewer_user_id, reviewed_at, created_at",
            "", "id desc")
    );

    private static final Map<String, String> DASHBOARD_TABLES = Map.of(
        "users", TableNames.USERS,
        "sections", TableNames.FORUM_SECTIONS,
        "threads", TableNames.FORUM_THREADS,
        "articles", TableNames.CONTENT_ARTICLES,
        "themes", TableNames.THEMES
    );

    private final DatabaseRuntimeSettingsService settingsService;
    private final DatabaseClient databaseClient;

    public AdminWorkspaceQueryService(
        DatabaseRuntimeSettingsService settingsService,
        DatabaseClient databaseClient
    ) {
        this.settingsService = settingsService;
        this.databaseClient = databaseClient;
    }

    /**
     * 返回控制台真实业务数量；每个计数只访问一个受控逻辑表。
     */
    public Mono<Map<String, Object>> dashboard() {
        return Flux.fromIterable(DASHBOARD_TABLES.entrySet())
            .flatMapSequential(item -> count(item.getValue())
                .map(value -> Map.entry(item.getKey(), value)), 3)
            .collectMap(Map.Entry::getKey, Map.Entry::getValue, LinkedHashMap::new)
            .map(counts -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("counts", counts);
                result.put("databaseType", settingsService.current().type().value());
                result.put("tablePrefix", settingsService.current().tablePrefix());
                result.put("publicEntry", "/");
                result.put("forumEntry", "/forum");
                return result;
            });
    }

    /**
     * 按白名单资源分页读取真实表记录。
     *
     * @param domain 资源域，例如 users、forum、content
     * @param resource 域内资源标识
     * @param page 页码，从 1 开始
     * @param pageSize 页大小，最大 100
     */
    public Mono<Map<String, Object>> resource(
        String domain,
        String resource,
        Integer page,
        Integer pageSize
    ) {
        String key = text(domain) + "/" + text(resource);
        ResourceSpec spec = RESOURCES.get(key);
        if (spec == null) {
            return Mono.error(new IllegalArgumentException("未知后台数据资源：" + key));
        }

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = pageSize == null || pageSize < 1
            ? DEFAULT_PAGE_SIZE
            : Math.min(pageSize, MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;
        String table = table(spec.logicalTable());
        String where = spec.whereClause().isBlank()
            ? ""
            : " where " + spec.whereClause();

        Mono<Long> total = databaseClient.sql(
                "select count(*) as row_count from " + table + where
            )
            .map((row, metadata) -> number(row.get("row_count")))
            .one()
            .defaultIfEmpty(0L);

        /*
         * BEGIN：R2DBC 分页 SQL
         *
         * MySQL R2DBC 驱动不会稳定地把 LIMIT / OFFSET 参数当作单条查询参数处理，
         * 使用命名绑定会触发 executeMany 和 bad SQL grammar。这里的两个数字已经
         * 在服务端完成范围收敛，不接收原始字符串，因此可以安全写入 SQL 字面量。
         */
        String itemsSql = R2dbcPaginationSql.limitOffset(
            "select " + spec.columns() + " from " + table + where
                + " order by " + spec.orderBy(),
            safeSize,
            offset
        );

        Mono<List<Map<String, Object>>> items = databaseClient.sql(itemsSql)
            .map(this::rowMap)
            .all()
            .collectList();
        /* END：R2DBC 分页 SQL */

        return Mono.zip(total, items).map(result -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("domain", text(domain));
            data.put("resource", text(resource));
            data.put("title", spec.title());
            data.put("table", table);
            data.put("page", safePage);
            data.put("pageSize", safeSize);
            data.put("total", result.getT1());
            data.put("totalPages", (result.getT1() + safeSize - 1L) / safeSize);
            data.put("columns", List.of(spec.columns().split(",\\s*")));
            data.put("items", result.getT2());
            return data;
        });
    }

    private Mono<Long> count(String logicalTable) {
        return databaseClient.sql(
                "select count(*) as row_count from " + table(logicalTable)
            )
            .map((row, metadata) -> number(row.get("row_count")))
            .one()
            .defaultIfEmpty(0L);
    }

    private Map<String, Object> rowMap(Row row, RowMetadata metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        metadata.getColumnMetadatas().forEach(column ->
            result.put(column.getName(), row.get(column.getName()))
        );
        return result;
    }

    private String table(String logicalName) {
        return TableNameResolver.tableName(
            settingsService.current().tablePrefix(),
            logicalName
        );
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String text(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static Map.Entry<String, ResourceSpec> entry(
        String key,
        String logicalTable,
        String title,
        String columns,
        String whereClause,
        String orderBy
    ) {
        return Map.entry(
            key,
            new ResourceSpec(logicalTable, title, columns, whereClause, orderBy)
        );
    }

    /**
     * 单个管理资源的安全查询契约。
     */
    private record ResourceSpec(
        String logicalTable,
        String title,
        String columns,
        String whereClause,
        String orderBy
    ) {
    }
}
