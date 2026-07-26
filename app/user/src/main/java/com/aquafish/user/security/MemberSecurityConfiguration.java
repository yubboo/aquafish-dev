package com.aquafish.user.security;

import com.aquafish.common.net.TrustedProxyClientIpResolver;
import com.aquafish.user.auth.MemberAuthService;
import com.aquafish.user.auth.MemberAuthUser;
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
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 前台会员与论坛 API 的统一 Spring Security 边界。
 *
 * <p>GET 论坛列表允许匿名进入领域可见性检查；会员资料和论坛写请求必须完成
 * 数据库会话认证。所有写请求继续要求独立 CSRF Cookie/请求头，前端隐藏按钮
 * 不作为权限控制。</p>
 */
@Configuration(proxyBeanMethods = false)
public class MemberSecurityConfiguration {

    public static final String SESSION_COOKIE = "AQUAFISH_MEMBER_SESSION";
    public static final String CSRF_COOKIE = "AQUAFISH_MEMBER_XSRF";
    public static final String CSRF_HEADER = "X-AQUAFISH-CSRF";

    @Bean
    @Order(2)
    SecurityWebFilterChain memberSecurityWebFilterChain(
        ServerHttpSecurity http,
        MemberAuthService authService,
        TrustedProxyClientIpResolver
            trustedProxyClientIpResolver
    ) {
        ReactiveAuthenticationManager memberAuthenticationManager =
            authentication -> authenticate(authService, authentication);
        AuthenticationWebFilter authenticationFilter =
            new AuthenticationWebFilter(memberAuthenticationManager);
        authenticationFilter.setServerAuthenticationConverter(
            memberAuthenticationConverter()
        );
        authenticationFilter.setRequiresAuthenticationMatcher(
            authenticationRequiredMatcher()
        );
        authenticationFilter.setAuthenticationFailureHandler((webFilterExchange, error) -> {
            ServerWebExchange exchange = webFilterExchange.getExchange();
            exchange.getResponse().addCookie(
                expiredSessionCookie(
                    exchange,
                    trustedProxyClientIpResolver
                )
            );
            return jsonError(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "MEMBER_AUTH_UNAUTHORIZED",
                "会员登录状态已失效，请重新登录。"
            );
        });

        CookieServerCsrfTokenRepository csrfRepository =
            CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieName(CSRF_COOKIE);
        csrfRepository.setHeaderName(CSRF_HEADER);
        csrfRepository.setCookiePath("/");

        return http
            .securityMatcher(new OrServerWebExchangeMatcher(
                new PathPatternParserServerWebExchangeMatcher("/api/member/**"),
                new PathPatternParserServerWebExchangeMatcher("/api/forum/**")
            ))
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                .pathMatchers("/api/member/auth/csrf").permitAll()
                .pathMatchers("/api/member/auth/login").permitAll()
                .pathMatchers("/api/member/auth/register").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/forum/**").permitAll()
                .anyExchange().authenticated())
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                .accessDeniedHandler((exchange, error) -> jsonError(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    "MEMBER_CSRF_INVALID",
                    "请求缺少有效安全令牌，请刷新页面后重试。"
                )))
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((exchange, error) -> jsonError(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "MEMBER_AUTH_UNAUTHORIZED",
                    "该操作需要先登录会员账号。"
                ))
                .accessDeniedHandler((exchange, error) -> jsonError(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    "MEMBER_AUTH_FORBIDDEN",
                    "当前会员没有执行该操作的权限。"
                )))
            .addFilterAt(authenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }

    ServerAuthenticationConverter memberAuthenticationConverter() {
        return exchange -> {
            String token = MemberSessionTokenResolver.resolve(exchange.getRequest());
            if (token == null) {
                return Mono.empty();
            }
            return Mono.just(new UsernamePasswordAuthenticationToken(token, token));
        };
    }

    private Mono<Authentication> authenticate(
        MemberAuthService authService,
        Authentication authentication
    ) {
        String token = authentication == null
            ? null
            : String.valueOf(authentication.getCredentials());
        if (token == null || token.isBlank()) {
            return Mono.error(new BadCredentialsException("前台会员会话令牌为空"));
        }

        return authService.authenticate(token)
            .map(user -> UsernamePasswordAuthenticationToken.authenticated(
                user,
                token,
                authorities(user)
            ))
            .cast(Authentication.class)
            .switchIfEmpty(Mono.error(new BadCredentialsException("前台会员会话已失效")))
            .onErrorMap(error -> error instanceof BadCredentialsException
                ? error
                : new BadCredentialsException("前台会员会话校验失败", error)
            );
    }

    /**
     * CSRF 获取和登录是恢复会话的入口，残留旧 Cookie 不能阻止这两个请求。
     */
    private ServerWebExchangeMatcher authenticationRequiredMatcher() {
        return exchange -> {
            String path = exchange.getRequest().getPath().value();
            HttpMethod method = exchange.getRequest().getMethod();
            if (HttpMethod.OPTIONS.equals(method)
                || "/api/member/auth/csrf".equals(path)
                || "/api/member/auth/login".equals(path)
                || "/api/member/auth/register".equals(path)) {
                return ServerWebExchangeMatcher.MatchResult.notMatch();
            }
            return ServerWebExchangeMatcher.MatchResult.match();
        };
    }

    private List<SimpleGrantedAuthority> authorities(MemberAuthUser user) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_MEMBER"));
        user.permissions().stream()
            .map(SimpleGrantedAuthority::new)
            .forEach(authorities::add);
        return List.copyOf(authorities);
    }

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

    private Mono<Void> jsonError(
        ServerWebExchange exchange,
        HttpStatus status,
        String code,
        String message
    ) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        String body = "{\"success\":false,\"code\":\"" + code
            + "\",\"message\":\"" + message + "\",\"data\":null}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(
            exchange.getResponse().bufferFactory().wrap(bytes)
        ));
    }
}
