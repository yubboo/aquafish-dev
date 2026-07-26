package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.install.ApplicationConfigPreview;
import com.aquafish.core.install.ApplicationConfigWriteResult;
import com.aquafish.core.install.ApplicationConfigWriterService;
import com.aquafish.core.install.SetupApplicationConfigRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 安装配置写入接口。
 *
 * 当前阶段：
 * Step 17-22-3：安装配置写入 workdir/application.yaml。
 *
 * 接口：
 * POST /api/setup/config/preview
 * POST /api/setup/config/write
 *
 * 注意：
 * 这里不写 install.lock。
 * install.lock 必须等数据库初始化、管理员创建成功之后再写。
 */
@RestController
public class SetupConfigController {

    private final ApplicationConfigWriterService applicationConfigWriterService;

    public SetupConfigController(ApplicationConfigWriterService applicationConfigWriterService) {
        this.applicationConfigWriterService = applicationConfigWriterService;
    }

    /**
     * 预览将要写入的 application.yaml。
     */
    @PostMapping("/api/setup/config/preview")
    public Mono<ApiResult<ApplicationConfigPreview>> preview(
        @RequestBody SetupApplicationConfigRequest request
    ) {
        return applicationConfigWriterService.preview(request)
            .map(preview ->
                ApiResult.ok(
                    preview,
                    "安装配置预览生成成功"
                )
            );
    }

    /**
     * 写入 application.yaml。
     */
    @PostMapping("/api/setup/config/write")
    public Mono<ApiResult<ApplicationConfigWriteResult>> write(
        @RequestBody SetupApplicationConfigRequest request
    ) {
        return applicationConfigWriterService.write(request)
            .map(result ->
                ApiResult.ok(
                    result,
                    "安装配置写入成功"
                )
            )
            .onErrorResume(error ->
                Mono.just(
                    ApiResult.fail(
                        "SETUP_CONFIG_WRITE_FAILED",
                        error.getMessage()
                    )
                )
            );
    }
}
