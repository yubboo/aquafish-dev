package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.install.SetupDatabaseInspection;
import com.aquafish.core.install.SetupDatabaseInspectionService;
import com.aquafish.core.install.SetupDeploymentContextService;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 安装向导数据库身份只读检测接口。
 */
@RestController
public final class SetupDatabaseInspectionController {

    private final SetupDatabaseInspectionService
        inspectionService;

    private final DatabaseRuntimeSettingsService
        settingsService;

    private final SetupDeploymentContextService
        contextService;

    public SetupDatabaseInspectionController(
        SetupDatabaseInspectionService
            inspectionService,
        DatabaseRuntimeSettingsService
            settingsService,
        SetupDeploymentContextService
            contextService
    ) {
        this.inspectionService =
            Objects.requireNonNull(
                inspectionService,
                "数据库识别服务不能为空。"
            );

        this.settingsService =
            Objects.requireNonNull(
                settingsService,
                "数据库运行配置服务不能为空。"
            );

        this.contextService =
            Objects.requireNonNull(
                contextService,
                "部署上下文服务不能为空。"
            );
    }

    /**
     * 检测分发包表单中的数据库。
     */
    @PostMapping(
        "/api/setup/database/inspect"
    )
    public Mono<ApiResult<SetupDatabaseInspection>>
        inspect(
            @RequestBody DatabaseSettings request
        ) {

        if (
            contextService.managedDatabase()
        ) {
            return Mono.just(
                ApiResult.fail(
                    "DATABASE_IS_MANAGED",
                    "当前数据库由部署平台管理。"
                )
            );
        }

        return inspectionService
            .inspect(request)
            .map(
                result ->
                    ApiResult.ok(
                        result,
                        "数据库身份检测完成"
                    )
            );
    }

    /**
     * 检测 Docker 或 1Panel 注入的数据库。
     */
    @PostMapping(
        "/api/setup/database/managed/inspect"
    )
    public Mono<ApiResult<SetupDatabaseInspection>>
        inspectManaged() {

        if (
            !contextService.managedDatabase()
        ) {
            return Mono.just(
                ApiResult.fail(
                    "DATABASE_NOT_MANAGED",
                    "当前是分发包安装模式。"
                )
            );
        }

        return inspectionService
            .inspect(
                settingsService.current()
            )
            .map(
                result ->
                    ApiResult.ok(
                        result,
                        "平台数据库身份检测完成"
                    )
            );
    }
}
