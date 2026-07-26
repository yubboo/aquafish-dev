package com.aquafish.admin.web;

import com.aquafish.admin.user.AdminUserGroupManagementService;
import com.aquafish.core.admin.auth.AdminAuthUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 用户组与后台管理组的增删改 HTTP 入口。
 *
 * <p>查询仍由 {@link AdminUserQueryController} 提供；本控制器只承接写操作，操作者必须
 * 来自 Spring Security 后台会话。超级管理员权限、内置组保护、关联检查、事务和审计
 * 均在 {@link AdminUserGroupManagementService} 内完成。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserGroupManagementController {

    private final AdminUserGroupManagementService managementService;

    public AdminUserGroupManagementController(
        AdminUserGroupManagementService managementService
    ) {
        this.managementService = managementService;
    }

    /* BEGIN：前台用户组写接口。 */

    @PostMapping("/groups")
    public Mono<ResponseEntity<Map<String, Object>>> createUserGroup(
        Authentication authentication,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handle("ADMIN_USER_GROUP_CREATE_FAILED", authentication, user ->
            managementService.createUserGroup(user, safeRequest(request))
        );
    }

    @PutMapping("/groups/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateUserGroup(
        Authentication authentication,
        @PathVariable("id") long id,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handle("ADMIN_USER_GROUP_UPDATE_FAILED", authentication, user ->
            managementService.updateUserGroup(user, id, safeRequest(request))
        );
    }

    @DeleteMapping("/groups/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteUserGroup(
        Authentication authentication,
        @PathVariable("id") long id
    ) {
        return handle("ADMIN_USER_GROUP_DELETE_FAILED", authentication, user ->
            managementService.deleteUserGroup(user, id)
        );
    }

    /* END：前台用户组写接口。 */

    /* BEGIN：后台管理组写接口。 */

    @PostMapping("/admin-groups")
    public Mono<ResponseEntity<Map<String, Object>>> createAdminGroup(
        Authentication authentication,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handle("ADMIN_GROUP_CREATE_FAILED", authentication, user ->
            managementService.createAdminGroup(user, safeRequest(request))
        );
    }

    @PutMapping("/admin-groups/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> updateAdminGroup(
        Authentication authentication,
        @PathVariable("id") long id,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handle("ADMIN_GROUP_UPDATE_FAILED", authentication, user ->
            managementService.updateAdminGroup(user, id, safeRequest(request))
        );
    }

    @DeleteMapping("/admin-groups/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteAdminGroup(
        Authentication authentication,
        @PathVariable("id") long id
    ) {
        return handle("ADMIN_GROUP_DELETE_FAILED", authentication, user ->
            managementService.deleteAdminGroup(user, id)
        );
    }

    /* END：后台管理组写接口。 */

    /** 统一提取管理员身份并转换领域结果，避免六个接口重复响应包装。 */
    private Mono<ResponseEntity<Map<String, Object>>> handle(
        String errorCode,
        Authentication authentication,
        GroupAction action
    ) {
        return Mono.defer(() -> action.run(authenticatedUser(authentication)))
            .map(result -> ResponseEntity.ok(ok(
                String.valueOf(result.getOrDefault("message", "操作成功。")),
                result.get("data")
            )))
            .onErrorResume(error -> Mono.just(
                ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error(errorCode, rootMessage(error)))
            ));
    }

    private AdminAuthUser authenticatedUser(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof AdminAuthUser user)) {
            throw new IllegalStateException("未登录或登录已过期，请重新登录。");
        }
        return user;
    }

    private Map<String, Object> safeRequest(Map<String, Object> request) {
        return request == null ? new LinkedHashMap<>() : request;
    }

    private Map<String, Object> ok(String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("code", "OK");
        body.put("message", message);
        body.put("data", data);
        return body;
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        return body;
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        if (current == null || current.getMessage() == null
            || current.getMessage().isBlank()) {
            return "未知错误";
        }
        return current.getMessage();
    }

    private interface GroupAction {
        Mono<Map<String, Object>> run(AdminAuthUser user);
    }
}
