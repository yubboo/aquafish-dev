package com.aquafish.forum.section.database;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.forum.database.ForumTableNames;
import com.aquafish.forum.section.ForumSection;
import com.aquafish.forum.section.ForumSectionCommand;
import com.aquafish.forum.section.ForumSectionModerationPolicy;
import com.aquafish.forum.section.ForumSectionPostingPolicy;
import com.aquafish.forum.section.ForumSectionRepository;
import com.aquafish.forum.section.ForumSectionVisibility;
import io.r2dbc.spi.Row;
import java.time.LocalDateTime;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 基于 Spring DatabaseClient 的论坛板块存储实现。
 *
 * <p>全部在线读写均使用 R2DBC，不使用 JDBC，也不调用 block()。
 * 表名通过当前安装配置与 TableNameResolver 生成，
 * 因此可正确跟随用户选定的唯一数据库和自定义表前缀。</p>
 *
 * <p>本类只保存跨 MySQL、MariaDB 和 PostgreSQL 一致的参数化 DML。
 * 数据库结构差异已集中在各自迁移目录，不在业务方法中到处判断数据库类型。</p>
 *
 * <p>该类故意不声明为 final。Spring 会为 @Repository 创建异常转换代理，
 * 默认 CGLIB 代理需要生成子类，final 会导致应用启动失败。</p>
 */
@Repository
public class R2dbcForumSectionRepository implements ForumSectionRepository {

    private static final String SELECT_COLUMNS = "id, parent_id, section_key, name, "
        + "description, icon, sort_order, visibility, posting_policy, moderation_policy, "
        + "thread_count, post_count, enabled, created_by, updated_by, created_at, updated_at";

    private final DatabaseClient databaseClient;
    private final DatabaseRuntimeSettingsService databaseSettings;

    public R2dbcForumSectionRepository(
        DatabaseClient databaseClient,
        DatabaseRuntimeSettingsService databaseSettings
    ) {
        this.databaseClient = databaseClient;
        this.databaseSettings = databaseSettings;
    }

    /**
     * 一次读取板块快照，树形组装由上层展示服务负责。
     */
    @Override
    public Flux<ForumSection> findAllOrdered() {
        String sql = "select " + SELECT_COLUMNS + " from " + sectionsTable()
            + " order by coalesce(parent_id, 0), sort_order, id";

        return databaseClient.sql(sql)
            .map((row, metadata) -> mapSection(row))
            .all();
    }

    @Override
    public Mono<ForumSection> findById(long sectionId) {
        String sql = "select " + SELECT_COLUMNS + " from " + sectionsTable()
            + " where id = :sectionId";

        return databaseClient.sql(sql)
            .bind("sectionId", sectionId)
            .map((row, metadata) -> mapSection(row))
            .one();
    }

    @Override
    public Mono<Boolean> existsBySectionKey(String sectionKey, Long excludedSectionId) {
        String exclusion = excludedSectionId == null ? "" : " and id <> :excludedSectionId";
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(
            "select count(*) as row_count from " + sectionsTable()
                + " where section_key = :sectionKey" + exclusion
        ).bind("sectionKey", sectionKey);

        if (excludedSectionId != null) {
            spec = spec.bind("excludedSectionId", excludedSectionId);
        }

        return spec.map((row, metadata) -> number(row, "row_count") > 0).one();
    }

    @Override
    public Mono<Boolean> existsChild(long parentSectionId) {
        return databaseClient.sql(
                "select count(*) as row_count from " + sectionsTable()
                    + " where parent_id = :parentSectionId"
            )
            .bind("parentSectionId", parentSectionId)
            .map((row, metadata) -> number(row, "row_count") > 0)
            .one();
    }

    /**
     * 插入后使用全局唯一的 section_key 回读数据。
     *
     * <p>这种做法避免在业务代码中分别编写 MySQL 自增 ID 和
     * PostgreSQL RETURNING 语法，同时仍由数据库唯一索引保证并发安全。</p>
     */
    @Override
    public Mono<ForumSection> insert(ForumSectionCommand command, long operatorUserId) {
        String sql = "insert into " + sectionsTable() + " (parent_id, section_key, name, "
            + "description, icon, sort_order, visibility, posting_policy, moderation_policy, "
            + "enabled, created_by, updated_by) values (:parentId, :sectionKey, :name, "
            + ":description, :icon, :sortOrder, :visibility, :postingPolicy, :moderationPolicy, "
            + ":enabled, :operatorUserId, :operatorUserId)";

        return bindCommand(databaseClient.sql(sql), command, operatorUserId)
            .fetch()
            .rowsUpdated()
            .flatMap(rows -> requireChanged(rows, "创建论坛板块失败。"))
            .then(findBySectionKey(command.sectionKey()));
    }

