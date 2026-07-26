package com.aquafish.admin.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.r2dbc.RuntimeR2dbcConnectionFactory;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 开发环境 R2DBC 连接诊断接口。
 *
 * <p>响应中包含数据库主机、库名和用户名等运行信息，因此只允许在 dev Profile 注册。</p>
 */
@Profile("dev")
@RestController
public class AdminR2dbcDiagnosticsController {

    private final DatabaseClient databaseClient;
    private final DatabaseRuntimeSettingsService databaseRuntimeSettingsService;
    private final RuntimeR2dbcConnectionFactory runtimeConnectionFactory;

    public AdminR2dbcDiagnosticsController(
        DatabaseClient databaseClient,
        DatabaseRuntimeSettingsService databaseRuntimeSettingsService,
        RuntimeR2dbcConnectionFactory runtimeConnectionFactory
    ) {
        this.databaseClient = databaseClient;
        this.databaseRuntimeSettingsService = databaseRuntimeSettingsService;
        this.runtimeConnectionFactory = runtimeConnectionFactory;
    }

    /**
     * 在开发环境执行真实 {@code SELECT 1}，并返回当前 R2DBC 协议、连接元数据和耗时。
     *
     * <p>该接口用于排查安装后数据库配置与运行连接不一致的问题，十二秒未完成即失败；
     * 因响应包含主机、库名和用户名，只在 {@code dev} Profile 中注册。</p>
     */
    @GetMapping("/api/admin/database/r2dbc/status")
    public Mono<ApiResult<Map<String, Object>>> status() {
        return Mono.defer(() -> {
            DatabaseSettings settings = databaseRuntimeSettingsService.current();
            long startedAt = System.nanoTime();

            return databaseClient
                .sql("SELECT 1 AS healthy")
                .map((row, metadata) -> row.get(0))
                .one()
                .defaultIfEmpty(1)
                .map(value -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("connected", true);
                    data.put("databaseType", String.valueOf(settings.type()));
                    data.put("protocol", runtimeConnectionFactory.currentProtocol());
                    data.put("host", settings.host());
                    data.put("port", settings.port());
                    data.put("databaseName", settings.name());
                    data.put("username", settings.username());
                    data.put("tablePrefix", settings.tablePrefix());
                    data.put("connectionFactory", runtimeConnectionFactory.getMetadata().getName());
                    data.put("testValue", value);
                    data.put(
                        "elapsedMs",
                        Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
                    );

                    return ApiResult.ok(data, "R2DBC 数据库连接正常。");
                });
        })
            .timeout(Duration.ofSeconds(12))
            .onErrorResume(error -> Mono.just(
                ApiResult.<Map<String, Object>>fail(
                    "ADMIN_R2DBC_CONNECTION_FAILED",
                    "R2DBC 数据库连接失败：" + rootMessage(error)
                )
            ));
    }

    /** 展开响应式/驱动异常，保留数据库连接失败的根本原因。 */
    private String rootMessage(Throwable error) {
        if (error == null) {
            return "未知错误";
        }

        Throwable current = error;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return message == null || message.isBlank()
            ? current.getClass().getSimpleName()
            : message;
    }
}
