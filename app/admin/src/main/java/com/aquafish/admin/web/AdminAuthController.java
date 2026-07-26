package com.aquafish.admin.web;

import com.aquafish.common.net.TrustedProxyClientIpResolver;
import com.aquafish.common.web.ApiResult;
import com.aquafish.admin.security.AdminSecurityConfiguration;
import com.aquafish.admin.security.AdminSessionTokenResolver;
import com.aquafish.admin.security.AdminLoginRateLimiter;
import com.aquafish.admin.security.AdminLoginRateLimitException;
import com.aquafish.core.admin.auth.AdminAuthService;
import com.aquafish.core.admin.auth.AdminAuthToken;
import com.aquafish.core.admin.auth.AdminAuthUser;
import com.aquafish.core.admin.auth.AdminLoginMetadata;
import com.aquafish.core.admin.auth.AdminLoginRequest;
import com.aquafish.core.admin.auth.AdminLogoutResult;
import com.aquafish.user.auth.MemberAuthService;
import com.aquafish.user.auth.MemberAuthToken;
import com.aquafish.user.auth.MemberLoginMetadata;
import com.aquafish.user.security.MemberSecurityConfiguration;
import com.aquafish.user.security.MemberSessionTokenResolver;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 后台登录接口。
 *
 * 当前阶段：
 * Step 17-23：后台登录接口与管理员登录。
 *
 * 接口：
 * POST /api/admin/auth/login
 * GET  /api/admin/auth/me
 * POST /api/admin/auth/logout
 */
