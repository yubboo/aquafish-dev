package com.aquafish.boot.web;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Vue 后台单页应用的服务器端页面回退过滤器。
 *
 * <p>关联功能：</p>
 * <ol>
 *   <li>发行 JAR 内嵌的 {@code static/index.html}；</li>
 *   <li>首次安装页面 {@code /setup}；</li>
 *   <li>后台登录和所有 {@code /admin/**} 前端路由。</li>
 * </ol>
 *
 * <p>实现结果：浏览器直接访问或刷新 Vue history 路由时，后端把请求内部
 * 改写到 {@code /index.html}，浏览器地址保持不变，再由 Vue Router 决定显示
 * 安装页、登录页或后台页面。公开根路径由主题渲染控制器负责，不能再被后台 SPA
 * 抢占；API、静态资源和非 GET 请求不参与改写。</p>
 *
 * <p>开发阶段如果 boot classpath 中没有前端首页，过滤器自动停用，避免影响
 * Vite 独立开发服务器。</p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class SpaFallbackWebFilter implements WebFilter {

    private static final String SPA_INDEX_PATH = "/index.html";

    private final boolean spaIndexAvailable;

    public SpaFallbackWebFilter(
        @Value("classpath:/static/index.html") Resource spaIndexResource
    ) {
        this.spaIndexAvailable = Objects.requireNonNull(
            spaIndexResource,
            "后台前端首页资源不能为空。"
        ).exists();
    }

    /**
     * 把发行包中的前端页面路由内部改写到 {@code /index.html}。
     *
     * <p>仅处理符合 {@link #shouldForwardToSpa(ServerWebExchange)} 的 GET 页面请求；
     * API、静态资源和写请求继续沿原过滤器链执行。内部改写不会改变浏览器地址，
     * 因而 Vue Router 仍能根据原路径显示安装页、登录页或后台页面。</p>
     */
    @Override
    public Mono<Void> filter(
        ServerWebExchange exchange,
        WebFilterChain chain
    ) {
        if (!shouldForwardToSpa(exchange)) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest()
            .mutate()
            .path(SPA_INDEX_PATH)
            .build();

        return chain.filter(
            exchange.mutate().request(request).build()
        );
    }

    /**
     * 只处理发行包中真实存在前端首页时的页面 GET 请求。
     */
    private boolean shouldForwardToSpa(ServerWebExchange exchange) {
        if (!spaIndexAvailable) {
            return false;
        }

        if (!HttpMethod.GET.equals(exchange.getRequest().getMethod())) {
            return false;
        }

        String path = exchange.getRequest().getURI().getPath();

        return "/setup".equals(path)
            || "/admin".equals(path)
            || path.startsWith("/admin/");
    }
}
