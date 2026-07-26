package com.aquafish.forum.portal;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import com.aquafish.forum.section.ForumSection;
import com.aquafish.forum.section.ForumSectionRepository;
import com.aquafish.forum.section.ForumSectionVisibility;
import io.r2dbc.spi.Row;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 论坛公开页面的响应式只读装配服务。
 *
 * <p>板块沿用论坛领域仓储；主题详情和楼层使用显式列白名单读取。前台只能看到
 * 已启用公开板块、已发布且审核通过的主题与楼层，不能通过 URL 绕过状态过滤。</p>
 */
@Service
public class ForumPortalQueryService {

    private final ForumSectionRepository sectionRepository;
    private final DatabaseClient databaseClient;
    private final DatabaseRuntimeSettingsService settingsService;

    public ForumPortalQueryService(
        ForumSectionRepository sectionRepository,
        DatabaseClient databaseClient,
        DatabaseRuntimeSettingsService settingsService
    ) {
        this.sectionRepository = sectionRepository;
        this.databaseClient = databaseClient;
        this.settingsService = settingsService;
    }

    /** 返回前台可见板块，保持后台配置的树形排序。 */
    public Flux<ForumSection> publicSections() {
        return sectionRepository.findAllOrdered()
            .filter(ForumSection::enabled)
            .filter(section -> section.visibility() == ForumSectionVisibility.PUBLIC);
    }

    /** 读取一个公开板块；私有、会员或停用板块在匿名前台均表现为不存在。 */
    public Mono<ForumSection> publicSection(long sectionId) {
        if (sectionId <= 0) {
            return Mono.empty();
        }
        return sectionRepository.findById(sectionId)
            .filter(ForumSection::enabled)
            .filter(section -> section.visibility() == ForumSectionVisibility.PUBLIC);
    }

    /**
     * 读取公开主题及其可见楼层。
     */
    public Mono<ThreadView> publicThread(long threadId) {
        if (threadId <= 0) {
            return Mono.empty();
        }

        Mono<Map<String, Object>> thread = databaseClient.sql(
                "select id, section_id, author_user_id, title, status, "
                    + "pinned_level, featured_level, reply_count, view_count, "
                    + "created_at, last_reply_at from " + table(TableNames.FORUM_THREADS)
                    + " where id = :threadId and status = 'OPEN' "
                    + "and moderation_status = 'APPROVED' and deleted_at is null"
            )
            .bind("threadId", threadId)
            .map((row, metadata) -> threadMap(row))
            .one();

        Mono<List<Map<String, Object>>> posts = databaseClient.sql(
                "select id, thread_id, author_user_id, floor_number, content_text, "
                    + "quoted_post_id, edited_at, created_at from " + table(TableNames.FORUM_POSTS)
                    + " where thread_id = :threadId and status = 'PUBLISHED' "
                    + "and moderation_status = 'APPROVED' and deleted_at is null "
                    + "order by floor_number, id"
            )
            .bind("threadId", threadId)
            .map((row, metadata) -> postMap(row))
            .all()
            .collectList();

        return thread.flatMap(item -> publicSection(number(item.get("sectionId")))
            .flatMap(section -> posts.map(items -> new ThreadView(item, section, items))));
    }

    private Map<String, Object> threadMap(Row row) {
        Map<String, Object> item = new LinkedHashMap<>();
        long authorId = number(row.get("author_user_id"));
        item.put("id", number(row.get("id")));
        item.put("sectionId", number(row.get("section_id")));
        item.put("authorUserId", authorId);
        item.put("authorName", "用户 #" + authorId);
        item.put("title", text(row.get("title")));
        item.put("status", text(row.get("status")));
        item.put("pinnedLevel", number(row.get("pinned_level")));
        item.put("featuredLevel", number(row.get("featured_level")));
        item.put("replyCount", number(row.get("reply_count")));
        item.put("viewCount", number(row.get("view_count")));
        item.put("createdAt", time(row, "created_at"));
        item.put("lastReplyAt", time(row, "last_reply_at"));
        return item;
    }

    private Map<String, Object> postMap(Row row) {
        Map<String, Object> item = new LinkedHashMap<>();
        long authorId = number(row.get("author_user_id"));
        item.put("id", number(row.get("id")));
        item.put("threadId", number(row.get("thread_id")));
        item.put("authorUserId", authorId);
        item.put("authorName", "用户 #" + authorId);
        item.put("floorNumber", number(row.get("floor_number")));
        item.put("content", text(row.get("content_text")));
        item.put("quotedPostId", number(row.get("quoted_post_id")));
        item.put("editedAt", time(row, "edited_at"));
        item.put("createdAt", time(row, "created_at"));
        return item;
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Object time(Row row, String column) {
        LocalDateTime value = row.get(column, LocalDateTime.class);
        return value == null ? "" : value;
    }

    /** 前台主题页面的一次性安全快照。 */
    public record ThreadView(
        Map<String, Object> thread,
        ForumSection section,
        List<Map<String, Object>> posts
    ) {
        public ThreadView {
            thread = thread == null ? Map.of() : Map.copyOf(thread);
            posts = posts == null ? List.of() : List.copyOf(posts);
        }
    }
}
