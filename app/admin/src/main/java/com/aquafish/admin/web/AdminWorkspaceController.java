package com.aquafish.admin.web;

import com.aquafish.admin.workspace.AdminWorkspaceQueryService;
import com.aquafish.common.web.ApiResult;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 后台初版控制台与跨域只读工作台 API。
 *
 * <p>HTTP 路径为 {@code /api/admin/dashboard} 和
 * {@code /api/admin/workspace/{domain}/{resource}}。后台 Spring Security
 * 会话链在进入本控制器前完成认证；服务层只接受白名单资源，未知路径返回 404，
 * 不把 URL 拼接成 SQL。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminWorkspaceController {

    private final AdminWorkspaceQueryService service;

    public AdminWorkspaceController(AdminWorkspaceQueryService service) {
        this.service = service;
    }

    /** 返回用户、论坛、内容和主题的真实数量。 */
    @GetMapping("/dashboard")
    public Mono<ResponseEntity<ApiResult<Map<String, Object>>>> dashboard() {
        return service.dashboard()
            .map(data -> ResponseEntity.ok(ApiResult.ok(data, "控制台数据获取成功")))
            .onErrorResume(this::serverError);
    }

    /** 分页读取一个已经登记的后台资源投影。 */
    @GetMapping("/workspace/{domain}/{resource}")
    public Mono<ResponseEntity<ApiResult<Map<String, Object>>>> resource(
        @PathVariable("domain") String domain,
        @PathVariable("resource") String resource,
        @RequestParam(name = "page", required = false) Integer page,
        @RequestParam(name = "pageSize", required = false) Integer pageSize
    ) {
        return service.resource(domain, resource, page, pageSize)
            .map(data -> ResponseEntity.ok(ApiResult.ok(data, "管理数据获取成功")))
            .onErrorResume(IllegalArgumentException.class, error -> Mono.just(
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResult.fail("ADMIN_RESOURCE_NOT_FOUND", safeMessage(error)))
            ))
            .onErrorResume(this::serverError);
    }

    private Mono<ResponseEntity<ApiResult<Map<String, Object>>>> serverError(
        Throwable error
    ) {
        return Mono.just(
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.fail("ADMIN_WORKSPACE_FAILED", safeMessage(error)))
        );
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank() ? "后台数据读取失败。" : message;
    }
}
