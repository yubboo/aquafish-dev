package com.aquafish.forum.thread.database;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.R2dbcPaginationSql;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.forum.database.ForumTableNames;
import com.aquafish.forum.thread.ForumModerationStatus;
import com.aquafish.forum.thread.ForumThreadCreatedEvent;
import com.aquafish.forum.thread.ForumThreadQuery;
import com.aquafish.forum.thread.ForumThreadRepository;
import com.aquafish.forum.thread.ForumThreadStatus;
import com.aquafish.forum.thread.ForumThreadSummary;
import io.r2dbc.spi.Row;
import java.time.LocalDateTime;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 论坛主题的 R2DBC 存储实现。
 *
 * <p>全部表名由当前安装配置和 {@link TableNameResolver} 动态生成，
 * 不写死 aq_。SQL 使用 MySQL、MariaDB 和 PostgreSQL 共同支持的
 * 参数绑定、LIMIT/OFFSET 和 COALESCE 语法。</p>
 *
 * <p>新增主题和第一楼通过 R2DBC Statement.returnGeneratedValues("id")
 * 读取数据库自增/identity 主键，避免使用 MySQL LAST_INSERT_ID()
 * 或 PostgreSQL RETURNING 的方言分支。</p>
 *
 * <p>该类不声明为 final，以便 Spring 为 {@code @Repository}
 * 创建异常转换代理。</p>
 */
@Repository
public class R2dbcForumThreadRepository implements ForumThreadRepository {

    private static final String LIST_COLUMNS = "id, section_id, author_user_id, title, "
        + "status, moderation_status, pinned_level, featured_level, reply_count, view_count, "
        + "first_post_id, last_post_id, last_reply_user_id, last_reply_at, created_at, "
        + "coalesce(last_reply_at, created_at) as last_activity_at";

    private final DatabaseClient databaseClient;
    private final DatabaseRuntimeSettingsService databaseSettings;

    public R2dbcForumThreadRepository(
        DatabaseClient databaseClient,
        DatabaseRuntimeSettingsService databaseSettings
    ) {
        this.databaseClient = databaseClient;
        this.databaseSettings = databaseSettings;
    }

    @Override
    public Mono<Boolean> existsApprovedPostByAuthorInSection(
        long sectionId,
        long authorUserId
    ) {
        String sql = "select count(*) as row_count from " + postsTable()
            + " where section_id = :sectionId and author_user_id = :authorUserId "
            + "and status <> 'DELETED' and moderation_status = 'APPROVED'";

        return databaseClient.sql(sql)
            .bind("sectionId", sectionId)
            .bind("authorUserId", authorUserId)
            .map((row, metadata) -> number(row, "row_count") > 0L)
            .one()
            .defaultIfEmpty(false);
    }

    @Override
    public Mono<Long> insertThread(
        long sectionId,
        long authorUserId,
        String title,
        ForumModerationStatus moderationStatus
    ) {
        String sql = "insert into " + threadsTable()
            + " (section_id, author_user_id, title, status, moderation_status) "
            + "values (:sectionId, :authorUserId, :title, :status, :moderationStatus)";

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
            .bind("sectionId", sectionId)
            .bind("authorUserId", authorUserId)
            .bind("title", title)
            .bind("status", ForumThreadStatus.OPEN.name())
            .bind("moderationStatus", moderationStatus.name());
        return generatedId(spec, "创建论坛主题后没有返回主键。");
    }

    @Override
    public Mono<Long> insertFirstPost(
        long threadId,
        long sectionId,
        long authorUserId,
        String contentText,
        ForumModerationStatus moderationStatus
    ) {
        String sql = "insert into " + postsTable()
            + " (thread_id, section_id, author_user_id, floor_number, content_text, "
            + "status, moderation_status) values (:threadId, :sectionId, :authorUserId, "
            + ":floorNumber, :contentText, :status, :moderationStatus)";

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
            .bind("threadId", threadId)
            .bind("sectionId", sectionId)
            .bind("authorUserId", authorUserId)
            .bind("floorNumber", 1L)
            .bind("contentText", contentText)
            .bind("status", "PUBLISHED")
            .bind("moderationStatus", moderationStatus.name());
        return generatedId(spec, "创建论坛主题第一楼后没有返回主键。");
    }

    @Override
    public Mono<Void> completeThreadCreation(long threadId, long firstPostId) {
        String sql = "update " + threadsTable()
            + " set first_post_id = :firstPostId, last_post_id = :firstPostId, "
            + "updated_at = current_timestamp where id = :threadId "
            + "and first_post_id is null";

        return databaseClient.sql(sql)
            .bind("firstPostId", firstPostId)
            .bind("threadId", threadId)
            .fetch()
            .rowsUpdated()
            .flatMap(rows -> requireChanged(rows, "回填论坛主题第一楼失败。"))
            .then();
    }

    @Override
    public Mono<Void> incrementVisibleSectionStatistics(long sectionId) {
        String sql = "update " + sectionsTable()
            + " set thread_count = thread_count + 1, post_count = post_count + 1, "
            + "updated_at = current_timestamp where id = :sectionId and enabled = 1";

        return databaseClient.sql(sql)
            .bind("sectionId", sectionId)
            .fetch()
            .rowsUpdated()
            .flatMap(rows -> requireChanged(rows, "更新论坛板块可见内容统计失败。"))
            .then();
    }

