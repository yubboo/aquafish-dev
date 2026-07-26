package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.install.SetupFinishPreview;
import com.aquafish.core.install.SetupFinishRequest;
import com.aquafish.core.install.SetupFinishResult;
import com.aquafish.core.install.SetupFinishService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 安装完成接口。
 *
 * 当前阶段：
 * Step 17-22-6：完成安装，写入 install.lock。
 *
 * 接口：
 * POST /api/setup/finish/preview
 * POST /api/setup/finish
 */
@RestController
public class SetupFinishController {

    private final SetupFinishService setupFinishService;

    public SetupFinishController(SetupFinishService setupFinishService) {
        this.setupFinishService = setupFinishService;
    }

    /**
     * 预览是否可以完成安装。
     */
    @PostMapping("/api/setup/finish/preview")
    public Mono<ApiResult<SetupFinishPreview>> preview() {
        return Mono.defer(setupFinishService::preview)
            .map(preview -> {
                if (!preview.canFinish()) {
                    return ApiResult.fail(
                        "SETUP_FINISH_PREVIEW_FAILED",
                        preview.note(),
                        preview
                    );
                }

                return ApiResult.ok(
                    preview,
                    "安装完成预览成功"
                );
            });
    }

    /**
     * 完成安装。
     */
    @PostMapping("/api/setup/finish")
    public Mono<ApiResult<SetupFinishResult>> finish(
        @RequestBody SetupFinishRequest request
    ) {
        return Mono.defer(
            () -> setupFinishService.finish(request)
        )
            .map(result ->
                ApiResult.ok(
                    result,
                    "Aquafish 安装完成"
                )
            )
            .onErrorResume(error ->
                Mono.just(
                    ApiResult.fail(
                        "SETUP_FINISH_FAILED",
                        safeMessage(error)
                    )
                )
            );
    }

    private String safeMessage(Throwable error) {
        if (
            error == null
                || error.getMessage() == null
                || error.getMessage().isBlank()
        ) {
            return "安装最终提交失败。";
        }

        return error.getMessage();
    }
}
