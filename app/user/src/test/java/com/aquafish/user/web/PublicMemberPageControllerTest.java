package com.aquafish.user.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquafish.template.engine.DefaultTemplateRenderService;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 前台登录、注册页面的登录态重定向测试。
 */
class PublicMemberPageControllerTest {

    private DefaultTemplateRenderService renderService;
    private PublicTemplateContextService contextService;
    private PublicMemberPageController controller;

    @BeforeEach
    void setUp() {
        renderService = mock(DefaultTemplateRenderService.class);
        contextService = mock(PublicTemplateContextService.class);
        controller = new PublicMemberPageController(renderService, contextService);
    }

    /** 已登录管理员可以沿安全 redirect 直接返回后台，不能再次看到登录表单。 */
    @Test
    void authenticatedAdminShouldRedirectFromLoginToRequestedAdminPage() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/login?redirect=/admin/users").build()
        );
        when(contextService.create(exchange, "会员登录", "登录 Aquafish 会员账号"))
            .thenReturn(Mono.just(Map.of(
                "viewer",
                Map.of("authenticated", true, "admin", true)
            )));

        StepVerifier.create(controller.login(exchange))
            .assertNext(response -> {
                assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
                assertEquals(URI.create("/admin/users"), response.getHeaders().getLocation());
            })
            .verifyComplete();

        verify(renderService, never()).render(org.mockito.ArgumentMatchers.any());
    }

    /** 普通会员不能被后台 redirect 带入循环，应回到个人中心。 */
    @Test
    void authenticatedMemberShouldRedirectFromRegisterToMemberCenter() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/register?redirect=/admin").build()
        );
        when(contextService.create(exchange, "用户注册", "创建 Aquafish 会员账号"))
            .thenReturn(Mono.just(Map.of(
                "viewer",
                Map.of("authenticated", true, "admin", false)
            )));

        StepVerifier.create(controller.register(exchange))
            .assertNext(response -> {
                assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
                assertEquals(URI.create("/member"), response.getHeaders().getLocation());
            })
            .verifyComplete();

        verify(renderService, never()).render(org.mockito.ArgumentMatchers.any());
    }
}
