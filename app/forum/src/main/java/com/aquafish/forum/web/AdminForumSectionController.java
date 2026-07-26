package com.aquafish.forum.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.admin.auth.AdminAuthUser;
import com.aquafish.forum.permission.ForumManagementActor;
import com.aquafish.forum.permission.ForumPermissions;
import com.aquafish.forum.section.ForumSection;
import com.aquafish.forum.section.ForumSectionCommand;
import com.aquafish.forum.section.ForumSectionManagementService;
import java.util.List;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 后台论坛板块管理 API。
 *
 * <p>操作人只从后台安全上下文取得。初版已登录管理员拥有板块管理权限，
 * 超级管理员仍保留领域层快速放行语义；请求体不能提交 operatorUserId。</p>
 */
@RestController
@RequestMapping("/api/admin/forum/sections")
public class AdminForumSectionController {

    private final ForumSectionManagementService service;

    public AdminForumSectionController(ForumSectionManagementService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResult<List<ForumSection>>>> list(
        Authentication authentication
    ) {
        return Mono.defer(() -> service.listForManagement(actor(authentication))
                .collectList()
                .map(items -> ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResult.ok(items, "论坛板块获取成功"))))
            .onErrorResume(this::failure);
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResult<ForumSection>>> create(
        Authentication authentication,
        @RequestBody(required = false) ForumSectionCommand command
    ) {
        return Mono.defer(() -> service.create(actor(authentication), command)
                .map(item -> ResponseEntity.status(HttpStatus.CREATED)
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResult.ok(item, "论坛板块创建成功"))))
            .onErrorResume(this::failure);
    }

    @PutMapping("/{sectionId}")
    public Mono<ResponseEntity<ApiResult<ForumSection>>> update(
        Authentication authentication,
        @PathVariable("sectionId") long sectionId,
        @RequestBody(required = false) ForumSectionCommand command
    ) {
        return Mono.defer(() -> service.update(actor(authentication), sectionId, command)
                .map(item -> ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResult.ok(item, "论坛板块更新成功"))))
            .onErrorResume(this::failure);
    }

    @PostMapping("/{sectionId}/enabled/{enabled}")
    public Mono<ResponseEntity<ApiResult<ForumSection>>> changeEnabled(
        Authentication authentication,
        @PathVariable("sectionId") long sectionId,
        @PathVariable("enabled") boolean enabled
    ) {
        return Mono.defer(() -> service.changeEnabled(
                    actor(authentication),
                    sectionId,
                    enabled
                )
                .map(item -> ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResult.ok(item, enabled ? "论坛板块已启用" : "论坛板块已停用"))))
            .onErrorResume(this::failure);
    }

    private ForumManagementActor actor(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof AdminAuthUser user)
            || !user.hasAdminAccess()) {
            throw new IllegalStateException("未登录或没有论坛管理权限。");
        }
        return new ForumManagementActor(
            user.id(),
            user.superAdmin(),
            Set.of(ForumPermissions.SECTION_MANAGE)
        );
    }

    private <T> Mono<ResponseEntity<ApiResult<T>>> failure(Throwable error) {
        String message = error == null || error.getMessage() == null
            ? "论坛板块操作失败。"
            : error.getMessage();
        HttpStatus status = message.contains("未登录")
            ? HttpStatus.UNAUTHORIZED
            : HttpStatus.BAD_REQUEST;
        return Mono.just(ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore())
            .body(ApiResult.fail("FORUM_SECTION_OPERATION_FAILED", message)));
    }
}
