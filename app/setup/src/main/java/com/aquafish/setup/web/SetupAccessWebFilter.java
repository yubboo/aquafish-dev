package com.aquafish.setup.web;

import com.aquafish.core.install.AuthoritativeInstallStatus;
import com.aquafish.core.install.AuthoritativeInstallStatusService;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 安装接口总闸门。只有数据库 INSTALLED 可以锁定安装接口。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SetupAccessWebFilter implements WebFilter {

    private static final String SETUP_PREFIX = "/api/setup/";
    private static final String STATUS_PATH = "/api/setup/status";
    private static final String SETUP_PAGE_PATH = "/setup";
    private static final Set<String> PUBLIC_ENTRY_PATHS =
        Set.of("/", "/site");

    /*
     * 这两个维护入口不能先经过通用安装状态总闸门：
     *
     * 1. 已安装实例恢复本来就要求目标数据库是 INSTALLED；
     * 2. 危险重装本来就要求目标数据库是 EXISTING_INSTALLED
     *    或 INCOMPLETE_INSTALLATION。
     *
     * 若先用当前 application.yaml 对应的权威状态拦截，
     * 分发安装临时选择的数据库会在进入各自服务前被错误关闭。
     *
     * 两个接口自身都会重新读取请求中的目标数据库并执行严格校验，
     * 因而这里只绕过“通用状态判断”，不会绕过各自的安全边界。
     */
    private static final String EXISTING_RECOVERY_PATH =
        "/api/setup/recovery/existing";

    private static final String DATABASE_RESET_PATH =
        "/api/setup/database/reset";

    /*
     * 开发重装验收只提前放行“准备阶段”接口。
     * 配置写入、迁移、管理员创建和 finish 仍由正常安装状态总闸门保护。
     */
    private static final String DEVELOPMENT_PROFILE = "dev";
    private static final String MAINTENANCE_HEADER =
        "X-Aquafish-Setup-Maintenance";
    private static final String REINSTALL_MAINTENANCE = "reinstall";
    private static final String CONTEXT_PATH = "/api/setup/context";
    private static final Set<String> DEVELOPMENT_REINSTALL_POST_PATHS =
        Set.of(
            "/api/setup/database/test",
            "/api/setup/database/managed/test",
            "/api/setup/database/inspect",
            "/api/setup/database/managed/inspect",
            "/api/setup/redis/test",
            "/api/setup/redis/managed/test"
        );

    private final AuthoritativeInstallStatusService statusService;
    private final boolean developmentProfileActive;

    @Autowired
    public SetupAccessWebFilter(
        AuthoritativeInstallStatusService statusService,
        Environment environment
    ) {
        this.statusService = statusService;
        this.developmentProfileActive = Arrays
            .asList(environment.getActiveProfiles())
            .contains(DEVELOPMENT_PROFILE);
    }

    /**
     * 保留给同包单元测试的兼容构造器；默认不开启开发维护模式。
     */
    SetupAccessWebFilter(
        AuthoritativeInstallStatusService statusService
    ) {
        this.statusService = statusService;
        this.developmentProfileActive = false;
    }

    /**
     * 在请求到达安装 Controller 之前统一判断安装入口是否仍可使用。
     *
     * <p>状态查询接口和跨域预检始终放行；未安装且权威状态可用时放行安装写操作；
     * 已安装时返回 {@code SETUP_LOCKED}，状态无法确认时采取失败关闭策略并返回
     * {@code SETUP_STATE_UNAVAILABLE}。该过滤器与前端 setup 路由守卫共同工作，
     * 但后端判断才是防止绕过页面直接重复安装的最终安全边界。</p>
     *
     * @param exchange 当前 WebFlux 请求与响应上下文
     * @param chain 后续过滤器和 Controller 调用链
     * @return 请求放行或已写入拒绝响应后的完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        /*
         * 首次安装前内容表尚未创建，根路径不能继续进入 CMS/主题渲染链。
         * 浏览器访问站点入口时先读取权威安装状态，未安装则统一跳转安装向导。
         */
        if (isPublicEntryRequest(exchange, path)) {
            return routePublicEntry(exchange, chain);
        }

        if (!path.startsWith(SETUP_PREFIX)
            || STATUS_PATH.equals(path)
            || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())
            || isSelfVerifyingMaintenanceRequest(exchange, path)
            || isDevelopmentReinstallPreparationRequest(exchange, path)) {
            return chain.filter(exchange);
        }

        return statusService.current()
            .flatMap(status ->
                routeByStatus(
                    exchange,
                    chain,
                    status
                )
            )
            .onErrorResume(error ->
                stateUnavailable(exchange)
            );
    }

    /**
     * 只处理浏览器可直接进入的两个公开首页地址，不影响 API、静态资源和非 GET 请求。
     */
    private boolean isPublicEntryRequest(
        ServerWebExchange exchange,
        String path
    ) {
        return HttpMethod.GET.equals(exchange.getRequest().getMethod())
            && PUBLIC_ENTRY_PATHS.contains(path);
    }

    /**
     * 未安装时把公开首页交给安装向导；完成安装后保持原有前台渲染流程。
     */
    private Mono<Void> routePublicEntry(
        ServerWebExchange exchange,
        WebFilterChain chain
    ) {
        return statusService.current()
            .flatMap(status ->
                status.installed()
                    ? chain.filter(exchange)
                    : redirectToSetup(exchange)
            )
            /*
             * 安装状态服务已经负责把数据库不可用转换为安全状态。
             * 这里保留最后一道失败保护，避免首次访问再次落入内容表查询并显示 500。
             */
            .onErrorResume(error -> redirectToSetup(exchange));
    }

    private Mono<Void> redirectToSetup(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(SETUP_PAGE_PATH));
        return response.setComplete();
    }

    /**
     * 已有实例恢复和数据库重装使用请求中的目标数据库，
     * 并由各自服务在写操作前重新执行完整校验。
     *
     * <p>只允许精确 POST 路径绕过通用总闸门；其他安装写接口仍保持
     * INSTALLED 锁定和状态不可用时失败关闭。</p>
     */
    private boolean isSelfVerifyingMaintenanceRequest(
        ServerWebExchange exchange,
        String path
    ) {
        if (
            !HttpMethod.POST.equals(
                exchange.getRequest().getMethod()
            )
        ) {
            return false;
        }

        return EXISTING_RECOVERY_PATH.equals(path)
            || DATABASE_RESET_PATH.equals(path);
    }

    /**
     * 仅在 Spring dev profile、本机回环来源和精确维护请求头同时满足时，
     * 放行开发重装验收所需的上下文、数据库检测和 Redis 检测接口。
     *
     * <p>这里不放行 config/write、迁移、finish 等正式安装写接口。危险 reset
     * 仍由重装服务自身的数据库状态、确认词和白名单校验负责。</p>
     */
    private boolean isDevelopmentReinstallPreparationRequest(
        ServerWebExchange exchange,
        String path
    ) {
        if (!developmentProfileActive) {
            return false;
        }

        if (
            !REINSTALL_MAINTENANCE.equals(
                exchange
                    .getRequest()
                    .getHeaders()
                    .getFirst(MAINTENANCE_HEADER)
            )
        ) {
            return false;
        }

        InetSocketAddress remoteAddress =
            exchange.getRequest().getRemoteAddress();

        if (
            remoteAddress == null
            || remoteAddress.getAddress() == null
            || !remoteAddress.getAddress().isLoopbackAddress()
        ) {
            return false;
        }

        if (CONTEXT_PATH.equals(path)) {
            return HttpMethod.GET.equals(
                exchange.getRequest().getMethod()
            );
        }

        return HttpMethod.POST.equals(
            exchange.getRequest().getMethod()
        ) && DEVELOPMENT_REINSTALL_POST_PATHS.contains(path);
    }

    /**
     * 根据数据库中的权威安装状态选择放行、锁定或安全关闭。
     */
    private Mono<Void> routeByStatus(
        ServerWebExchange exchange,
        WebFilterChain chain,
        AuthoritativeInstallStatus status
    ) {
        if (status.installed()) {
            return locked(exchange);
        }

        if (!status.stateAvailable() || !status.canInstall()) {
            return stateUnavailable(exchange);
        }

        return chain.filter(exchange);
    }

    /**
     * 系统已安装时返回 409，永久阻止安装写接口再次执行。
     */
    private Mono<Void> locked(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.CONFLICT);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes = ("{"
            + "\"success\":false,"
            + "\"code\":\"SETUP_LOCKED\","
            + "\"message\":\"系统已经完成安装，安装接口已锁定。\","
            + "\"data\":null"
            + "}").getBytes(StandardCharsets.UTF_8);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    /**
     * 权威状态无法读取时返回 503；宁可暂时停止安装，也不在未知状态下写数据。
     */
    private Mono<Void> stateUnavailable(
        ServerWebExchange exchange
    ) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes = ("{"
            + "\"success\":false,"
            + "\"code\":\"SETUP_STATE_UNAVAILABLE\","
            + "\"message\":\"数据库安装状态暂时不可用，安装写接口已安全关闭。\","
            + "\"data\":null"
            + "}").getBytes(StandardCharsets.UTF_8);

        return response.writeWith(
            Mono.just(
                response.bufferFactory().wrap(bytes)
            )
        );
    }
}
