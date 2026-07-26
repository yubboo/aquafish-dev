package com.aquafish.forum.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.forum.permission.ForumMemberActor;
import com.aquafish.forum.portal.ForumPortalQueryService;
import com.aquafish.forum.section.ForumSection;
import com.aquafish.forum.thread.ForumThreadPage;
import com.aquafish.forum.thread.ForumThreadQuery;
import com.aquafish.forum.thread.ForumThreadService;
import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.core.TemplateType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 论坛公开 API 与主题页面装配器。
 */
@RestController
public class PublicForumController {

    private final ForumPortalQueryService portalService;
    private final ForumThreadService threadService;
    private final DefaultTemplateRenderService templateRenderService;
    private final PublicTemplateContextService templateContextService;

    public PublicForumController(
        ForumPortalQueryService portalService,
        ForumThreadService threadService,
        DefaultTemplateRenderService templateRenderService,
        PublicTemplateContextService templateContextService
    ) {
        this.portalService = portalService;
        this.threadService = threadService;
        this.templateRenderService = templateRenderService;
        this.templateContextService = templateContextService;
    }

    /** 供前台组件使用的公开板块 API。 */
    @GetMapping("/api/forum/sections")
    public Mono<ResponseEntity<ApiResult<List<ForumSection>>>> sectionsApi() {
        return portalService.publicSections()
            .collectList()
            .map(items -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResult.ok(items, "公开论坛板块获取成功")));
    }

    /** 论坛首页。 */
    @GetMapping("/forum")
    public Mono<ResponseEntity<String>> forumIndex(ServerWebExchange exchange) {
        return portalService.publicSections()
            .map(this::sectionMap)
            .collectList()
            .flatMap(sections -> templateContextService.create(
                    exchange,
                    "论坛",
                    "Aquafish 公开论坛板块"
                )
                .flatMap(model -> {
                    model.put("forums", sections);
                    model.put("sections", sections);
                    return render(TemplateTypes.FORUM, model, HttpStatus.OK);
                }));
    }

    /** 公开板块主题列表。 */
    @GetMapping("/forum/section/{sectionId}")
    public Mono<ResponseEntity<String>> section(
        @PathVariable("sectionId") long sectionId,
        @RequestParam(name = "page", defaultValue = "1") int page,
        ServerWebExchange exchange
    ) {
        return portalService.publicSection(sectionId)
            .flatMap(section -> threadService.list(
                    ForumMemberActor.anonymous(),
                    sectionId,
                    new ForumThreadQuery(page, 20)
                )
                .flatMap(threads -> templateContextService.create(
                        exchange,
                        section.name(),
                        section.description()
                    )
                    .flatMap(model -> {
                        model.put("forum", sectionMap(section));
                        model.put("section", sectionMap(section));
                        model.put("threads", threadMaps(threads));
                        model.put("pagination", Map.of(
                            "page", threads.page(),
                            "total", threads.total(),
                            "totalPages", threads.totalPages()
                        ));
                        return render(TemplateTypes.FORUM_DETAIL, model, HttpStatus.OK);
                    })))
            .switchIfEmpty(notFound(exchange, "论坛板块不存在或暂不公开。"));
    }

    /** 公开主题与楼层详情。 */
    @GetMapping("/forum/thread/{threadId}")
    public Mono<ResponseEntity<String>> thread(
        @PathVariable("threadId") long threadId,
        ServerWebExchange exchange
    ) {
        return portalService.publicThread(threadId)
            .flatMap(view -> templateContextService.create(
                    exchange,
                    String.valueOf(view.thread().get("title")),
                    "Aquafish 论坛主题"
                )
                .flatMap(model -> {
                    model.put("forum", sectionMap(view.section()));
                    model.put("thread", view.thread());
                    model.put("posts", view.posts());
                    model.put("replies", view.posts());
                    return render(TemplateTypes.THREAD, model, HttpStatus.OK);
                }))
            .switchIfEmpty(notFound(
                exchange,
                "论坛主题不存在、未通过审核或所在板块不可见。"
            ));
    }

    private List<Map<String, Object>> threadMaps(ForumThreadPage page) {
        return page.items().stream().map(thread -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", thread.id());
            item.put("title", thread.title());
            item.put("authorName", "用户 #" + thread.authorUserId());
            item.put("replyCount", thread.replyCount());
            item.put("viewCount", thread.viewCount());
            item.put("pinnedLevel", thread.pinnedLevel());
            item.put("featuredLevel", thread.featuredLevel());
            item.put("lastActivityAt", thread.lastActivityAt());
            item.put("url", "/forum/thread/" + thread.id());
            return item;
        }).toList();
    }

    private Map<String, Object> sectionMap(ForumSection section) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", section.id());
        item.put("parentId", section.parentId() == null ? 0L : section.parentId());
        item.put("key", section.sectionKey());
        item.put("name", section.name());
        item.put("description", section.description());
        item.put("icon", section.icon());
        item.put("threadCount", section.threadCount());
        item.put("postCount", section.postCount());
        item.put("url", "/forum/section/" + section.id());
        return item;
    }

    private Mono<ResponseEntity<String>> notFound(
        ServerWebExchange exchange,
        String message
    ) {
        return templateContextService.create(exchange, "页面不存在", message)
            .flatMap(model -> {
                model.put("errorTitle", "页面不存在");
                model.put("errorMessage", message);
                return render(TemplateTypes.ERROR, model, HttpStatus.NOT_FOUND);
            });
    }

    private Mono<ResponseEntity<String>> render(
        TemplateType type,
        Map<String, Object> model,
        HttpStatus status
    ) {
        return Mono.fromCallable(() ->
                templateRenderService.render(TemplateRenderRequest.of(type, model))
            )
            .subscribeOn(Schedulers.boundedElastic())
            .map(result -> response(result, status));
    }

    private ResponseEntity<String> response(TemplateRenderResult result, HttpStatus status) {
        String body = result.success()
            ? result.html()
            : "<!doctype html><html lang=\"zh-CN\"><meta charset=\"UTF-8\">"
                + "<title>Aquafish 论坛</title><body><main><h1>论坛暂时无法渲染</h1>"
                + "<p>请稍后重试。</p><a href=\"/\">返回首页</a></main></body></html>";
        return ResponseEntity.status(result.success() ? status : HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
            .cacheControl(CacheControl.noStore())
            .body(body);
    }
}
