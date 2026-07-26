package com.aquafish.admin.web;

import com.aquafish.admin.user.AdminIpBanManagementService;
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
 * 后台 IP 封禁规则写接口。
 */
@RestController
@RequestMapping("/api/admin/users/ip-bans")
public class AdminIpBanManagementController {

    private final AdminIpBanManagementService service;

    public AdminIpBanManagementController(AdminIpBanManagementService service) {
        this.service = service;
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> create(
        Authentication authentication,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handle(authentication, user ->
            service.create(user, safeRequest(request))
        );
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> update(
        Authentication authentication,
        @PathVariable("id") long id,
        @RequestBody(required = false) Map<String, Object> request
    ) {
        return handle(authentication, user ->
            service.update(user, id, safeRequest(request))
        );
    }

    @PostMapping("/{id}/enable")
    public Mono<ResponseEntity<Map<String, Object>>> enable(
        Authentication authentication,
        @PathVariable("id") long id
    ) {
        return handle(authentication, user -> service.setEnabled(user, id, true));
    }

    @PostMapping("/{id}/disable")
    public Mono<ResponseEntity<Map<String, Object>>> disable(
        Authentication authentication,
        @PathVariable("id") long id
    ) {
        return handle(authentication, user -> service.setEnabled(user, id, false));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(
        Authentication authentication,
        @PathVariable("id") long id
    ) {
        return handle(authentication, user -> service.delete(user, id));
    }

    private Mono<ResponseEntity<Map<String, Object>>> handle(
        Authentication authentication,
        Action action
    ) {
        return Mono.defer(() -> action.run(authenticatedUser(authentication)))
            .map(result -> ResponseEntity.ok(ok(
                String.valueOf(result.getOrDefault("message", "操作成功。")),
                result.get("data")
            )))
            .onErrorResume(error -> Mono.just(
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(fail(rootMessage(error)))
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

    private Map<String, Object> fail(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", "ADMIN_IP_BAN_ACTION_FAILED");
        body.put("message", message);
        body.put("data", null);
        return body;
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null || current.getMessage() == null
            ? "IP 封禁操作失败。"
            : current.getMessage();
    }

    private interface Action {
        Mono<Map<String, Object>> run(AdminAuthUser user);
    }
}
