package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.install.SetupAdminAccountInitializer;
import com.aquafish.core.install.SetupAdminAccountRequest;
import com.aquafish.core.install.SetupAdminCreateResult;
import com.aquafish.core.install.SetupAdminPreview;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 安装向导管理员账号接口。
 *
 * 当前阶段：
 * Step 17-22-5：初始化管理员账号。
 *
 * 接口：
 * POST /api/setup/admin/preview
 * POST /api/setup/admin/create
 *
 * 注意：
 * 1. 这里只创建超级管理员；
 * 2. 不提交 INSTALLED 状态；
 * 3. 安装完成状态由后续完成接口统一提交。
 */
@RestController
public class SetupAdminController {

    private final SetupAdminAccountInitializer setupAdminAccountInitializer;

    public SetupAdminController(SetupAdminAccountInitializer setupAdminAccountInitializer) {
        this.setupAdminAccountInitializer = setupAdminAccountInitializer;
    }

    /**
     * 预览管理员账号是否可以创建。
     */
    @PostMapping("/api/setup/admin/preview")
    public Mono<ApiResult<SetupAdminPreview>> preview(
        @RequestBody SetupAdminAccountRequest request
    ) {
        return Mono.defer(
            () -> setupAdminAccountInitializer
                .preview(request)
        ).map(preview -> {
            if (!preview.canCreate()) {
                return ApiResult.fail(
                    "SETUP_ADMIN_PREVIEW_FAILED",
                    preview.note(),
                    preview
                );
            }

            return ApiResult.ok(
                preview,
                "管理员账号创建预览成功"
            );
        }).onErrorResume(
            error -> Mono.just(
                ApiResult.fail(
                    "SETUP_ADMIN_PREVIEW_FAILED",
                    safeMessage(
                        error,
                        "管理员创建预览失败。"
                    )
                )
            )
        );
    }

    /**
     * 创建超级管理员账号。
     */
    @PostMapping("/api/setup/admin/create")
    public Mono<ApiResult<SetupAdminCreateResult>> create(
        @RequestBody SetupAdminAccountRequest request
    ) {
        return Mono.defer(
            () -> setupAdminAccountInitializer
                .create(request)
        ).map(result ->
            ApiResult.ok(
                result,
                "超级管理员账号创建成功"
            )
        ).onErrorResume(
            error -> Mono.just(
                ApiResult.fail(
                    "SETUP_ADMIN_CREATE_FAILED",
                    safeMessage(
                        error,
                        "管理员账号创建失败。"
                    )
                )
            )
        );
    }

    private String safeMessage(
        Throwable error,
        String fallback
    ) {
        if (
            error == null
                || error.getMessage() == null
                || error.getMessage().isBlank()
        ) {
            return fallback;
        }

        return error.getMessage();
    }
}
