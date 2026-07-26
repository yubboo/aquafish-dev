package com.aquafish.plugin.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 在数据库迁移和 Web 服务均已就绪后恢复插件运行状态。
 */
@Component
public final class PluginRuntimeBootstrap {

    private static final Logger LOG =
        LoggerFactory.getLogger(PluginRuntimeBootstrap.class);

    private final PluginRuntimeLifecycleService lifecycleService;

    public PluginRuntimeBootstrap(
        PluginRuntimeLifecycleService lifecycleService
    ) {
        this.lifecycleService = lifecycleService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        lifecycleService.bootstrap().subscribe(
            ignored -> {
            },
            error -> LOG.warn(
                "插件运行时启动恢复失败；安装完成或修复清单后可在后台重新扫描。",
                error
            ),
            () -> LOG.info("Aquafish PF4J 插件运行时已就绪。")
        );
    }
}
