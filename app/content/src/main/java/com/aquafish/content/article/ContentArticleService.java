package com.aquafish.content.article;

import com.aquafish.core.admin.auth.AdminAuthUser;
import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.R2dbcPaginationSql;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import io.r2dbc.spi.Row;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * CMS 文章初版用例服务。
 *
 * <p>当前闭环包含后台分页、创建草稿、发布文章，以及前台公开列表和详情。
 * 创建事务同时写入文章和不可覆盖的第一版 revision，任一步失败都会回滚。
 * 发布只允许后台管理员执行，浏览器不能提交作者 ID 或绕过 DRAFT 状态。</p>
 */
@Service
public class ContentArticleService {

    private static final String ARTICLE_COLUMNS = "id, public_id, author_user_id, title, "
        + "slug, excerpt, content_text, status, visibility, view_count, comment_count, "
        + "published_at, created_at, updated_at";

    private final DatabaseClient databaseClient;
    private final DatabaseRuntimeSettingsService settingsService;
    private final TransactionalOperator transactionalOperator;

    public ContentArticleService(
        DatabaseClient databaseClient,
        DatabaseRuntimeSettingsService settingsService,
        TransactionalOperator transactionalOperator
    ) {
        this.databaseClient = databaseClient;
        this.settingsService = settingsService;
        this.transactionalOperator = transactionalOperator;
    }

    /** 后台按更新时间倒序读取文章，页大小上限为 100。 */
    public Mono<ArticlePage> listForManagement(Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        int offset = (safePage - 1) * safeSize;

        Mono<Long> total = databaseClient.sql(
                "select count(*) as row_count from " + articlesTable()
                    + " where deleted_at is null"
            )
            .map((row, metadata) -> number(row, "row_count"))
            .one()
            .defaultIfEmpty(0L);
        Mono<List<ContentArticle>> items = databaseClient.sql(
                R2dbcPaginationSql.limitOffset(
                "select " + ARTICLE_COLUMNS + " from " + articlesTable()
                    + " where deleted_at is null order by updated_at desc, id desc",
                safeSize,
                offset
                )
            )
            .map((row, metadata) -> mapArticle(row))
            .all()
            .collectList();

        return Mono.zip(total, items)
            .map(result -> new ArticlePage(
                safePage,
                safeSize,
                result.getT1(),
                (result.getT1() + safeSize - 1L) / safeSize,
                result.getT2()
            ));
    }

    /** 前台只返回公开且已发布的文章。 */
    public Flux<ContentArticle> listPublished(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return databaseClient.sql(
                R2dbcPaginationSql.limit(
                "select " + ARTICLE_COLUMNS + " from " + articlesTable()
                    + " where status = 'PUBLISHED' and visibility = 'PUBLIC' "
                    + "and deleted_at is null and published_at is not null "
                    + "order by published_at desc, id desc",
                safeLimit
                )
            )
            .map((row, metadata) -> mapArticle(row))
            .all();
    }

    /** 按不可变 slug 读取一个公开文章。 */
    public Mono<ContentArticle> findPublishedBySlug(String slug) {
        String safeSlug = slug == null ? "" : slug.trim().toLowerCase();
        return databaseClient.sql(
                "select " + ARTICLE_COLUMNS + " from " + articlesTable()
                    + " where slug = :slug and status = 'PUBLISHED' "
                    + "and visibility = 'PUBLIC' and deleted_at is null"
            )
            .bind("slug", safeSlug)
            .map((row, metadata) -> mapArticle(row))
            .one();
    }

    /**
     * 创建草稿并写入第一条版本快照。
     */
    public Mono<ContentArticle> createDraft(
        AdminAuthUser operator,
        ContentArticleCommand source
    ) {
        requireAdmin(operator);
        ContentArticleCommand command = requireCommand(source);
        String publicId = UUID.randomUUID().toString();

        Mono<ContentArticle> work = slugExists(command.slug())
            .flatMap(exists -> exists
                ? Mono.error(new IllegalStateException("文章别名已存在：" + command.slug()))
                : insertArticle(publicId, operator.id(), command)
            )
            .flatMap(article -> insertRevision(article, operator.id())
                .thenReturn(article));
        return transactionalOperator.transactional(work);
    }

