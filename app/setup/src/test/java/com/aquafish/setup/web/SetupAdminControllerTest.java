package com.aquafish.setup.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquafish.core.install.SetupAdminAccountInitializer;
import com.aquafish.core.install.SetupAdminAccountRequest;
import com.aquafish.core.install.SetupAdminCreateResult;
import com.aquafish.core.install.SetupAdminPreview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 安装管理员响应式接口测试。
 */
class SetupAdminControllerTest {

    private SetupAdminAccountInitializer initializer;

    private SetupAdminController controller;

    private SetupAdminAccountRequest request;

    @BeforeEach
    void setUp() {
        initializer =
            mock(
                SetupAdminAccountInitializer.class
            );
        controller =
            new SetupAdminController(initializer);
        request =
            new SetupAdminAccountRequest(
                "admin",
                "admin@example.com",
                "AquaFish-2026!",
                "超级管理员"
            );
    }

    @Test
    void previewShouldRemainLazy() {
        Mono<?> response =
            controller.preview(request);

        verifyNoInteractions(initializer);

        SetupAdminPreview preview =
            preview(true);

        when(initializer.preview(request))
            .thenReturn(Mono.just(preview));

        StepVerifier.create(response)
            .assertNext(result ->
                assertTrue(
                    ((com.aquafish.common.web.ApiResult<?>)
                        result).success()
                )
            )
            .verifyComplete();
    }

    @Test
    void previewShouldReturnBusinessFailure() {
        SetupAdminPreview preview =
            preview(false);

        when(initializer.preview(request))
            .thenReturn(Mono.just(preview));

        StepVerifier.create(
            controller.preview(request)
        ).assertNext(result -> {
            assertFalse(result.success());
            assertEquals(
                "SETUP_ADMIN_PREVIEW_FAILED",
                result.code()
            );
            assertSame(preview, result.data());
        }).verifyComplete();
    }

    @Test
    void createShouldReturnSuccessfulResult() {
        SetupAdminCreateResult created =
            new SetupAdminCreateResult(
                false,
                true,
                9L,
                "admin",
                "admin@example.com",
                "超级管理员",
                "super_admin",
                "创建完成"
            );

        when(initializer.create(request))
            .thenReturn(Mono.just(created));

        StepVerifier.create(
            controller.create(request)
        ).assertNext(result -> {
            assertTrue(result.success());
            assertSame(created, result.data());
        }).verifyComplete();
    }

    @Test
    void createShouldConvertSafeFailure() {
        when(initializer.create(request))
            .thenReturn(
                Mono.error(
                    new IllegalStateException(
                        "管理员用户名已经存在。"
                    )
                )
            );

        StepVerifier.create(
            controller.create(request)
        ).assertNext(result -> {
            assertFalse(result.success());
            assertEquals(
                "SETUP_ADMIN_CREATE_FAILED",
                result.code()
            );
            assertTrue(
                result.message()
                    .contains("用户名")
            );
        }).verifyComplete();
    }

    private SetupAdminPreview preview(
        boolean canCreate
    ) {
        return new SetupAdminPreview(
            false,
            true,
            true,
            !canCreate,
            canCreate,
            "admin",
            "admin@example.com",
            "aq_users",
            "aq_roles",
            "aq_user_roles",
            canCreate
                ? "可以创建超级管理员账号。"
                : "已经存在超级管理员。",
            null
        );
    }
}
