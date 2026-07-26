package com.aquafish.boot.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.boot.AquafishApplication;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

/**
 * Aquafish 主启动入口单实例保护接线测试。
 */
class AquafishApplicationInstanceGuardWiringTest {

    /**
     * 启动类必须保留正式 main 入口，
     * 并能够创建 SpringApplication。
     *
     * <p>
     * 具体监听器接线同时由源码静态检查脚本确认。
     * 这里避免真正启动 Netty 和数据库。
     * </p>
     *
     * @throws Exception 反射读取失败
     */
    @Test
    void shouldKeepMainApplicationEntryPoint()
        throws Exception {

        Method mainMethod =
            AquafishApplication.class
                .getDeclaredMethod(
                    "main",
                    String[].class
                );

        assertTrue(
            java.lang.reflect.Modifier
                .isStatic(
                    mainMethod.getModifiers()
                )
        );

        SpringApplication application =
            new SpringApplication(
                AquafishApplication.class
            );

        assertTrue(
            application
                .getAllSources()
                .contains(
                    AquafishApplication.class
                )
        );
    }
}