    /** 把草稿或审核中文章发布到前台。重复发布保持幂等。 */
    public Mono<ContentArticle> publish(AdminAuthUser operator, long articleId) {
        requireAdmin(operator);
        if (articleId <= 0) {
            return Mono.error(new IllegalStateException("文章 ID 必须大于 0。"));
        }

        Mono<ContentArticle> work = databaseClient.sql(
                "update " + articlesTable()
                    + " set status = 'PUBLISHED', visibility = 'PUBLIC', "
                    + "published_at = coalesce(published_at, current_timestamp), "
                    + "updated_at = current_timestamp where id = :articleId "
                    + "and deleted_at is null and status in ('DRAFT', 'REVIEW', 'PUBLISHED')"
            )
            .bind("articleId", articleId)
            .fetch()
            .rowsUpdated()
            .flatMap(rows -> rows == null || rows <= 0
                ? Mono.error(new IllegalStateException("文章不存在或当前状态不能发布。"))
                : findById(articleId)
            );
        return transactionalOperator.transactional(work);
    }

    private Mono<Boolean> slugExists(String slug) {
        return databaseClient.sql(
                "select count(*) as row_count from " + articlesTable()
                    + " where slug = :slug"
            )
            .bind("slug", slug)
            .map((row, metadata) -> number(row, "row_count") > 0L)
            .one()
            .defaultIfEmpty(false);
    }

    private Mono<ContentArticle> insertArticle(
        String publicId,
        long authorId,
        ContentArticleCommand command
    ) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(
                "insert into " + articlesTable()
                    + " (public_id, author_user_id, title, slug, excerpt, content_text, "
                    + "status, visibility) values (:publicId, :authorId, :title, :slug, "
                    + ":excerpt, :contentText, 'DRAFT', 'PUBLIC')"
            )
            .bind("publicId", publicId)
            .bind("authorId", authorId)
            .bind("title", command.title())
            .bind("slug", command.slug())
            .bind("excerpt", command.excerpt())
            .bind("contentText", command.contentText());

        return spec.filter(statement -> statement.returnGeneratedValues("id"))
            .map((row, metadata) -> number(row, 0))
            .one()
            .flatMap(this::findById)
            .switchIfEmpty(Mono.error(new IllegalStateException("创建文章后没有返回主键。")));
    }

    private Mono<Void> insertRevision(ContentArticle article, long editorId) {
        return databaseClient.sql(
                "insert into " + revisionsTable()
                    + " (target_type, target_id, revision_number, editor_user_id, title, "
                    + "content_text, change_summary) values ('ARTICLE', :targetId, 1, "
                    + ":editorId, :title, :contentText, :summary)"
            )
            .bind("targetId", article.id())
            .bind("editorId", editorId)
            .bind("title", article.title())
            .bind("contentText", article.contentText())
            .bind("summary", "创建文章初稿")
            .fetch()
            .rowsUpdated()
            .flatMap(rows -> rows == null || rows <= 0
                ? Mono.error(new IllegalStateException("写入文章版本历史失败。"))
                : Mono.empty()
            );
    }

    private Mono<ContentArticle> findById(long id) {
        return databaseClient.sql(
                "select " + ARTICLE_COLUMNS + " from " + articlesTable()
                    + " where id = :id and deleted_at is null"
            )
            .bind("id", id)
            .map((row, metadata) -> mapArticle(row))
            .one();
    }

    private ContentArticle mapArticle(Row row) {
        return new ContentArticle(
            number(row, "id"),
            text(row, "public_id"),
            number(row, "author_user_id"),
            text(row, "title"),
            text(row, "slug"),
            text(row, "excerpt"),
            text(row, "content_text"),
            text(row, "status"),
            text(row, "visibility"),
            number(row, "view_count"),
            number(row, "comment_count"),
            row.get("published_at", LocalDateTime.class),
            row.get("created_at", LocalDateTime.class),
            row.get("updated_at", LocalDateTime.class)
        );
    }

    private ContentArticleCommand requireCommand(ContentArticleCommand command) {
        if (command == null) {
            throw new IllegalStateException("文章内容不能为空。");
        }
        return command.normalized();
    }

    private void requireAdmin(AdminAuthUser operator) {
        if (operator == null || !operator.hasAdminAccess()) {
            throw new IllegalStateException("当前账号没有内容管理权限。");
        }
    }

    private long number(Row row, String column) {
        Number value = row.get(column, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private long number(Row row, int index) {
        Number value = row.get(index, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private String text(Row row, String column) {
        String value = row.get(column, String.class);
        return value == null ? "" : value;
    }

    private String articlesTable() {
        return table(TableNames.CONTENT_ARTICLES);
    }

    private String revisionsTable() {
        return table(TableNames.CONTENT_REVISIONS);
    }

    private String table(String logicalName) {
        return TableNameResolver.tableName(
            settingsService.current().tablePrefix(),
            logicalName
        );
    }

    /**
     * 后台文章分页结果。
     */
    public record ArticlePage(
        int page,
        int pageSize,
        long total,
        long totalPages,
        List<ContentArticle> items
    ) {
    }
}
