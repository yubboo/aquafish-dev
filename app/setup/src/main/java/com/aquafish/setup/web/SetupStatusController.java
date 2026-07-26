package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.install.AuthoritativeInstallStatusService;
import com.aquafish.core.install.InstallEnvironmentInfo;
import com.aquafish.core.install.InstallLockService;
import com.aquafish.core.install.SetupDeploymentContext;
import com.aquafish.core.install.SetupDeploymentContextService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 安装状态接口。
 *
 * 当前阶段：
 * Step 17-22-2：安装状态与 install.lock。
 *
 * 接口：
 * GET /api/setup/status
 * GET /api/setup/environment
 *
 * 说明：
 * 1. /api/setup/status 只根据数据库判断系统是否已安装；
 * 2. /api/setup/environment 用于安装向导环境检查；
 * 3. install.lock 只保留兼容展示和自动恢复用途。
 */
@RestController
public class SetupStatusController {

    private final InstallLockService installLockService;
    private final AuthoritativeInstallStatusService statusService;

    private final SetupDeploymentContextService deploymentContextService;

    public SetupStatusController(
        InstallLockService installLockService,
        AuthoritativeInstallStatusService statusService,
        SetupDeploymentContextService deploymentContextService
    ) {
        this.installLockService = installLockService;
        this.statusService = statusService;
        this.deploymentContextService = deploymentContextService;
    }

    /**
     * 获取安装状态。
     */
    @GetMapping("/api/setup/status")
    public Mono<ApiResult<PublicInstallStatus>> status() {
        return statusService.current()
            .map(status ->
                ApiResult.ok(
                    new PublicInstallStatus(
                        status.installed(),
                        status.locked(),
                        status.canInstall(),
                        status.stateAvailable(),
                        status.databaseState(),
                        status.applicationConfigExists(),
                        status.installedAt(),
                        status.safeMessage()
                    ),
                    "安装状态获取成功"
                )
            );
    }

    /**
     * 获取安装环境信息。
     */
    @GetMapping("/api/setup/environment")
    public Mono<ApiResult<InstallEnvironmentInfo>> environment() {
        return Mono.fromCallable(installLockService::environment)
            .subscribeOn(Schedulers.boundedElastic())
            .map(environment ->
                ApiResult.ok(
                    environment,
                    "安装环境获取成功"
                )
            );
    }

    /**
     * 获取部署平台声明的安装步骤能力。
     *
     * <p>该接口不返回数据库或 Redis 密码；前端必须依据此结果隐藏托管字段，
     * 不能通过查询参数自行切换完整安装与托管初始化。</p>
     */
    @GetMapping("/api/setup/context")
    public ApiResult<SetupDeploymentContext> context() {
        return ApiResult.ok(
            deploymentContextService.current(),
            "安装部署上下文获取成功"
        );
    }

    /**
     * 公开安装状态不包含服务器绝对路径、锁内容或其他环境细节。
     */
    public record PublicInstallStatus(
        boolean installed,
        boolean locked,
        boolean canInstall,
        boolean stateAvailable,
        String databaseState,
        boolean applicationConfigExists,
        String installedAt,
        String note
    ) {
    }
}
