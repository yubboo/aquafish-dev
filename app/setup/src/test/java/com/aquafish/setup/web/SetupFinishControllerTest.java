package com.aquafish.setup.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquafish.core.install.SetupAdminAccountRequest;
import com.aquafish.core.install.SetupFinishRequest;
import com.aquafish.core.install.SetupFinishResult;
import com.aquafish.core.install.SetupFinishService;
import com.aquafish.core.install.SiteSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 安装最终提交响应式接口测试。
 */
class SetupFinishControllerTest {

    private SetupFinishService service;
    private SetupFinishController controller;
    private SetupFinishRequest request;

    @BeforeEach
    void setUp() {
        service = mock(SetupFinishService.class);
        controller = new SetupFinishController(service);
        request = new SetupFinishRequest(
            new SetupAdminAccountRequest(
                "admin",
                "admin@example.com",
                "AquaFish-2026!",
                "超级管理员"
            ),
            SiteSettings.defaultSettings()
        );
    }

    @Test
    void finishShouldRemainLazyAndReturnResult() {
        SetupFinishResult finished = new SetupFinishResult(
            true,
            true,
            true,
            "workdir",
            "application.yaml",
            "install.lock",
            "2026-07-16T00:00:00Z",
            "完成"
        );

        Mono<?> response = controller.finish(request);
        verifyNoInteractions(service);
        when(service.finish(request)).thenReturn(Mono.just(finished));

        StepVerifier.create(response)
            .assertNext(value -> {
                com.aquafish.common.web.ApiResult<?> result =
                    (com.aquafish.common.web.ApiResult<?>) value;
                assertTrue(result.success());
                assertSame(finished, result.data());
            })
            .verifyComplete();
    }

    @Test
    void finishShouldConvertSafeFailure() {
        when(service.finish(request))
            .thenReturn(Mono.error(new IllegalStateException("事务已回滚")));

        StepVerifier.create(controller.finish(request))
            .assertNext(result -> {
                assertFalse(result.success());
                assertEquals("SETUP_FINISH_FAILED", result.code());
                assertTrue(result.message().contains("回滚"));
            })
            .verifyComplete();
    }
}
