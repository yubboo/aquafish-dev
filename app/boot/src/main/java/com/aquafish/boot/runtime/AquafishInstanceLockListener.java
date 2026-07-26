package com.aquafish.boot.runtime;

import com.aquafish.core.config.AquafishPathResolver;
import java.nio.file.Path;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

/**
 * Aquafish 主进程 standalone 单实例启动监听器。
 *
 * <p>
 * 在 Spring 环境已经解析、Web 服务器尚未启动时取得实例锁。
 * 因此第二个使用相同 workdir 的 Aquafish 主进程
 * 会在 Netty 监听端口之前直接终止启动。
 * </p>
 */
public final class AquafishInstanceLockListener
    implements ApplicationListener<ApplicationEvent> {

    /**
     * 当前进程持有的实例锁。
     */
    private AquafishInstanceLock instanceLock;

    /**
     * 处理 Spring Boot 生命周期事件。
     *
     * @param event 应用事件
     */
    @Override
    public synchronized void onApplicationEvent(
        ApplicationEvent event
    ) {
        if (
            event
                instanceof
                ApplicationEnvironmentPreparedEvent
                    environmentPreparedEvent
        ) {
            acquire(
                environmentPreparedEvent
            );

            return;
        }

        if (
            event instanceof ApplicationFailedEvent
            || event instanceof ContextClosedEvent
        ) {
            release();
        }
    }

    /**
     * 根据最终 Spring Environment 中的配置取得实例锁。
     */
    private void acquire(
        ApplicationEnvironmentPreparedEvent event
    ) {
        if (instanceLock != null) {
            return;
        }

        String configuredWorkDir =
            event
                .getEnvironment()
                .getProperty(
                    "aquafish.work-dir",
                    "workdir"
                );

        Path workDir =
            AquafishPathResolver
                .resolveWorkDirPath(
                    configuredWorkDir
                );

        instanceLock =
            AquafishInstanceLock.acquire(
                workDir
            );
    }

    /**
     * 释放当前实例锁。
     */
    private void release() {
        if (instanceLock == null) {
            return;
        }

        instanceLock.close();
        instanceLock = null;
    }
}
