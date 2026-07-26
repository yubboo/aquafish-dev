package com.aquafish.license;

import com.aquafish.common.web.ApiResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 后台系统平台授权管理 API。
 *
 * <p>这些接口仍然受后台登录和 CSRF 保护；许可证全局拦截器只豁免本组接口，保证
 * 管理员在未激活时能够查看设备码并提交授权码。</p>
 */
@RestController
@RequestMapping("/api/admin/license")
public final class LicenseController {

    private final LicenseService licenseService;

    public LicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    /** 返回设备码与脱敏实时授权状态，原始授权码不会出现在响应中。 */
    @GetMapping("/status")
    public ApiResult<LicenseStatusView> status() {
        return ApiResult.ok(licenseService.status(), "授权状态读取成功");
    }

    /**
     * 验证并激活授权码；业务校验失败返回 400 和稳定错误码，不覆盖原有效授权。
     */
    @PostMapping("/activation")
    public ResponseEntity<ApiResult<LicenseStatusView>> activate(
        @RequestBody LicenseActivationRequest request
    ) {
        try {
            LicenseStatusView status = licenseService.activate(
                request == null ? null : request.licenseCode()
            );
            return ResponseEntity.ok(ApiResult.ok(status, "Aquafish 系统平台激活成功"));
        } catch (LicenseActivationException error) {
            return ResponseEntity.badRequest().body(
                ApiResult.fail(error.code(), error.getMessage(), licenseService.status())
            );
        }
    }

    /**
     * 输入授权中心生成的 AQO1 短激活码。设备码由后端读取，前端不能伪造或替换。
     */
    @PostMapping("/online/activation")
    public Mono<ResponseEntity<ApiResult<LicenseStatusView>>> activateOnline(
        @RequestBody OnlineLicenseActivationRequest request
    ) {
        String activationCode = request == null ? null : request.activationCode();
        return Mono.fromFuture(licenseService.activateOnline(activationCode))
            .map(status -> ResponseEntity.ok(
                ApiResult.ok(status, "Aquafish 在线授权激活成功")
            ))
            .onErrorResume(error -> {
                Throwable cause = unwrap(error);
                if (cause instanceof LicenseActivationException activationError) {
                    return Mono.just(ResponseEntity.badRequest().body(
                        ApiResult.fail(
                            activationError.code(), activationError.getMessage(), licenseService.status()
                        )
                    ));
                }
                return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    ApiResult.fail(
                        "ONLINE_ACTIVATION_FAILED", "在线授权中心暂时不可用。", licenseService.status()
                    )
                ));
            });
    }

    /** 删除本机授权文件并返回新的未激活状态，实例设备码保持不变。 */
    @DeleteMapping("/activation")
    public ResponseEntity<ApiResult<LicenseStatusView>> deactivate() {
        try {
            return ResponseEntity.ok(
                ApiResult.ok(licenseService.deactivate(), "本机授权已经取消激活")
            );
        } catch (RuntimeException error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResult.fail("LICENSE_DEACTIVATE_FAILED", error.getMessage(), licenseService.status())
            );
        }
    }

    /**
     * 主动等待一次在线状态刷新；仍受后台登录与 CSRF 保护，并由客户端超时限制兜底。
     */
    @PostMapping("/online/refresh")
    public Mono<ApiResult<LicenseStatusView>> refreshOnline() {
        return Mono.fromFuture(licenseService.refreshOnline())
            .map(status -> ApiResult.ok(status, "在线授权状态复核完成"));
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null
            && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }
}
