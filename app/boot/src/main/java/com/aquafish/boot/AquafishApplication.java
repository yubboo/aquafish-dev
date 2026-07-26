package com.aquafish.boot;

import com.aquafish.boot.runtime.AquafishInstanceLockListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aquafish 后端启动入口。
 *
 * <p>当前职责：</p>
 *
 * <ol>
 *     <li>启动完整 Aquafish Spring Boot 后端；</li>
 *     <li>扫描整个 com.aquafish 包下的业务模块；</li>
 *     <li>
 *         在 Netty 启动前保护 standalone workdir，
 *         防止同一套 Aquafish 被重复启动；
 *     </li>
 *     <li>对外提供 /api/** 后端接口。</li>
 * </ol>
 */
@SpringBootApplication(
    scanBasePackages = "com.aquafish"
)
public class AquafishApplication {

    /**
     * Java 程序入口。
     *
     * <p>
     * standalone 实例保护只挂载在真正的 main 启动链路。
     * 普通 Spring 单元测试不会因为测试上下文缓存
     * 而互相争抢生产实例锁。
     * </p>
     *
     * @param args 启动参数
     */
    public static void main(
        String[] args
    ) {
        SpringApplication application =
            new SpringApplication(
                AquafishApplication.class
            );

        application.addListeners(
            new AquafishInstanceLockListener()
        );

        application.run(args);
    }
}
