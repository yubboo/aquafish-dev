package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.install.SetupDeploymentContextService;
import com.aquafish.core.redis.RedisConnectionTestResult;
import com.aquafish.core.redis.RedisConnectionTester;
import com.aquafish.core.redis.RedisRuntimeSettingsService;
import com.aquafish.core.redis.RedisSettings;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 首次安装 Redis 检测接口。
 *
 * <p>分发模式允许检测请求体中的可选配置；托管模式只检测服务器环境变量，
 * 浏览器无法覆盖 1Panel/Docker 注入的 Redis 密钥。</p>
 */
@RestController
public class SetupRedisController {

    private final RedisConnectionTester connectionTester;
    private final RedisRuntimeSettingsService settingsService;
    private final SetupDeploymentContextService deploymentContextService;

    public SetupRedisController(
        RedisConnectionTester connectionTester,
        RedisRuntimeSettingsService settingsService,
        SetupDeploymentContextService deploymentContextService
    ) {
        this.connectionTester = connectionTester;
        this.settingsService = settingsService;
        this.deploymentContextService = deploymentContextService;
    }

    /**
     * 检测分发安装页面提交的 Redis 配置是否可以真实建立连接。
     *
     * <p>该接口只负责连通性校验，不会把配置写入文件或数据库；当部署方式为
     * 1Panel/Docker 托管 Redis 时拒绝浏览器覆盖服务器注入的配置，避免密钥被
     * 前端请求意外替换。前端安装向导在进入下一步前调用本接口。</p>
     *
     * @param request 安装者填写的 Redis 主机、端口、数据库编号和可选密码
     * @return 包含真实连接结果及错误说明的统一响应
     */
    @PostMapping("/api/setup/redis/test")
    public Mono<ApiResult<RedisConnectionTestResult>> test(
        @RequestBody RedisSettings request
    ) {
        if (deploymentContextService.managedRedis()) {
            return Mono.just(
                ApiResult.fail(
                    "REDIS_IS_MANAGED",
                    "Redis 由部署平台管理，安装页面不能覆盖。"
                )
            );
        }
        return response(connectionTester.test(request));
    }

    /**
     * 检测由 1Panel/Docker 环境变量托管的 Redis 配置。
     *
     * <p>配置由 {@link RedisRuntimeSettingsService} 从服务端运行环境解析，
     * 浏览器不提交也不会获得 Redis 密码；非托管部署调用本接口会被拒绝。</p>
     *
     * @return 托管 Redis 的真实连接检测结果
     */
    @PostMapping("/api/setup/redis/managed/test")
    public Mono<ApiResult<RedisConnectionTestResult>> testManaged() {
        if (!deploymentContextService.managedRedis()) {
            return Mono.just(
                ApiResult.fail(
                    "REDIS_NOT_MANAGED",
                    "当前 Redis 配置由安装器管理。"
                )
            );
        }
        return response(connectionTester.test(settingsService.current()));
    }

    /**
     * 把底层连接测试结果转换为安装器统一的成功或失败响应。
     *
     * @param result 异步连接检测结果
     * @return 可直接由安装页消费的统一 API 响应
     */
    private Mono<ApiResult<RedisConnectionTestResult>> response(
        Mono<RedisConnectionTestResult> result
    ) {
        return result.map(value -> value.connected()
            ? ApiResult.ok(value, value.message())
            : ApiResult.fail("REDIS_CONNECTION_FAILED", value.message(), value));
    }
}
