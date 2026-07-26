package com.aquafish.boot;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * Aquafish 进程健康检查接口。
 *
 * <p><strong>功能：</strong>向开发菜单、Docker/1Panel 健康检查以及后台控制台提供
 * {@code GET /api/health}，用于确认 WebFlux 进程已经启动并能正常处理请求。</p>
 *
 * <p><strong>实现：</strong>接口不访问数据库、Redis 或文件系统，只返回站点名称、固定
 * {@code ok} 状态和当前时间，因此即使系统尚未安装也可以用于启动探测。</p>
 *
 * <p><strong>关联：</strong>前端 {@code DashboardPage.vue} 会读取本接口展示后端状态；
 * 部署脚本也可以用它区分“Java 进程存在”和“HTTP 服务真正可用”。</p>
 */
@RestController
public class HealthController {

    private final String siteName;

    /**
     * 注入站点名称；空值统一回退为 Aquafish，避免健康响应出现空名称。
     */
    public HealthController(
        @Value("${aquafish.site.name:Aquafish}") String siteName
    ) {
        this.siteName = siteName == null || siteName.isBlank() ? "Aquafish" : siteName.trim();
    }

    /**
     * 返回无外部依赖的轻量健康信息。
     *
     * @return 单次订阅即产生健康响应的 Mono
     */
    @GetMapping("/api/health")
    public Mono<Map<String, Object>> health() {
        return Mono.just(Map.of(
            "name", siteName,
            "status", "ok",
            "time", Instant.now().toString()
        ));
    }
}