    @Override
    public Mono<Void> appendCreationEvent(ForumThreadCreatedEvent event) {
        String sql = "insert into " + outboxTable()
            + " (event_key, event_type, aggregate_type, aggregate_id, payload) "
            + "values (:eventKey, :eventType, :aggregateType, :aggregateId, :payload)";

        return databaseClient.sql(sql)
            .bind("eventKey", event.eventKey())
            .bind("eventType", ForumThreadCreatedEvent.EVENT_TYPE)
            .bind("aggregateType", ForumThreadCreatedEvent.AGGREGATE_TYPE)
            .bind("aggregateId", event.threadId())
            .bind("payload", eventPayload(event))
            .fetch()
            .rowsUpdated()
            .flatMap(rows -> requireChanged(rows, "写入论坛主题事务发件箱失败。"))
            .then();
    }

    @Override
    public Flux<ForumThreadSummary> findVisibleBySection(
        long sectionId,
        ForumThreadQuery query
    ) {
        String sql = R2dbcPaginationSql.limitOffset(
            "select " + LIST_COLUMNS + " from " + threadsTable()
            + " where section_id = :sectionId and status in ('OPEN', 'CLOSED') "
            + "and moderation_status = 'APPROVED' and deleted_at is null "
            + "order by pinned_level desc, "
            + "coalesce(last_reply_at, created_at) desc, id desc",
            query.size(),
            query.offset()
        );

        return databaseClient.sql(sql)
            .bind("sectionId", sectionId)
            .map((row, metadata) -> mapSummary(row))
            .all();
    }

    @Override
    public Mono<Long> countVisibleBySection(long sectionId) {
        String sql = "select count(*) as row_count from " + threadsTable()
            + " where section_id = :sectionId and status in ('OPEN', 'CLOSED') "
            + "and moderation_status = 'APPROVED' and deleted_at is null";

        return databaseClient.sql(sql)
            .bind("sectionId", sectionId)
            .map((row, metadata) -> number(row, "row_count"))
            .one()
            .defaultIfEmpty(0L);
    }

    /**
     * 使用 R2DBC 标准生成值能力读取 MySQL 自增或 PostgreSQL identity 主键。
     */
    private Mono<Long> generatedId(
        DatabaseClient.GenericExecuteSpec spec,
        String missingMessage
    ) {
        return spec
            .filter(statement -> statement.returnGeneratedValues("id"))
            .map((row, metadata) -> {
                Number value = row.get(0, Number.class);
                if (value == null || value.longValue() <= 0L) {
                    throw new IllegalStateException(missingMessage);
                }
                return value.longValue();
            })
            .one()
            .switchIfEmpty(Mono.error(new IllegalStateException(missingMessage)));
    }

    /**
     * 事件负载只包含正整数和受控枚举值，不接收任意外部文本。
     *
     * <p>forum 模块因此无需为了一个固定小事件引入 JSON databind 依赖；
     * 枚举名称只可能包含 Java 标识符字符，不存在引号注入风险。</p>
     */
    private String eventPayload(ForumThreadCreatedEvent event) {
        return "{\"eventVersion\":1"
            + ",\"threadId\":" + event.threadId()
            + ",\"sectionId\":" + event.sectionId()
            + ",\"authorUserId\":" + event.authorUserId()
            + ",\"moderationStatus\":\"" + event.moderationStatus().name() + "\"}";
    }

    private ForumThreadSummary mapSummary(Row row) {
        return new ForumThreadSummary(
            number(row, "id"),
            number(row, "section_id"),
            number(row, "author_user_id"),
            text(row, "title"),
            enumValue(ForumThreadStatus.class, text(row, "status"), "论坛主题状态"),
            enumValue(
                ForumModerationStatus.class,
                text(row, "moderation_status"),
                "论坛主题审核状态"
            ),
            (int) number(row, "pinned_level"),
            (int) number(row, "featured_level"),
            number(row, "reply_count"),
            number(row, "view_count"),
            number(row, "first_post_id"),
            number(row, "last_post_id"),
            nullableNumber(row, "last_reply_user_id"),
            row.get("last_reply_at", LocalDateTime.class),
            row.get("created_at", LocalDateTime.class),
            row.get("last_activity_at", LocalDateTime.class)
        );
    }

    private Mono<Long> requireChanged(Long rows, String message) {
        if (rows == null || rows <= 0L) {
            return Mono.error(new IllegalStateException(message));
        }
        return Mono.just(rows);
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

    private <E extends Enum<E>> E enumValue(
        Class<E> type,
        String value,
        String fieldName
    ) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException error) {
            throw new IllegalStateException(fieldName + "存在未知值：" + value, error);
        }
    }

    private String sectionsTable() {
        return table(ForumTableNames.SECTIONS);
    }

    private String threadsTable() {
        return table(ForumTableNames.THREADS);
    }

    private String postsTable() {
        return table(ForumTableNames.POSTS);
    }

    private String outboxTable() {
        return table(ForumTableNames.NOTIFICATION_OUTBOX);
    }

    /** 每次读取当前运行配置，不能缓存安装前缀。 */
    private String table(String logicalName) {
        return TableNameResolver.tableName(
            databaseSettings.current().tablePrefix(),
            logicalName
        );
    }
}
