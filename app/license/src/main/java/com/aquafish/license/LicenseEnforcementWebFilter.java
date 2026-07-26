package com.aquafish.license;

import com.aquafish.common.web.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 未授权实例的服务端强制访问边界。
 *
 * <p>前端路由跳转只改善体验，不能作为安全措施。未授权实例仍允许登录、查看控制台、
 * 用户与基础系统设置等普通能力；论坛、内容、主题、插件、市场、AI、搜索和更新等
 * 高级能力按 {@link LicenseFeature} 在服务端返回 HTTP 423/403。这样管理员能进入后台
 * 处理授权和维护站点，但不能通过直接调用 API 绕过商业模块限制。</p>
 */
@Component
public final class LicenseEnforcementWebFilter implements WebFilter, Ordered {

    private final LicenseService licenseService;
    private final ObjectMapper objectMapper;

    public LicenseEnforcementWebFilter(
        LicenseService licenseService,
        ObjectMapper objectMapper
    ) {
        this.licenseService = licenseService;
        this.objectMapper = objectMapper;
    }

    /**
     * 只对已登记的高级模块路径读取授权状态；未登记的基础后台接口直接放行。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (isAllowedWithoutLicense(exchange.getRequest().getMethod(), path)) {
            return chain.filter(exchange);
        }

        java.util.Optional<LicenseFeature> requiredFeature = LicenseFeature.requiredForApiPath(path);
        if (requiredFeature.isEmpty()) {
            return chain.filter(exchange);
        }

        return Mono.fromCallable(licenseService::status)
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(status -> {
                if (!status.usable()) {
                    return writeLockedResponse(exchange, status);
                }
                LicenseFeature feature = requiredFeature.orElseThrow();
                if (!licenseService.isFeatureUsable(status, feature)) {
                    return writeFeatureDeniedResponse(exchange, status, feature);
                }
                return chain.filter(exchange);
            });
    }

    /**
     * 在身份认证之后执行，确保匿名请求先得到 401，避免泄露实例授权状态。
     */
    @Override
    public int getOrder() {
        /*
         * 必须晚于 Spring Security：匿名访问后台接口应先得到 401，只有已经通过
         * 身份认证的管理员才会看到 423 授权状态，避免向未登录请求泄露实例信息。
         */
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    /** 判断安装、登录、授权、健康与预检请求是否可以在未激活时访问。 */
    private boolean isAllowedWithoutLicense(HttpMethod method, String path) {
        if (HttpMethod.OPTIONS.equals(method) || !path.startsWith("/api/")) {
            return true;
        }

        return path.startsWith("/api/setup/")
            || path.startsWith("/api/admin/auth/")
            || path.equals("/api/admin/license/status")
            || path.equals("/api/admin/license/activation")
            || path.equals("/api/admin/license/online/activation")
            || path.equals("/api/admin/license/online/refresh")
            || path.equals("/api/health")
            || path.startsWith("/actuator/");
    }

    /**
     * 写入模块授权不足响应。
     *
     * <p>HTTP 403 表示管理员已经登录、平台授权也有效，但当前授权版本不包含目标
     * 模块；它与整个平台未激活使用的 423 有明确区别，便于前端进入不同页面。</p>
     */
    private Mono<Void> writeFeatureDeniedResponse(
        ServerWebExchange exchange,
        LicenseStatusView status,
        LicenseFeature feature
    ) {
        LicenseFeatureDeniedView denied = new LicenseFeatureDeniedView(
            feature.code(),
            feature.label(),
            status.edition(),
            status.features()
        );
        String message = "当前授权不包含“" + feature.label() + "”模块。";
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(
                ApiResult.fail("LICENSE_FEATURE_REQUIRED", message, denied)
            );
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
            );
        } catch (Exception error) {
            byte[] bytes = ("{\"success\":false,\"code\":\"LICENSE_FEATURE_REQUIRED\","
                + "\"message\":\"当前授权不包含所需模块\",\"data\":null}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
            );
        }
    }

    /** 写入统一 LICENSE_REQUIRED 响应；序列化异常时使用最小 JSON 兜底。 */
    private Mono<Void> writeLockedResponse(
        ServerWebExchange exchange,
        LicenseStatusView status
    ) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(
                ApiResult.fail("LICENSE_REQUIRED", status.message(), status)
            );
            exchange.getResponse().setStatusCode(HttpStatus.LOCKED);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
            );
        } catch (Exception error) {
            byte[] bytes = "{\"success\":false,\"code\":\"LICENSE_REQUIRED\",\"message\":\"系统尚未激活\",\"data\":null}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponse().setStatusCode(HttpStatus.LOCKED);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
            );
        }
    }
}
