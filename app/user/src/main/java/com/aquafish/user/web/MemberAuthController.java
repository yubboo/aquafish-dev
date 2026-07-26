package com.aquafish.user.web;

import com.aquafish.common.net.TrustedProxyClientIpResolver;
import com.aquafish.common.web.ApiResult;
import com.aquafish.user.auth.MemberAuthService;
import com.aquafish.user.auth.MemberAuthToken;
import com.aquafish.user.auth.MemberAuthUser;
import com.aquafish.user.auth.MemberLoginMetadata;
import com.aquafish.user.auth.MemberLoginRequest;
import com.aquafish.user.auth.MemberLogoutResult;
import com.aquafish.user.security.MemberLoginRateLimitException;
import com.aquafish.user.security.MemberLoginRateLimiter;
import com.aquafish.user.security.MemberSecurityConfiguration;
import com.aquafish.user.security.MemberSessionTokenResolver;
import com.aquafish.user.security.IpAccessBannedException;
import com.aquafish.user.security.IpBanLookupService;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 前台会员登录、会话查询和退出接口。
 *
 * <p>登录成功只写入 HttpOnly Cookie，JSON 不返回原始令牌；
 * 所有响应设置 no-store，避免浏览器或代理缓存认证数据。</p>
 */
@RestController
public class MemberAuthController {

    private final MemberAuthService authService;
    private final MemberLoginRateLimiter rateLimiter;
    private final IpBanLookupService ipBanLookupService;

    private final TrustedProxyClientIpResolver
        trustedProxyClientIpResolver;

    public MemberAuthController(
        MemberAuthService authService,
        MemberLoginRateLimiter rateLimiter,
        IpBanLookupService ipBanLookupService,
        TrustedProxyClientIpResolver
            trustedProxyClientIpResolver
    ) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.ipBanLookupService = ipBanLookupService;
        this.trustedProxyClientIpResolver =
            trustedProxyClientIpResolver;
    }

    @PostMapping("/api/member/auth/login")
    public Mono<ResponseEntity<ApiResult<MemberLoginResponse>>> login(
        @RequestBody MemberLoginRequest request,
        ServerWebExchange exchange
    ) {
        noStore(exchange);
        MemberLoginMetadata metadata = metadata(exchange.getRequest());
        String loginName = request == null ? "" : request.normalized().loginName();

        return ipBanLookupService.requireAllowed(metadata.ipAddress(), "login")
            .then(Mono.defer(() -> {
                    rateLimiter.requireAllowed(metadata.ipAddress(), loginName);
                    return authService.login(request, metadata);
                }))
            .doOnSuccess(token ->
                rateLimiter.recordSuccess(metadata.ipAddress(), loginName)
            )
            .doOnError(error -> {
                if (!(error instanceof MemberLoginRateLimitException)) {
                    rateLimiter.recordFailure(metadata.ipAddress(), loginName);
                }
            })
            .map(token -> {
                exchange.getResponse().addCookie(sessionCookie(token, exchange));
                return ResponseEntity.ok(ApiResult.ok(
                    new MemberLoginResponse(
                        token.expiresAt(),
                        token.expiresInSeconds(),
                        token.user()
                    ),
                    "会员登录成功"
                ));
            })
            .onErrorResume(MemberLoginRateLimitException.class, error ->
                Mono.just(ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(error.retryAfterSeconds()))
                    .body(ApiResult.fail(
                        "MEMBER_AUTH_RATE_LIMITED",
                        error.getMessage()
                    )))
            )
            .onErrorResume(IpAccessBannedException.class, error ->
                Mono.just(ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(ApiResult.fail(
                        "MEMBER_AUTH_IP_BANNED",
                        error.getMessage()
                    )))
            )
            .onErrorResume(IllegalStateException.class, error ->
                Mono.just(ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResult.fail(
                        "MEMBER_AUTH_LOGIN_FAILED",
                        error.getMessage()
                    )))
            );
    }

    @GetMapping("/api/member/auth/me")
    public ResponseEntity<ApiResult<MemberAuthUser>> me(
        Authentication authentication
    ) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(ApiResult.ok(
                authenticatedUser(authentication),
                "当前会员获取成功"
            ));
    }

    @GetMapping("/api/member/auth/csrf")
    public Mono<ResponseEntity<ApiResult<Map<String, String>>>> csrf(
        ServerWebExchange exchange
    ) {
        noStore(exchange);
        Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            return Mono.error(new IllegalStateException("安全令牌尚未生成。"));
        }
        return token.map(value -> ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(ApiResult.ok(
                Map.of(
                    "token", value.getToken(),
                    "headerName", value.getHeaderName()
                ),
                "会员安全令牌获取成功"
            )));
    }

    @PostMapping("/api/member/auth/logout")
    public Mono<ResponseEntity<ApiResult<MemberLogoutResult>>> logout(
        ServerWebExchange exchange
    ) {
        noStore(exchange);
        String token = MemberSessionTokenResolver.resolve(exchange.getRequest());
        exchange.getResponse().addCookie(expiredSessionCookie(exchange));
        return authService.logout(token)
            .map(result -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResult.ok(result, "会员退出登录完成")));
    }

    private MemberAuthUser authenticatedUser(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof MemberAuthUser user)) {
            throw new IllegalStateException("会员登录已过期，请重新登录。");
        }
        return user;
    }

    private ResponseCookie sessionCookie(
        MemberAuthToken token,
        ServerWebExchange exchange
    ) {
        return ResponseCookie.from(
                MemberSecurityConfiguration.SESSION_COOKIE,
                token.accessToken()
            )
            .httpOnly(true)
            .secure(isSecure(exchange))
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofSeconds(token.expiresInSeconds()))
            .build();
    }

    private ResponseCookie expiredSessionCookie(ServerWebExchange exchange) {
        return ResponseCookie.from(MemberSecurityConfiguration.SESSION_COOKIE, "")
            .httpOnly(true)
            .secure(isSecure(exchange))
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();
    }

    MemberLoginMetadata metadata(
        ServerHttpRequest request
    ) {
        if (
            request == null ||
            request.getRemoteAddress() == null
        ) {
            return MemberLoginMetadata.empty();
        }

        String remoteAddress =
            request.getRemoteAddress().getAddress() == null
                ? request
                    .getRemoteAddress()
                    .getHostString()
                : request
                    .getRemoteAddress()
                    .getAddress()
                    .getHostAddress();

        String clientIp =
            trustedProxyClientIpResolver.resolve(
                remoteAddress,
                request
                    .getHeaders()
                    .getFirst("X-Forwarded-For"),
                request
                    .getHeaders()
                    .getFirst("X-Real-IP")
            );

        return new MemberLoginMetadata(
            clientIp,
            request
                .getHeaders()
                .getFirst("User-Agent")
        ).normalized();
    }

    private boolean isSecure(
        ServerWebExchange exchange
    ) {
        return trustedProxyClientIpResolver
            .isSecureRequest(
                exchange
                    .getRequest()
                    .getURI()
                    .getScheme(),
                TrustedProxyClientIpResolver
                    .normalizeRemoteAddress(
                        exchange
                            .getRequest()
                            .getRemoteAddress()
                    ),
                exchange
                    .getRequest()
                    .getHeaders()
                    .getFirst(
                        "X-Forwarded-Proto"
                    )
            );
    }

    private void noStore(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().setCacheControl("no-store");
    }

    /**
     * 登录响应只含非敏感会话摘要，不包含 accessToken。
     */
    public record MemberLoginResponse(
        String expiresAt,
        long expiresInSeconds,
        MemberAuthUser user
    ) {
    }
}
