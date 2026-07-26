package com.aquafish.admin.web;

import com.aquafish.admin.plugin.ui.PluginUiPermissionService;
import com.aquafish.admin.plugin.ui.PluginUiResourceService;
import com.aquafish.admin.plugin.ui.PluginUiResourceService.Asset;
import com.aquafish.admin.plugin.ui.PluginUiResourceService.Catalog;
import com.aquafish.common.web.ApiResult;
import com.aquafish.core.admin.auth.AdminAuthUser;
import com.aquafish.plugin.runtime.PluginManagementSnapshot;
import com.aquafish.plugin.runtime.PluginRuntimeLifecycleService;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 后台 PF4J 插件状态和启停接口。
 *
 * <p>状态直接来自 plugin 模块的真实 PF4J 管理器；启停、重扫会同步数据库 enabled_flag
 * 和审计日志。生命周期写操作只允许超级管理员执行。</p>
 */
@RestController
@RequestMapping("/api/admin/plugins")
public class AdminPluginStatusController {

    private final PluginRuntimeLifecycleService lifecycleService;
    private final PluginUiResourceService pluginUiResourceService;
    private final PluginUiPermissionService pluginUiPermissionService;

    public AdminPluginStatusController(
        PluginRuntimeLifecycleService lifecycleService,
        PluginUiResourceService pluginUiResourceService,
        PluginUiPermissionService pluginUiPermissionService
    ) {
        this.lifecycleService = lifecycleService;
        this.pluginUiResourceService = pluginUiResourceService;
        this.pluginUiPermissionService = pluginUiPermissionService;
    }

    @GetMapping("/status")
    public Mono<ResponseEntity<ApiResult<PluginManagementSnapshot>>> status() {
        return lifecycleService.snapshot()
            .map(data -> ok(data, "插件运行状态读取成功。"))
            .onErrorResume(error -> Mono.just(fail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "PLUGIN_STATUS_READ_FAILED",
                rootMessage(error)
            )));
    }

    /**
     * 返回当前已启动且通过宿主清单校验的插件 UI。
     *
     * <p>清单扫描需要读取目录或 JAR，因此切换到 boundedElastic；权限来自数据库，
     * 查询失败会按空授权处理。</p>
     */
    @GetMapping("/ui")
    public Mono<ResponseEntity<ApiResult<Catalog>>> uiCatalog() {
        return Mono.fromCallable(pluginUiResourceService::scan)
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(pluginUiPermissionService::enrich)
            .map(data -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResult.ok(
                    data,
                    "插件 UI 清单读取成功。"
                )))
            .onErrorResume(error -> Mono.just(
                ResponseEntity.status(
                        HttpStatus.INTERNAL_SERVER_ERROR
                    )
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResult.fail(
                        "PLUGIN_UI_CATALOG_FAILED",
                        rootMessage(error)
                    ))
            ));
    }

    /**
     * 输出清单允许的同源插件脚本或样式。
     */
    @GetMapping("/{pluginId}/ui/{*assetPath}")
    public Mono<ResponseEntity<Resource>> uiAsset(
        @PathVariable("pluginId") String pluginId,
        @PathVariable("assetPath") String assetPath
    ) {
        return Mono.fromCallable(() ->
                pluginUiResourceService.asset(
                    pluginId,
                    assetPath
                )
            )
            .subscribeOn(Schedulers.boundedElastic())
            .map(this::assetResponse)
            .onErrorReturn(
                ResponseEntity.<Resource>notFound().build()
            );
    }

    @PostMapping("/rescan")
    public Mono<ResponseEntity<ApiResult<PluginManagementSnapshot>>> rescan(
        Authentication authentication
    ) {
        return Mono.defer(() -> {
            requireSuperAdmin(authentication);
            return lifecycleService.rescan();
        })
            .map(data -> ok(data, "插件目录重新扫描完成。"))
            .onErrorResume(error -> Mono.just(fail(
                HttpStatus.BAD_REQUEST,
                "PLUGIN_RESCAN_FAILED",
                rootMessage(error)
            )));
    }

    @PostMapping("/{pluginId}/start")
    public Mono<ResponseEntity<ApiResult<PluginManagementSnapshot>>> start(
        Authentication authentication,
        @PathVariable("pluginId") String pluginId
    ) {
        return Mono.defer(() -> {
            AdminAuthUser operator = requireSuperAdmin(authentication);
            return lifecycleService.start(pluginId, operator.id());
        })
            .map(data -> ok(data, "插件启用成功。"))
            .onErrorResume(error -> Mono.just(fail(
                HttpStatus.BAD_REQUEST,
                "PLUGIN_START_FAILED",
                rootMessage(error)
            )));
    }

    @PostMapping("/{pluginId}/stop")
    public Mono<ResponseEntity<ApiResult<PluginManagementSnapshot>>> stop(
        Authentication authentication,
        @PathVariable("pluginId") String pluginId
    ) {
        return Mono.defer(() -> {
            AdminAuthUser operator = requireSuperAdmin(authentication);
            return lifecycleService.stop(pluginId, operator.id());
        })
            .map(data -> ok(data, "插件停用成功。"))
            .onErrorResume(error -> Mono.just(fail(
                HttpStatus.BAD_REQUEST,
                "PLUGIN_STOP_FAILED",
                rootMessage(error)
            )));
    }

    private AdminAuthUser requireSuperAdmin(
        Authentication authentication
    ) {
        if (authentication == null
            || !(authentication.getPrincipal()
                instanceof AdminAuthUser user)) {
            throw new IllegalStateException(
                "未登录或登录已过期，请重新登录。"
            );
        }
        if (!user.superAdmin()) {
            throw new IllegalStateException(
                "插件生命周期只能由超级管理员操作。"
            );
        }
        return user;
    }

    private ResponseEntity<Resource> assetResponse(Asset asset) {
        return ResponseEntity.ok()
            .contentType(asset.mediaType())
            .contentLength(asset.contentLength())
            .lastModified(asset.lastModified())
            .cacheControl(
                CacheControl.maxAge(Duration.ofMinutes(5))
                    .cachePrivate()
            )
            .header(
                "X-Content-Type-Options",
                "nosniff"
            )
            .header(
                "Cross-Origin-Resource-Policy",
                "same-origin"
            )
            .body(asset.resource());
    }

    private ResponseEntity<ApiResult<PluginManagementSnapshot>> ok(
        PluginManagementSnapshot data,
        String message
    ) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(ApiResult.ok(data, message));
    }

    private ResponseEntity<ApiResult<PluginManagementSnapshot>> fail(
        HttpStatus status,
        String code,
        String message
    ) {
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore())
            .body(ApiResult.fail(code, message));
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        if (current == null
            || current.getMessage() == null
            || current.getMessage().isBlank()) {
            return "插件生命周期操作失败。";
        }
        return current.getMessage();
    }
}