@RestController
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final MemberAuthService memberAuthService;
    private final AdminLoginRateLimiter loginRateLimiter;

    private final TrustedProxyClientIpResolver
        trustedProxyClientIpResolver;

    public AdminAuthController(
        AdminAuthService adminAuthService,
        MemberAuthService memberAuthService,
        AdminLoginRateLimiter loginRateLimiter,
        TrustedProxyClientIpResolver
            trustedProxyClientIpResolver
    ) {
        this.adminAuthService = adminAuthService;
        this.memberAuthService = memberAuthService;
        this.loginRateLimiter = loginRateLimiter;
        this.trustedProxyClientIpResolver =
            trustedProxyClientIpResolver;
    }

    /**
     * 后台登录。
     */
    @PostMapping("/api/admin/auth/login")
    public Mono<ResponseEntity<ApiResult<AdminLoginResponse>>> login(
        @RequestBody AdminLoginRequest request,
        ServerWebExchange exchange
    ) {
        AdminLoginMetadata metadata = loginMetadata(exchange.getRequest());
        String loginName = request == null ? "" : request.normalized().username();

        return Mono.defer(() -> {
            loginRateLimiter.requireAllowed(metadata.clientIp(), loginName);
            return adminAuthService.login(request, metadata)
                .flatMap(adminToken -> memberAuthService.issueTrustedWebSession(
                        adminToken.user().id(),
                        memberMetadata(exchange.getRequest()),
                        Duration.ofSeconds(adminToken.expiresInSeconds())
                    )
                    .map(memberToken -> new UnifiedLogin(adminToken, memberToken))
                    .onErrorResume(error -> {
                        adminAuthService.logout("Bearer " + adminToken.accessToken());
                        return Mono.error(error);
                    }));
        })
            .doOnSuccess(login -> loginRateLimiter.recordSuccess(metadata.clientIp(), loginName))
            .doOnError(error -> {
                if (!(error instanceof AdminLoginRateLimitException)) {
                    loginRateLimiter.recordFailure(metadata.clientIp(), loginName);
                }
            })
            .map(login -> {
                exchange.getResponse().addCookie(sessionCookie(login.adminToken(), exchange));
                exchange.getResponse().addCookie(memberSessionCookie(login.memberToken(), exchange));
                return ResponseEntity.ok(ApiResult.ok(
                    new AdminLoginResponse(
                        login.adminToken().expiresAt(),
                        login.adminToken().expiresInSeconds(),
                        login.adminToken().user()
                    ),
                    "后台登录成功"
                ));
            })
            .onErrorResume(AdminLoginRateLimitException.class, error ->
                Mono.just(ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(error.retryAfterSeconds()))
                    .body(ApiResult.fail("ADMIN_AUTH_RATE_LIMITED", error.getMessage())))
            )
            .onErrorResume(
                IllegalStateException.class,
                error ->
                    Mono.just(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                            ApiResult.<AdminLoginResponse>fail(
                                "ADMIN_AUTH_LOGIN_FAILED",
                                error.getMessage()
                            )
                        )
                    )
            );
    }

    /**
     * 当前后台用户。
     */
    @GetMapping("/api/admin/auth/me")
    public ApiResult<AdminAuthUser> me(Authentication authentication) {
        return ApiResult.ok(
            authenticatedUser(authentication),
            "当前登录用户获取成功"
        );
    }

    /**
     * 获取当前后台会话对应的 CSRF 令牌和请求头名称。
     *
     * <p>后台前端在执行新增、修改、删除等写请求前调用本接口；返回值由
     * {@code admin-fetch-guard.ts} 缓存并自动附加到后续请求，防止第三方站点借用
     * 浏览器 Cookie 发起跨站写操作。</p>
     */
    @GetMapping("/api/admin/auth/csrf")
    public Mono<ApiResult<Map<String, String>>> csrf(ServerWebExchange exchange) {
        Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            return Mono.error(new IllegalStateException("安全令牌尚未生成。"));
        }
        return token.map(value -> ApiResult.ok(
            Map.of(
                "token", value.getToken(),
                "headerName", value.getHeaderName()
            ),
            "安全令牌获取成功"
        ));
    }

    /**
     * 从前台会员会话桥接到后台管理员会话。
     *
     * <p>当前台已登录管理员点击"管理后台"时，浏览器只携带
     * {@code AQUAFISH_MEMBER_SESSION} Cookie。本接口使用该 Cookie 验证会员身份，
     * 确认具备管理员角色后通过受信签发绕过密码直接为浏览器补发
     * {@code AQUAFISH_ADMIN_SESSION} Cookie，后续后台 API 调用即可直接通过后台守卫。</p>
     *
     * <p>桥接成功返回当前管理员用户快照；会员无管理员角色返回 403；
     * 会员会话无效返回 401。</p>
     */
    @GetMapping("/api/admin/auth/bridge")
    public Mono<ResponseEntity<ApiResult<AdminAuthUser>>> bridgeSession(
        ServerWebExchange exchange
    ) {
        String memberToken = MemberSessionTokenResolver.resolve(exchange.getRequest());
        if (memberToken == null || memberToken.isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResult.fail("BRIDGE_NO_MEMBER_SESSION", "没有有效的前台会员会话，请先登录。")));
        }

        return memberAuthService.authenticate(memberToken)
            .flatMap(memberUser -> {
                if (!memberUser.hasAdminAccess()) {
                    return Mono.<ResponseEntity<ApiResult<AdminAuthUser>>>just(
                        ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(ApiResult.fail("BRIDGE_NO_ADMIN_ROLE", "当前账号没有管理后台权限。"))
                    );
                }

                // 受信签发：已验证会员身份且具备管理员角色，跳过密码直接签发后台 Token
                return adminAuthService.issueTrustedToken(
                        memberUser.id(),
                        memberUser.username(),
                        loginMetadata(exchange.getRequest()),
                        true
                    )
                    .map(adminToken -> {
                        exchange.getResponse().addCookie(
                            sessionCookie(adminToken, exchange)
                        );
                        return ResponseEntity.ok(ApiResult.ok(
                            adminToken.user(),
                            "后台会话桥接成功"
                        ));
                    });
            })
            .onErrorResume(error -> {
                if (error instanceof IllegalStateException) {
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResult.<AdminAuthUser>fail("BRIDGE_FAILED", error.getMessage())));
                }
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResult.<AdminAuthUser>fail("BRIDGE_ERROR", "会话桥接失败，请重新登录。")));
            });
    }

    /**
     * 退出后台登录，同时清除后台和前台的两个会话 Cookie。
     *
     * <p>管理员登录时后端会同时签发 ADMIN 和 MEMBER 两枚 Cookie；
     * 退出登录必须把两枚 Cookie 全部置为立即过期，否则前台页面仍然显示为已登录状态。</p>
     */
    @PostMapping("/api/admin/auth/logout")
    public ApiResult<AdminLogoutResult> logout(
        ServerWebExchange exchange
    ) {
        String token = AdminSessionTokenResolver.resolve(exchange.getRequest());
        exchange.getResponse().addCookie(expiredSessionCookie(exchange));
        exchange.getResponse().addCookie(expiredMemberSessionCookie(exchange));
        return ApiResult.ok(
            adminAuthService.logout(token == null ? null : "Bearer " + token),
            "后台退出登录完成"
        );
    }

    /**
     * 构造后台登录会话 Cookie。
     *
     * <p>HttpOnly 阻止前端脚本读取令牌，SameSite=Lax 降低跨站请求风险；
     * HTTPS 或可信反向代理声明 HTTPS 时自动增加 Secure 属性。</p>
     */
    private ResponseCookie sessionCookie(
        AdminAuthToken token,
        ServerWebExchange exchange
    ) {
        return ResponseCookie.from(
                AdminSecurityConfiguration.SESSION_COOKIE,
                token.accessToken()
            )
            .httpOnly(true)
            .secure(isSecure(exchange.getRequest()))
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofSeconds(token.expiresInSeconds()))
            .build();
    }

    private ResponseCookie memberSessionCookie(
        MemberAuthToken token,
        ServerWebExchange exchange
    ) {
        return ResponseCookie.from(
                MemberSecurityConfiguration.SESSION_COOKIE,
                token.accessToken()
            )
            .httpOnly(true)
            .secure(isSecure(exchange.getRequest()))
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofSeconds(token.expiresInSeconds()))
            .build();
    }

    /**
     * 构造立即过期的后台会话 Cookie，使浏览器在退出登录时删除本地会话。
     */
    private ResponseCookie expiredSessionCookie(ServerWebExchange exchange) {
        return ResponseCookie.from(AdminSecurityConfiguration.SESSION_COOKIE, "")
            .httpOnly(true)
            .secure(isSecure(exchange.getRequest()))
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();
    }

    /**
     * 构造立即过期的前台会员会话 Cookie。
     *
     * <p>管理员登录时一并签发了前台会话，退出时也必须清除，否则前台页面
     * 在管理员退出后仍然显示已登录状态。</p>
     */
    private ResponseCookie expiredMemberSessionCookie(ServerWebExchange exchange) {
        return ResponseCookie.from(MemberSecurityConfiguration.SESSION_COOKIE, "")
            .httpOnly(true)
            .secure(isSecure(exchange.getRequest()))
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();
    }

    /**
     * 同时识别直连 HTTPS 与受信反向代理传递的 HTTPS 协议标记。
     */
    private boolean isSecure(
        ServerHttpRequest request
    ) {
        return trustedProxyClientIpResolver
            .isSecureRequest(
                request
                    .getURI()
                    .getScheme(),
                TrustedProxyClientIpResolver
                    .normalizeRemoteAddress(
                        request.getRemoteAddress()
                    ),
                request
                    .getHeaders()
                    .getFirst(
                        "X-Forwarded-Proto"
                    )
            );
    }

    /**
     * 从 Spring Security 上下文取得已验证管理员；无有效身份时统一终止请求。
     */
    private AdminAuthUser authenticatedUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminAuthUser user)) {
            throw new IllegalStateException("登录已过期，请重新登录。");
        }
        return user;
    }

    private MemberLoginMetadata memberMetadata(
        ServerHttpRequest request
    ) {
        if (request == null) {
            return MemberLoginMetadata.empty();
        }
        String remote = cleanValue(remoteAddress(request));
        String forwarded = cleanValue(request.getHeaders().getFirst("X-Forwarded-For"));
        String realIp = cleanValue(request.getHeaders().getFirst("X-Real-IP"));
        return new MemberLoginMetadata(
            trustedProxyClientIpResolver.resolve(
                remote,
                forwarded,
                realIp
            ),
            cleanValue(
                request.getHeaders().getFirst(
                    "User-Agent"
                )
            )
        ).normalized();
    }

    private record UnifiedLogin(
        AdminAuthToken adminToken,
        MemberAuthToken memberToken
    ) {
    }

    /**
     * 登录成功后返回给前端的非敏感会话摘要；访问令牌只保存在 HttpOnly Cookie。
     */
    public record AdminLoginResponse(
        String expiresAt,
        long expiresInSeconds,
        AdminAuthUser user
    ) {
    }
    /**
     * 提取登录请求的 IP、代理头和 User-Agent。
     *
     * 当前同时保存原始代理头，便于以后配置可信代理规则。
     */
    AdminLoginMetadata loginMetadata(
        ServerHttpRequest request
    ) {
        if (request == null) {
            return AdminLoginMetadata.empty();
        }

        String remoteAddress =
            cleanValue(
                remoteAddress(request)
            );

        String xForwardedFor =
            cleanValue(
                request.getHeaders().getFirst(
                    "X-Forwarded-For"
                )
            );

        String xRealIp =
            cleanValue(
                request.getHeaders().getFirst(
                    "X-Real-IP"
                )
            );

        String clientIp =
            trustedProxyClientIpResolver.resolve(
                remoteAddress,
                xForwardedFor,
                xRealIp
            );

        return new AdminLoginMetadata(
            clientIp,
            remoteAddress,
            xForwardedFor,
            xRealIp,
            cleanValue(
                request.getHeaders().getFirst(
                    "User-Agent"
                )
            )
        );
    }

    /**
     * 从 WebFlux ServerHttpRequest 获取真实连接地址。
     *
     * 本地 IPv6 回环地址后续会统一规范为 ::1。
     */
    private String remoteAddress(
        ServerHttpRequest request
    ) {
        if (
            request == null ||
                request.getRemoteAddress() == null
        ) {
            return "";
        }

        InetSocketAddress address =
            request.getRemoteAddress();

        if (address.getAddress() != null) {
            return cleanValue(
                address
                    .getAddress()
                    .getHostAddress()
            );
        }

        return cleanValue(
            address.getHostString()
        );
    }

    /**
     * 清除请求头中的换行符与首尾空白，防止日志/响应头注入并统一存储格式。
     */
    private String cleanValue(
        String value
    ) {
        if (value == null) {
            return "";
        }

        return value
            .replace("\r", "")
            .replace("\n", "")
            .trim();
    }

}
