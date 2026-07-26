package com.aquafish.admin.web;

import com.aquafish.admin.user.AdminUserQueryService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Step 17-26-2：用户列表 / 用户详情真实接口。
 *
 * <p>本类负责 HTTP 查询参数、状态码和统一响应结构；实际 R2DBC 查询、表前缀处理及
 * 角色/管理组/资料聚合由 {@link AdminUserQueryService} 完成。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserQueryController {

    private final AdminUserQueryService adminUserQueryService;

    public AdminUserQueryController(AdminUserQueryService adminUserQueryService) {
        this.adminUserQueryService = adminUserQueryService;
    }

    /** 按分页、关键字、状态和管理员身份筛选用户，并返回汇总角色信息。 */
    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> listUsers(
        @RequestParam(name = "page", required = false) Integer page,
        @RequestParam(name = "pageSize", required = false) Integer pageSize,
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "adminOnly", required = false) Boolean adminOnly
    ) {
        return adminUserQueryService
            .listUsers(page, pageSize, keyword, status, adminOnly)
            .map(data -> ResponseEntity.ok(ok("用户列表获取成功。", data)))
            .onErrorResume(error -> Mono.just(
                ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("ADMIN_USERS_LIST_FAILED", rootMessage(error)))
            ));
    }

    /** 返回安装迁移创建的前台用户组，供用户编辑表单选择。 */
    @GetMapping("/groups")
    public Mono<ResponseEntity<Map<String, Object>>> listUserGroups() {
        return handleReactive(
            "ADMIN_USER_GROUPS_FAILED",
            "用户组获取成功。",
            adminUserQueryService.listUserGroups()
        );
    }

    /** 返回角色字典，供后台展示用户权限来源。 */
    @GetMapping("/roles")
    public Mono<ResponseEntity<Map<String, Object>>> listRoles() {
        return handleReactive(
            "ADMIN_USER_ROLES_FAILED",
            "角色列表获取成功。",
            adminUserQueryService.listRoles()
        );
    }

    /** 返回后台管理组字典，供超级管理员分配后台权限。 */
    @GetMapping("/admin-groups")
    public Mono<ResponseEntity<Map<String, Object>>> listAdminGroups() {
        return handleReactive(
            "ADMIN_GROUPS_FAILED",
            "管理组获取成功。",
            adminUserQueryService.listAdminGroups()
        );
    }

    /** 查询单个用户及其资料、角色、管理组、封禁和积分明细。 */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> userDetail(@PathVariable("id") long id) {
        return adminUserQueryService.userDetail(id)
            .map(data -> ResponseEntity.ok(ok("用户详情获取成功。", data)))
            .onErrorResume(NoSuchElementException.class, error -> Mono.just(
                ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(error("ADMIN_USER_NOT_FOUND", rootMessage(error)))
            ))
            .onErrorResume(error -> Mono.just(
                ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("ADMIN_USER_DETAIL_FAILED", rootMessage(error)))
            ));
    }

    /** 统一包装字典类响应式查询，避免各接口重复异常转换逻辑。 */
    private Mono<ResponseEntity<Map<String, Object>>> handleReactive(
        String errorCode,
        String successMessage,
        Mono<Map<String, Object>> source
    ) {
        return source
            .map(data ->
                ResponseEntity.ok(
                    ok(
                        successMessage,
                        data
                    )
                )
            )
            .onErrorResume(error ->
                Mono.just(
                    ResponseEntity
                        .status(
                            HttpStatus.INTERNAL_SERVER_ERROR
                        )
                        .body(
                            error(
                                errorCode,
                                rootMessage(error)
                            )
                        )
                )
            );
    }

    /** 构造用户查询接口的统一成功响应。 */
    private Map<String, Object> ok(String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("code", "OK");
        body.put("message", message);
        body.put("data", data);
        return body;
    }

    /** 构造用户查询接口的统一失败响应。 */
    private Map<String, Object> error(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        return body;
    }

    /** 展开底层数据库异常，保留最接近故障根源的说明。 */
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
}
