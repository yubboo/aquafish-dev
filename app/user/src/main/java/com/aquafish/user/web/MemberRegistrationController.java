package com.aquafish.user.web;

import com.aquafish.common.net.TrustedProxyClientIpResolver;
import com.aquafish.common.web.ApiResult;
import com.aquafish.user.auth.MemberAuthService;
import com.aquafish.user.auth.MemberAuthToken;
import com.aquafish.user.auth.MemberLoginMetadata;
import com.aquafish.user.auth.MemberLoginRequest;
import com.aquafish.user.auth.MemberRegistrationException;
import com.aquafish.user.auth.MemberRegistrationRequest;
import com.aquafish.user.auth.MemberRegistrationService;
import com.aquafish.user.security.MemberLoginRateLimitException;
import com.aquafish.user.security.MemberLoginRateLimiter;
import com.aquafish.user.security.MemberSecurityConfiguration;
import com.aquafish.user.security.IpAccessBannedException;
import com.aquafish.user.security.IpBanLookupService;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 前台用户自主注册接口。
 *
 * <p>注册完成后立即使用同一账号创建数据库会员会话，并只通过 HttpOnly Cookie
 * 交付会话令牌。普通注册不会写入 admin 或 super_admin 角色。</p>
 */
@RestController
public class MemberRegistrationController {

    private final MemberRegistrationService registrationService;
    private final MemberAuthService authService;
    private final MemberLoginRateLimiter rateLimiter;
    private final IpBanLookupService ipBanLookupService;

    private final TrustedProxyClientIpResolver
        trustedProxyClientIpResolver;

    public MemberRegistrationController(
        MemberRegistrationService registrationService,
        MemberAuthService authService,
        MemberLoginRateLimiter rateLimiter,
        IpBanLookupService ipBanLookupService,
        TrustedProxyClientIpResolver
            trustedProxyClientIpResolver
    ) {
        this.registrationService =
            registrationService;
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.ipBanLookupService =
            ipBanLookupService;
        this.trustedProxyClientIpResolver =
            trustedProxyClientIpResolver;
    }

    @PostMapping("/api/member/auth/register")
    public Mono<ResponseEntity<ApiResult<MemberAuthController.MemberLoginResponse>>> register(
        @RequestBody MemberRegistrationRequest request,
        ServerWebExchange exchange
    ) {
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        MemberLoginMetadata metadata = metadata(exchange.getRequest());
        MemberRegistrationRequest normalized = request == null
            ? new MemberRegistrationRequest("", "", "", "", "", false)
            : request.normalized();
        String rateLimitKey = "register:" + normalized.username();

        return ipBanLookupService.requireAllowed(metadata.ipAddress(), "register")
            .then(Mono.defer(() -> {
                    rateLimiter.requireAllowed(metadata.ipAddress(), rateLimitKey);
                    return registrationService.register(normalized, metadata)
                        .then(authService.login(
                            new MemberLoginRequest(
                                normalized.username(),
                                normalized.password(),
                                false
                            ),
                            metadata
                        ));
                }))
            .doOnSuccess(token ->
                rateLimiter.recordSuccess(metadata.ipAddress(), rateLimitKey)
            )
            .doOnError(error -> {
                if (!(error instanceof MemberLoginRateLimitException)) {
                    rateLimiter.recordFailure(metadata.ipAddress(), rateLimitKey);
                }
            })
            .map(token -> success(token, exchange))
            .onErrorResume(MemberLoginRateLimitException.class, error ->
                Mono.just(ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(error.retryAfterSeconds()))
                    .body(ApiResult.fail(
                        "MEMBER_REGISTER_RATE_LIMITED",
                        "注册尝试过于频繁，请稍后重试。"
                    )))
            )
            .onErrorResume(IpAccessBannedException.class, error ->
                Mono.just(ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(ApiResult.fail(
                        "MEMBER_REGISTER_IP_BANNED",
                        error.getMessage()
                    )))
            )
            .onErrorResume(MemberRegistrationException.class, error ->
                Mono.just(ResponseEntity
                    .status(error.conflict()
                        ? HttpStatus.CONFLICT
                        : HttpStatus.BAD_REQUEST)
                    .body(ApiResult.fail(error.code(), error.getMessage())))
            )
            .onErrorResume(IllegalStateException.class, error ->
                Mono.just(ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResult.fail(
                        "MEMBER_REGISTER_FAILED",
                        "注册暂时不可用，请稍后重试。"
                    )))
            );
    }

    private ResponseEntity<ApiResult<MemberAuthController.MemberLoginResponse>> success(
        MemberAuthToken token,
        ServerWebExchange exchange
    ) {
        exchange.getResponse().addCookie(sessionCookie(token, exchange));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(
                new MemberAuthController.MemberLoginResponse(
                    token.expiresAt(),
                    token.expiresInSeconds(),
                    token.user()
                ),
                "注册成功"
            ));
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
}
