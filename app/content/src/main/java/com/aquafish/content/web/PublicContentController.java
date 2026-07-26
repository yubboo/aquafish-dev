package com.aquafish.content.web;

import com.aquafish.content.article.ContentArticle;
import com.aquafish.content.article.ContentArticleService;
import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.core.TemplateTypes;
import com.aquafish.template.engine.DefaultTemplateRenderService;
import com.aquafish.user.web.PublicTemplateContextService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Aquafish 前台首页、文章列表和文章详情页面装配器。
 *
 * <p>数据库查询保持 R2DBC；主题文件读取与 Thymeleaf 渲染属于文件/CPU 工作，
 * 因此在 boundedElastic 调度器执行。页面只向模板传入安全 Map ViewModel，
 * 不把 Service、Repository 或数据库实体暴露给主题。</p>
 */
@RestController
public class PublicContentController {

    private final ContentArticleService articleService;
    private final DefaultTemplateRenderService templateRenderService;
    private final PublicTemplateContextService templateContextService;

    public PublicContentController(
        ContentArticleService articleService,
        DefaultTemplateRenderService templateRenderService,
        PublicTemplateContextService templateContextService
    ) {
        this.articleService = articleService;
        this.templateRenderService = templateRenderService;
        this.templateContextService = templateContextService;
    }

    /** 正式站点首页；展示最近文章和论坛入口。 */
    @GetMapping({"/", "/site"})
    public Mono<ResponseEntity<String>> home(ServerWebExchange exchange) {
        return articleService.listPublished(8)
            .map(this::articleListItem)
            .collectList()
            .flatMap(articles -> templateContextService.create(
                    exchange,
                    "Aquafish",
                    "Aquafish 内容社区首页"
                )
                .flatMap(model -> render(
                    TemplateTypes.INDEX,
                    withArticles(model, articles),
                    HttpStatus.OK
                )));
    }

    /** CMS 公开文章列表。 */
    @GetMapping("/content")
    public Mono<ResponseEntity<String>> contentIndex(ServerWebExchange exchange) {
        return articleService.listPublished(30)
            .map(this::articleListItem)
            .collectList()
            .flatMap(articles -> templateContextService.create(
                    exchange,
                    "内容中心",
                    "Aquafish 已发布的 CMS 内容"
                )
                .flatMap(model -> {
                    withArticles(model, articles);
                    model.put("category", Map.of(
                        "name", "最新文章",
                        "description", "Aquafish 已发布的 CMS 内容。"
                    ));
                    return render(TemplateTypes.CATEGORY, model, HttpStatus.OK);
                }));
    }

    /** 按 slug 渲染公开文章；不存在时进入主题错误页/核心 fallback。 */
    @GetMapping("/content/{slug}")
    public Mono<ResponseEntity<String>> article(
        @PathVariable("slug") String slug,
        ServerWebExchange exchange
    ) {
        return articleService.findPublishedBySlug(slug)
            .flatMap(article -> templateContextService.create(
                    exchange,
                    article.title(),
                    article.excerpt()
                )
                .flatMap(model -> {
                    withArticles(model, List.of());
                    model.put("article", articleDetail(article));
                    return render(TemplateTypes.POST, model, HttpStatus.OK);
                }))
            .switchIfEmpty(Mono.defer(() -> templateContextService.create(
                    exchange,
                    "文章不存在",
                    "这篇文章可能尚未发布、已归档或地址输入有误。"
                )
                .flatMap(model -> {
                    withArticles(model, List.of());
                    model.put("errorTitle", "文章不存在");
                    model.put("errorMessage", "这篇文章可能尚未发布、已归档或地址输入有误。");
                    return render(TemplateTypes.ERROR, model, HttpStatus.NOT_FOUND);
                })));
    }

    private Mono<ResponseEntity<String>> render(
        com.aquafish.template.core.TemplateType type,
        Map<String, Object> model,
        HttpStatus status
    ) {
        return Mono.fromCallable(() ->
                templateRenderService.render(TemplateRenderRequest.of(type, model))
            )
            .subscribeOn(Schedulers.boundedElastic())
            .map(result -> htmlResponse(result, status));
    }

    private ResponseEntity<String> htmlResponse(
        TemplateRenderResult result,
        HttpStatus status
    ) {
        String html = result.success()
            ? result.html()
            : emergencyHtml();
        return ResponseEntity.status(result.success() ? status : HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
            .cacheControl(CacheControl.noStore())
            .body(html);
    }

    /**
     * BEGIN：内容模块主题变量
     *
     * <p>公共 site、seo、viewer 和 navigation 已由用户模块统一装配；
     * 本方法只补充内容领域的文章集合和旧模板兼容别名。</p>
     */
    private Map<String, Object> withArticles(
        Map<String, Object> model,
        List<Map<String, Object>> articles
    ) {
        model.put("articles", articles);
        model.put("posts", articles);
        return model;
    }
    /* END：内容模块主题变量 */

    private Map<String, Object> articleListItem(ContentArticle article) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", article.id());
        item.put("slug", article.slug());
        item.put("title", article.title());
        item.put("summary", article.excerpt());
        item.put("url", "/content/" + article.slug());
        item.put("authorName", "用户 #" + article.authorUserId());
        item.put("publishedAt", article.publishedAt());
        return item;
    }

    private Map<String, Object> articleDetail(ContentArticle article) {
        Map<String, Object> item = articleListItem(article);
        item.put("content", article.contentText());
        item.put("viewCount", article.viewCount());
        item.put("commentCount", article.commentCount());
        item.put("updatedAt", article.updatedAt());
        return item;
    }

    /** 模板链发生不可恢复错误时仍返回不含内部信息的最小 HTML。 */
    private String emergencyHtml() {
        return "<!doctype html><html lang=\"zh-CN\"><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>Aquafish</title><body><main><h1>Aquafish 暂时无法渲染页面</h1>"
            + "<p>请稍后重试或联系站点管理员。</p><a href=\"/\">返回首页</a></main></body></html>";
    }
}
