package com.aquafish.content.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.content.article.ContentArticle;
import com.aquafish.content.article.ContentArticleCommand;
import com.aquafish.content.article.ContentArticleService;
import com.aquafish.core.admin.auth.AdminAuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * CMS 文章后台管理 API。
 *
 * <p>路径为 {@code /api/admin/content/articles}。列表需要后台登录；创建与发布
 * 还会把 Spring Security 中的 {@link AdminAuthUser} 传给领域服务，作者 ID
 * 不从请求体读取。写请求继续受后台 CSRF 保护。</p>
 */
@RestController
@RequestMapping("/api/admin/content/articles")
public class AdminContentArticleController {

    private final ContentArticleService service;

    public AdminContentArticleController(ContentArticleService service) {
        this.service = service;
    }

    /** 后台文章分页列表。 */
    @GetMapping
    public Mono<ResponseEntity<ApiResult<ContentArticleService.ArticlePage>>> list(
        @RequestParam(name = "page", required = false) Integer page,
        @RequestParam(name = "pageSize", required = false) Integer pageSize
    ) {
        return service.listForManagement(page, pageSize)
            .map(data -> ResponseEntity.ok(ApiResult.ok(data, "文章列表获取成功")))
            .onErrorResume(error -> failure(error, HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /** 创建草稿和第一条版本历史。 */
    @PostMapping
    public Mono<ResponseEntity<ApiResult<ContentArticle>>> create(
        @RequestBody ContentArticleCommand command,
        Authentication authentication
    ) {
        return Mono.defer(() -> service.createDraft(operator(authentication), command))
            .map(data -> ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.ok(data, "文章草稿创建成功")))
            .onErrorResume(error -> failure(error, HttpStatus.BAD_REQUEST));
    }

    /** 发布现有文章。 */
    @PostMapping("/{articleId}/publish")
    public Mono<ResponseEntity<ApiResult<ContentArticle>>> publish(
        @PathVariable("articleId") long articleId,
        Authentication authentication
    ) {
        return Mono.defer(() -> service.publish(operator(authentication), articleId))
            .map(data -> ResponseEntity.ok(ApiResult.ok(data, "文章发布成功")))
            .onErrorResume(error -> failure(error, HttpStatus.BAD_REQUEST));
    }

    private AdminAuthUser operator(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof AdminAuthUser user)) {
            throw new IllegalStateException("后台登录状态无效。");
        }
        return user;
    }

    private <T> Mono<ResponseEntity<ApiResult<T>>> failure(
        Throwable error,
        HttpStatus status
    ) {
        String message = error.getMessage() == null
            ? "文章请求处理失败。"
            : error.getMessage();
        return Mono.just(ResponseEntity.status(status)
            .body(ApiResult.fail("CONTENT_REQUEST_FAILED", message)));
    }
}
