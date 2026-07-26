package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.install.SetupDatabaseResetRequest;
import com.aquafish.core.install.SetupDatabaseResetResult;
import com.aquafish.core.install.SetupDatabaseResetService;
import com.aquafish.core.install.SetupExistingInstallationRecoveryRequest;
import com.aquafish.core.install.SetupExistingInstallationRecoveryResult;
import com.aquafish.core.install.SetupExistingInstallationRecoveryService;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 安装向导已有实例恢复与危险重装入口。
 */
@RestController
public final class SetupDatabaseMaintenanceController {

    private final SetupExistingInstallationRecoveryService
        recoveryService;

    private final SetupDatabaseResetService resetService;

    public SetupDatabaseMaintenanceController(
        SetupExistingInstallationRecoveryService recoveryService,
        SetupDatabaseResetService resetService
    ) {
        this.recoveryService = Objects.requireNonNull(
            recoveryService,
            "已有实例恢复服务不能为空。"
        );
        this.resetService = Objects.requireNonNull(
            resetService,
            "数据库重装服务不能为空。"
        );
    }

    /**
     * 恢复已有完整 Aquafish。
     */
    @PostMapping("/api/setup/recovery/existing")
    public Mono<ApiResult<SetupExistingInstallationRecoveryResult>>
        recover(
            @RequestBody
            SetupExistingInstallationRecoveryRequest request
        ) {

        return recoveryService.recover(request)
            .map(result ->
                ApiResult.ok(
                    result,
                    "已有实例恢复成功"
                )
            )
            .onErrorResume(error ->
                Mono.just(
                    ApiResult.fail(
                        "EXISTING_INSTALLATION_RECOVERY_FAILED",
                        safeMessage(
                            error,
                            "已有实例恢复失败。"
                        )
                    )
                )
            );
    }

    /**
     * 用户明确确认后精确清理 Aquafish 表。
     */
    @PostMapping("/api/setup/database/reset")
    public Mono<ApiResult<SetupDatabaseResetResult>>
        reset(
            @RequestBody SetupDatabaseResetRequest request
        ) {

        return resetService.reset(request)
            .map(result ->
                ApiResult.ok(
                    result,
                    "Aquafish 数据库清理完成"
                )
            )
            .onErrorResume(error ->
                Mono.just(
                    ApiResult.fail(
                        "SETUP_DATABASE_RESET_FAILED",
                        safeMessage(
                            error,
                            "Aquafish 数据库清理失败。"
                        )
                    )
                )
            );
    }

    private String safeMessage(
        Throwable error,
        String fallback
    ) {
        return error == null
            || error.getMessage() == null
            || error.getMessage().isBlank()
                ? fallback
                : error.getMessage();
    }
}
