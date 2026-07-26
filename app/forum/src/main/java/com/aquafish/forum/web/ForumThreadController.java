package com.aquafish.forum.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.forum.permission.ForumMemberActor;
import com.aquafish.forum.permission.ForumMemberActorFactory;
import com.aquafish.forum.thread.ForumThreadCreateCommand;
import com.aquafish.forum.thread.ForumThreadCreationResult;
import com.aquafish.forum.thread.ForumThreadPage;
import com.aquafish.forum.thread.ForumThreadQuery;
import com.aquafish.forum.thread.ForumThreadService;
import com.aquafish.user.auth.MemberAuthUser;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 论坛主题分页与发布 API。
 *
 * <p>请求体故意不定义 userId、权限或封禁字段。作者身份只从 Spring Security
 * 中已经验证的 {@link MemberAuthUser} 取得，防止前端冒充其他用户发帖。</p>
 */
@RestController
public class ForumThreadController {

    private final ForumThreadService threadService;
    private final ForumMemberActorFactory actorFactory;

    public ForumThreadController(
        ForumThreadService threadService,
        ForumMemberActorFactory actorFactory
    ) {
        this.threadService = threadService;
        this.actorFactory = actorFactory;
    }

    @GetMapping("/api/forum/sections/{sectionId}/threads")
    public Mono<ResponseEntity<ApiResult<ForumThreadPage>>> list(
        @PathVariable("sectionId") long sectionId,
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "size", defaultValue = "20") int size,
        Authentication authentication
    ) {
        ForumMemberActor viewer = actor(authentication);
        return threadService.list(
                viewer,
                sectionId,
                new ForumThreadQuery(page, size)
            )
            .map(result -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResult.ok(result, "论坛主题列表获取成功")))
            .onErrorResume(IllegalStateException.class, this::domainError);
    }

    @PostMapping("/api/forum/sections/{sectionId}/threads")
    public Mono<ResponseEntity<ApiResult<ForumThreadCreationResult>>> publish(
        @PathVariable("sectionId") long sectionId,
        @RequestBody ForumThreadPublishRequest request,
        Authentication authentication
    ) {
        return Mono.defer(() -> {
            ForumMemberActor author = authenticatedActor(authentication);
            ForumThreadPublishRequest safe = request == null
                ? new ForumThreadPublishRequest("", "")
                : request;
            return threadService.publish(
                    author,
                    new ForumThreadCreateCommand(
                        sectionId,
                        safe.title(),
                        safe.contentText()
                    )
                )
                .map(result -> ResponseEntity
                    .status(HttpStatus.CREATED)
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResult.ok(result, "论坛主题发布成功")));
        })
            .onErrorResume(IllegalStateException.class, this::domainError);
    }

    private ForumMemberActor actor(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof MemberAuthUser user)) {
            return actorFactory.anonymous();
        }
        return actorFactory.authenticated(user);
    }

    private ForumMemberActor authenticatedActor(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof MemberAuthUser user)) {
            throw new IllegalStateException("发布论坛主题需要先登录。");
        }
        return actorFactory.authenticated(user);
    }

    private <T> Mono<ResponseEntity<ApiResult<T>>> domainError(
        IllegalStateException error
    ) {
        String message = error.getMessage() == null
            ? "论坛请求处理失败。"
            : error.getMessage();
        HttpStatus status;
        String code;
        if (message.contains("不存在")) {
            status = HttpStatus.NOT_FOUND;
            code = "FORUM_NOT_FOUND";
        } else if (message.contains("需要先登录")
            || message.contains("缺少已认证会员主体")
            || message.contains("缺少可信认证上下文")) {
            status = HttpStatus.UNAUTHORIZED;
            code = "FORUM_AUTH_REQUIRED";
        } else if (forbidden(message)) {
            status = HttpStatus.FORBIDDEN;
            code = "FORUM_OPERATION_FORBIDDEN";
        } else {
            status = HttpStatus.BAD_REQUEST;
            code = "FORUM_REQUEST_INVALID";
        }
        return Mono.just(ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore())
            .body(ApiResult.fail(code, message)));
    }

    private boolean forbidden(String message) {
        return message.contains("缺少论坛权限")
            || message.contains("禁止发布")
            || message.contains("没有该私有板块")
            || message.contains("已关闭新主题")
            || message.contains("板块已停用")
            || message.contains("账号不可用")
            || message.contains("当前用户组不能");
    }

    /**
     * 只允许提交主题文本，作者字段不存在。
     */
    public record ForumThreadPublishRequest(
        String title,
        String contentText
    ) {
    }
}