    @Override
    public Mono<ForumSection> update(
        long sectionId,
        ForumSectionCommand command,
        long operatorUserId
    ) {
        String sql = "update " + sectionsTable() + " set parent_id = :parentId, "
            + "section_key = :sectionKey, name = :name, description = :description, icon = :icon, "
            + "sort_order = :sortOrder, visibility = :visibility, posting_policy = :postingPolicy, "
            + "moderation_policy = :moderationPolicy, enabled = :enabled, "
            + "updated_by = :operatorUserId, updated_at = current_timestamp where id = :sectionId";

        return bindCommand(databaseClient.sql(sql), command, operatorUserId)
            .bind("sectionId", sectionId)
            .fetch()
            .rowsUpdated()
            .flatMap(rows -> requireChanged(rows, "要修改的论坛板块不存在。"))
            .then(findById(sectionId));
    }

    @Override
    public Mono<ForumSection> updateEnabled(
        long sectionId,
        boolean enabled,
        long operatorUserId
    ) {
        return databaseClient.sql(
                "update " + sectionsTable() + " set enabled = :enabled, "
                    + "updated_by = :operatorUserId, updated_at = current_timestamp "
                    + "where id = :sectionId"
            )
            .bind("enabled", enabled ? 1 : 0)
            .bind("operatorUserId", operatorUserId)
            .bind("sectionId", sectionId)
            .fetch()
            .rowsUpdated()
            .flatMap(rows -> requireChanged(rows, "要启停的论坛板块不存在。"))
            .then(findById(sectionId));
    }

    /** 使用唯一板块标识回读新创建记录。 */
    private Mono<ForumSection> findBySectionKey(String sectionKey) {
        return databaseClient.sql(
                "select " + SELECT_COLUMNS + " from " + sectionsTable()
                    + " where section_key = :sectionKey"
            )
            .bind("sectionKey", sectionKey)
            .map((row, metadata) -> mapSection(row))
            .one()
            .switchIfEmpty(Mono.error(new IllegalStateException("创建后未找到论坛板块。")));
    }

    /**
     * 统一绑定创建和修改共用的板块字段。
     * 父板块为空时必须显式 bindNull，否则 R2DBC 无法推断 null 类型。
     */
    private DatabaseClient.GenericExecuteSpec bindCommand(
        DatabaseClient.GenericExecuteSpec source,
        ForumSectionCommand command,
        long operatorUserId
    ) {
        DatabaseClient.GenericExecuteSpec spec = command.parentId() == null
            ? source.bindNull("parentId", Long.class)
            : source.bind("parentId", command.parentId());

        return spec
            .bind("sectionKey", command.sectionKey())
            .bind("name", command.name())
            .bind("description", command.description())
            .bind("icon", command.icon())
            .bind("sortOrder", command.sortOrder())
            .bind("visibility", command.visibility().name())
            .bind("postingPolicy", command.postingPolicy().name())
            .bind("moderationPolicy", command.moderationPolicy().name())
            .bind("enabled", command.enabled() ? 1 : 0)
            .bind("operatorUserId", operatorUserId);
    }

    /** 将不同 R2DBC 驱动返回的 Number 统一转换为 long。 */
    private long number(Row row, String column) {
        Number value = row.get(column, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private Mono<Long> requireChanged(Long rows, String message) {
        if (rows == null || rows <= 0) {
            return Mono.error(new IllegalStateException(message));
        }
        return Mono.just(rows);
    }

    /** 将数据库行映射为不可变板块领域快照。 */
    private ForumSection mapSection(Row row) {
        Number parent = row.get("parent_id", Number.class);
        return new ForumSection(
            number(row, "id"),
            parent == null ? null : parent.longValue(),
            text(row, "section_key"),
            text(row, "name"),
            text(row, "description"),
            text(row, "icon"),
            (int) number(row, "sort_order"),
            enumValue(ForumSectionVisibility.class, text(row, "visibility"), "板块可见策略"),
            enumValue(ForumSectionPostingPolicy.class, text(row, "posting_policy"), "板块发布策略"),
            enumValue(ForumSectionModerationPolicy.class, text(row, "moderation_policy"), "板块审核策略"),
            number(row, "thread_count"),
            number(row, "post_count"),
            number(row, "enabled") == 1,
            number(row, "created_by"),
            number(row, "updated_by"),
            row.get("created_at", LocalDateTime.class),
            row.get("updated_at", LocalDateTime.class)
        );
    }

    /** 数据库中的未知状态必须显式失败，不能静默降级成默认策略。 */
    private <E extends Enum<E>> E enumValue(Class<E> type, String value, String fieldName) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException error) {
            throw new IllegalStateException(fieldName + "存在未知值：" + value, error);
        }
    }

    private String text(Row row, String column) {
        String value = row.get(column, String.class);
        return value == null ? "" : value;
    }

    /** 每次从当前运行配置读取前缀，不缓存某一套数据库表名。 */
    private String sectionsTable() {
        return TableNameResolver.tableName(
            databaseSettings.current().tablePrefix(),
            ForumTableNames.SECTIONS
        );
    }
}
