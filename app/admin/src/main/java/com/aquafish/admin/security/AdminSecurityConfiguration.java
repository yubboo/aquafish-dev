package com.aquafish.admin.security;

import com.aquafish.common.net.TrustedProxyClientIpResolver;
import com.aquafish.core.admin.auth.AdminAuthService;
import com.aquafish.core.admin.auth.AdminAuthUser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 后台接口统一安全边界。
 */
@Configuration(proxyBeanMethods = false)
public class AdminSecurityConfiguration {

    public static final String SESSION_COOKIE = "AQUAFISH_ADMIN_SESSION";

    @Bean
    @Order(1)
    SecurityWebFilterChain adminSecurityWebFilterChain(
        ServerHttpSecurity http,
        AdminAuthService authService,
        TrustedProxyClientIpResolver
            trustedProxyClientIpResolver
    ) {
        ReactiveAuthenticationManager adminAuthenticationManager =
            authentication -> authenticate(authService, authentication);
        AuthenticationWebFilter authenticationFilter =
            new AuthenticationWebFilter(adminAuthenticationManager);
        authenticationFilter.setServerAuthenticationConverter(authenticationConverter());
        // 登录页获取 CSRF 令牌时不能被浏览器中残留的旧会话阻断，否则用户连重新登录都无法进行。
        authenticationFilter.setRequiresAuthenticationMatcher(authenticationRequiredMatcher());
        // 已失效的会话属于正常的未登录状态，应返回 401 并清理 Cookie，而不是冒泡成 500。
        authenticationFilter.setAuthenticationFailureHandler((webFilterExchange, error) -> {
            ServerWebExchange exchange = webFilterExchange.getExchange();
            exchange.getResponse().addCookie(
                expiredSessionCookie(
                    exchange,
                    trustedProxyClientIpResolver
                )
            );
            return jsonError(
                exchange.getResponse(),
                HttpStatus.UNAUTHORIZED,
                "ADMIN_AUTH_UNAUTHORIZED",
                "登录状态已失效，请重新登录。"
            );
        });

        CookieServerCsrfTokenRepository csrfRepository =
            CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");

        return http
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/admin/**"))
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                .pathMatchers("/api/admin/auth/csrf").permitAll()
                .pathMatchers("/api/admin/auth/login").permitAll()
                .pathMatchers("/api/admin/auth/bridge").permitAll()
                .anyExchange().authenticated())
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                .accessDeniedHandler((exchange, error) -> jsonError(
                    exchange.getResponse(),
                    HttpStatus.FORBIDDEN,
                    "ADMIN_CSRF_INVALID",
                    "请求缺少有效安全令牌，请刷新页面后重试。"
                )))
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((exchange, error) -> jsonError(
                    exchange.getResponse(),
                    HttpStatus.UNAUTHORIZED,
                    "ADMIN_AUTH_UNAUTHORIZED",
                    "未登录或登录已过期，请重新登录。"
                ))
                .accessDeniedHandler((exchange, error) -> jsonError(
                    exchange.getResponse(),
                    HttpStatus.FORBIDDEN,
                    "ADMIN_AUTH_FORBIDDEN",
                    "请求缺少有效安全令牌或没有操作权限。"
                )))
            .addFilterAt(authenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }

    @Bean
    ServerAuthenticationConverter authenticationConverter() {
        return exchange -> {
            String token = AdminSessionTokenResolver.resolve(exchange.getRequest());
            if (token == null) {
                return Mono.empty();
            }
            return Mono.just(new UsernamePasswordAuthenticationToken(token, token));
        };
    }

    private Mono<Authentication> authenticate(
        AdminAuthService authService,
        Authentication authentication
    ) {
        String token = authentication == null ? null : String.valueOf(authentication.getCredentials());
        if (token == null || token.isBlank()) {
            return Mono.error(new BadCredentialsException("后台会话令牌为空"));
        }

        return authService.me("Bearer " + token)
            .map(user -> UsernamePasswordAuthenticationToken.authenticated(
                user,
                token,
                authorities(user)
            ))
            .cast(Authentication.class)
            // Spring Security 7.1 不再把空认证结果当作普通失败；明确抛出认证异常才能稳定返回 401。
            .switchIfEmpty(Mono.error(new BadCredentialsException("后台会话已失效")))
            .onErrorMap(
                error -> error instanceof BadCredentialsException
                    ? error
                    : new BadCredentialsException("后台会话校验失败", error)
            );
    }

    /**
     * 仅让需要登录态的后台请求尝试会话认证。
     *
     * <p>CSRF 与登录接口本身就是恢复登录态的入口，即使请求携带旧 Cookie 也必须保持可访问。</p>
     */
    private ServerWebExchangeMatcher authenticationRequiredMatcher() {
        return exchange -> {
            String path = exchange.getRequest().getPath().value();
            HttpMethod method = exchange.getRequest().getMethod();
            if (HttpMethod.OPTIONS.equals(method)
                || "/api/admin/auth/csrf".equals(path)
                || "/api/admin/auth/login".equals(path)
                || "/api/admin/auth/bridge".equals(path)) {
                return ServerWebExchangeMatcher.MatchResult.notMatch();
            }
            return ServerWebExchangeMatcher.MatchResult.match();
        };
    }

    /**
     * 生成立即过期的后台会话 Cookie，让浏览器自动丢弃跨重启遗留的无效令牌。
     */
    private ResponseCookie expiredSessionCookie(
        ServerWebExchange exchange,
        TrustedProxyClientIpResolver
            trustedProxyClientIpResolver
    ) {
        return ResponseCookie.from(
                SESSION_COOKIE,
                ""
            )
            .httpOnly(true)
            .secure(
                trustedProxyClientIpResolver
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
                    )
            )
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();
    }

    private List<SimpleGrantedAuthority> authorities(AdminAuthUser user) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        if (user.roles() != null) {
            user.roles().stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> "ROLE_" + role.trim().toUpperCase())
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        }
        return List.copyOf(authorities);
    }

    private Mono<Void> jsonError(
        org.springframework.http.server.reactive.ServerHttpResponse response,
        HttpStatus status,
        String code,
        String message
    ) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"success\":false,\"code\":\"" + code
            + "\",\"message\":\"" + message + "\",\"data\":null}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
