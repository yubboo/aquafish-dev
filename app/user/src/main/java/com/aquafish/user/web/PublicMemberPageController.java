package com.aquafish.user.web;

import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.core.TemplateType;
import com.aquafish.template.core.TemplateTypes;
import com.aquafish.template.engine.DefaultTemplateRenderService;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 前台会员页面入口。
 *
 * <p>登录动作继续由 MemberAuthController 的安全 Cookie、CSRF 和限流链处理；
 * 本控制器只通过当前主题渲染登录表单，不复制认证业务。</p>
 */
@RestController
public class PublicMemberPageController {

    private final DefaultTemplateRenderService templateRenderService;
    private final PublicTemplateContextService templateContextService;

    public PublicMemberPageController(
        DefaultTemplateRenderService templateRenderService,
        PublicTemplateContextService templateContextService
    ) {
        this.templateRenderService = templateRenderService;
        this.templateContextService = templateContextService;
    }

    @GetMapping("/login")
    public Mono<ResponseEntity<String>> login(ServerWebExchange exchange) {
        return renderAuthenticationPage(
            exchange,
            TemplateTypes.LOGIN,
            "会员登录",
            "登录 Aquafish 会员账号"
        );
    }

    @GetMapping("/register")
    public Mono<ResponseEntity<String>> register(ServerWebExchange exchange) {
        return renderAuthenticationPage(
            exchange,
            TemplateTypes.REGISTER,
            "用户注册",
            "创建 Aquafish 会员账号"
        );
    }

    @GetMapping("/member")
    public Mono<ResponseEntity<String>> memberCenter(ServerWebExchange exchange) {
        return renderMemberPage(
            exchange,
            TemplateTypes.USER_HOME,
            "个人中心",
            "查看当前账号资料与 UID"
        );
    }

    private Mono<ResponseEntity<String>> renderMemberPage(
        ServerWebExchange exchange,
        TemplateType templateType,
        String title,
        String description
    ) {
        return templateContextService.create(exchange, title, description)
            .flatMap(model -> renderModel(templateType, title, model));
    }

    /**
     * 登录和注册页面只允许匿名访问。
     *
     * <p>服务端在输出 HTML 前确认 HttpOnly Cookie 对应的真实会员身份，避免已登录用户
     * 仍看到登录、注册表单。合法站内 redirect 仅在权限允许时保留；普通会员不能借此
     * 反复跳转到后台登录链路。</p>
     */
    private Mono<ResponseEntity<String>> renderAuthenticationPage(
        ServerWebExchange exchange,
        TemplateType templateType,
        String title,
        String description
    ) {
        return templateContextService.create(exchange, title, description)
            .flatMap(model -> {
                String redirect = authenticatedRedirect(exchange, model);
                if (redirect != null) {
                    return Mono.just(
                        ResponseEntity.status(HttpStatus.SEE_OTHER)
                            .location(URI.create(redirect))
                            .cacheControl(CacheControl.noStore())
                            .body("")
                    );
                }
                return renderModel(templateType, title, model);
            });
    }

    /** 使用已经构建完成的公共模型渲染主题，避免认证页重复查询登录状态。 */
    private Mono<ResponseEntity<String>> renderModel(
        TemplateType templateType,
        String title,
        Map<String, Object> model
    ) {
        return Mono.fromCallable(() -> templateRenderService.render(
                TemplateRenderRequest.of(templateType, model)
            ))
            .subscribeOn(Schedulers.boundedElastic())
            .map(result -> response(result, title));
    }

    /**
     * 已登录用户访问登录或注册页时计算安全跳转地址；匿名用户返回 {@code null}。
     */
    private String authenticatedRedirect(
        ServerWebExchange exchange,
        Map<String, Object> model
    ) {
        Object viewerValue = model == null ? null : model.get("viewer");
        if (!(viewerValue instanceof Map<?, ?> viewer)
            || !Boolean.TRUE.equals(viewer.get("authenticated"))) {
            return null;
        }

        String requested = exchange == null
            ? null
            : exchange.getRequest().getQueryParams().getFirst("redirect");
        boolean admin = Boolean.TRUE.equals(viewer.get("admin"));

        if (isSafeRedirect(requested)
            && (!requested.startsWith("/admin") || admin)) {
            return requested;
        }
        return "/member";
    }

    /** 只接受非认证页的站内绝对路径，拒绝协议相对地址和循环跳转。 */
    private boolean isSafeRedirect(String value) {
        return value != null
            && value.startsWith("/")
            && !value.startsWith("//")
            && !value.startsWith("/login")
            && !value.startsWith("/register");
    }

    private ResponseEntity<String> response(
        TemplateRenderResult result,
        String title
    ) {
        String html = result.success()
            ? result.html()
            : "<!doctype html><html lang=\"zh-CN\"><meta charset=\"UTF-8\">"
                + "<title>" + title + "</title><body><h1>" + title + "暂时不可用</h1>"
                + "<a href=\"/\">返回首页</a></body></html>";
        return ResponseEntity.status(
                result.success() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE
            )
            .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
            .cacheControl(CacheControl.noStore())
            .body(html);
    }
}
