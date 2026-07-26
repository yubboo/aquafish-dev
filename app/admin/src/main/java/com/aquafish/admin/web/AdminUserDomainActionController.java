package com.aquafish.admin.web;

import com.aquafish.admin.user.AdminUserDomainActionService;
import com.aquafish.core.admin.auth.AdminAuthUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Step 17-26-4：用户领域管理动作接口。
 *
 * <p>不是普通 CRUD。这里暴露的是后台真实管理动作：创建用户、更新基础资料、
 * 修改用户组、启用、禁用、封禁、解除封禁、重置密码、分配管理组、移除管理组、
 * 积分奖惩。</p>
 *
 * <p>Controller 只负责从 Spring Security 取得操作者并统一转换 HTTP 响应；业务校验、
 * 权限边界、响应式事务、审计日志和会话撤销均由
 * {@link AdminUserDomainActionService} 完成。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserDomainActionController {

    private final AdminUserDomainActionService adminUserDomainActionService;

    public AdminUserDomainActionController(
        AdminUserDomainActionService adminUserDomainActionService
    ) {
        this.adminUserDomainActionService = adminUserDomainActionService;
    }

    /** 接收后台新建用户请求，认证操作者后交给领域服务完成校验、加密和事务写入。 */
    @PostMapping("/create")
    public Mono<ResponseEntity<Map<String, Object>>> createUser(
        Authentication authentication,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handleAuthorized("ADMIN_USER_CREATE_FAILED", authentication, user ->
            adminUserDomainActionService.createUser(user, safeRequest(request))
        );
    }

    /** 修改目标用户的用户名、邮箱、显示名和头像；越权规则由领域服务统一校验。 */
    @PostMapping("/{id}/update-basic")
    public Mono<ResponseEntity<Map<String, Object>>> updateBasic(
        Authentication authentication,
        @PathVariable("id") long id,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handleAuthorized("ADMIN_USER_UPDATE_BASIC_FAILED", authentication, user ->
            adminUserDomainActionService.updateBasic(user, id, safeRequest(request))
        );
    }

    /** 调整用户所属的前台用户组，并记录后台操作日志。 */
    @PostMapping("/{id}/change-user-group")
    public Mono<ResponseEntity<Map<String, Object>>> changeUserGroup(
        Authentication authentication,
        @PathVariable("id") long id,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handleAuthorized("ADMIN_USER_CHANGE_GROUP_FAILED", authentication, user ->
            adminUserDomainActionService.changeUserGroup(user, id, safeRequest(request))
        );
    }

    /** 恢复目标用户为可用状态。 */
    @PostMapping("/{id}/enable")
    public Mono<ResponseEntity<Map<String, Object>>> enableUser(
        Authentication authentication,
        @PathVariable("id") long id
    ) {
        return handleAuthorized("ADMIN_USER_ENABLE_FAILED", authentication, user ->
            adminUserDomainActionService.enableUser(user, id)
        );
    }

    /** 禁用目标用户；不允许操作者禁用自己，并使相关会话按服务规则失效。 */
    @PostMapping("/{id}/disable")
    public Mono<ResponseEntity<Map<String, Object>>> disableUser(
        Authentication authentication,
        @PathVariable("id") long id,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handleAuthorized("ADMIN_USER_DISABLE_FAILED", authentication, user ->
            adminUserDomainActionService.disableUser(user, id, safeRequest(request))
        );
    }

    /** 安全删除用户并释放展示 UID；内部历史关系和审计主键不会被复用。 */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteUser(
        Authentication authentication,
        @PathVariable("id") long id
    ) {
        return handleAuthorized("ADMIN_USER_DELETE_FAILED", authentication, user ->
            adminUserDomainActionService.deleteUser(user, id)
        );
    }

    /** 创建封禁记录、修改用户状态并撤销目标用户的后台会话。 */
    @PostMapping("/{id}/ban")
    public Mono<ResponseEntity<Map<String, Object>>> banUser(
        Authentication authentication,
        @PathVariable("id") long id,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handleAuthorized("ADMIN_USER_BAN_FAILED", authentication, user ->
            adminUserDomainActionService.banUser(user, id, safeRequest(request))
        );
    }

    /** 关闭目标用户的有效封禁记录并恢复可用状态。 */
    @PostMapping("/{id}/unban")
    public Mono<ResponseEntity<Map<String, Object>>> unbanUser(
        Authentication authentication,
        @PathVariable("id") long id,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handleAuthorized("ADMIN_USER_UNBAN_FAILED", authentication, user ->
            adminUserDomainActionService.unbanUser(user, id, safeRequest(request))
        );
    }

    /** 以 BCrypt 重置目标用户密码，并撤销旧会话避免旧凭据继续使用。 */
    @PostMapping("/{id}/reset-password")
    public Mono<ResponseEntity<Map<String, Object>>> resetPassword(
        Authentication authentication,
        @PathVariable("id") long id,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handleAuthorized("ADMIN_USER_RESET_PASSWORD_FAILED", authentication, user ->
            adminUserDomainActionService.resetPassword(user, id, safeRequest(request))
        );
    }

    /** 为用户分配后台管理组；该权限提升操作仅允许超级管理员执行。 */
    @PostMapping("/{id}/assign-admin-groups")
    public Mono<ResponseEntity<Map<String, Object>>> assignAdminGroups(
        Authentication authentication,
        @PathVariable("id") long id,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handleAuthorized("ADMIN_USER_ASSIGN_ADMIN_GROUPS_FAILED", authentication, user ->
            adminUserDomainActionService.assignAdminGroups(user, id, safeRequest(request))
        );
    }

    /** 移除用户的后台管理组；仅超级管理员可执行且不能移除自己的管理组。 */
    @PostMapping("/{id}/remove-admin-groups")
    public Mono<ResponseEntity<Map<String, Object>>> removeAdminGroups(
        Authentication authentication,
        @PathVariable("id") long id,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handleAuthorized("ADMIN_USER_REMOVE_ADMIN_GROUPS_FAILED", authentication, user ->
            adminUserDomainActionService.removeAdminGroups(user, id, safeRequest(request))
        );
    }

    /** 调整用户积分余额，并同时写入积分明细、调整记录和后台审计日志。 */
    @PostMapping("/{id}/adjust-points")
    public Mono<ResponseEntity<Map<String, Object>>> adjustPoints(
        Authentication authentication,
        @PathVariable("id") long id,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handleAuthorized("ADMIN_USER_ADJUST_POINTS_FAILED", authentication, user ->
            adminUserDomainActionService.adjustPoints(user, id, safeRequest(request))
        );
    }

    /**
     * 统一提取已登录管理员并包装领域动作异常，保证所有写接口返回相同响应结构。
     */
    private Mono<ResponseEntity<Map<String, Object>>> handleAuthorized(
        String errorCode,
        Authentication authentication,
        UserAction action
    ) {
        return Mono.defer(() -> handle(
                errorCode,
                action.run(authenticatedUser(authentication))
            ))
            .onErrorResume(error ->
                Mono.just(
                    ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(error(errorCode, rootMessage(error)))
                )
            );
    }

    /** 把可选请求体规范为空 Map，避免下游字段读取出现空指针。 */
    private Map<String, Object> safeRequest(Map<String, Object> request) {
        return request == null ? new LinkedHashMap<>() : request;
    }

    /** 执行异步领域动作，并把成功结果或根异常转换为 HTTP 响应。 */
    private Mono<ResponseEntity<Map<String, Object>>> handle(
        String errorCode,
        Mono<Map<String, Object>> action
    ) {
        return action.map(result -> {
                String message = String.valueOf(
                    result.getOrDefault("message", "操作成功。")
                );
                return ResponseEntity.ok(ok(message, result.get("data")));
            })
            .onErrorResume(error -> Mono.just(
                ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(error(errorCode, rootMessage(error)))
            ));
    }

    /** 构造与前端请求守卫约定一致的成功响应。 */
    private Map<String, Object> ok(String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("code", "OK");
        body.put("message", message);
        body.put("data", data);
        return body;
    }

    /** 构造与前端请求守卫约定一致的失败响应。 */
    private Map<String, Object> error(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        return body;
    }

    /** 展开响应式包装异常，向管理员展示最接近业务原因的错误信息。 */
    private String rootMessage(Throwable error) {
        if (error == null) {
            return "未知错误";
        }

        Throwable current = error;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        if (message == null || message.isBlank()) {
            return current.getClass().getName();
        }

        return message;
    }

    /** 用户领域动作的延迟执行函数；只有认证身份提取成功后才会运行。 */
    private interface UserAction {
        Mono<Map<String, Object>> run(AdminAuthUser user);
    }

    /** 从 Spring Security 上下文取得后台用户，未登录时立即拒绝写操作。 */
    private AdminAuthUser authenticatedUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminAuthUser user)) {
            throw new IllegalStateException("未登录或登录已过期，请重新登录。");
        }
        return user;
    }
}
