package com.aquafish.template.engine;


import com.aquafish.template.emergency.EmergencyTemplateRenderer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.template.core.TemplateRenderService;
import com.aquafish.template.resolve.ThemeTemplateResolver;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Aquafish 双模板引擎 Spring 容器集成测试。
 *
 * <p>
 * 前面的自动化测试已经分别验证了：
 * </p>
 *
 * <ul>
 *     <li>Pebble 可以真实执行模板渲染；</li>
 *     <li>ThemeEngineRegistry 可以注册多个模板引擎；</li>
 *     <li>DefaultTemplateRenderService 可以准确分发请求。</li>
 * </ul>
 *
 * <p>
 * 本测试进一步创建一个真实的最小 Spring 容器，
 * 验证这些组件按照生产代码中的 Spring 注解进行装配时，
 * 不会出现 Bean 缺失、重复注册或依赖注入歧义。
 * </p>
 *
 * <p>重点验证：</p>
 *
 * <ol>
 *     <li>ThymeleafTemplateRenderService 能够注册为 Spring Bean；</li>
 *     <li>PebbleThemeEngine 能够注册为 Spring Bean；</li>
 *     <li>ThemeEngineRegistry 会自动收集两个真实引擎；</li>
 *     <li>系统中会同时存在两个 TemplateRenderService 实现；</li>
 *     <li>
 *         注入单个 TemplateRenderService 时，
 *         Spring 会根据 @Primary 选择
 *         DefaultTemplateRenderService；
 *     </li>
 *     <li>
 *         不会因为 ThymeleafTemplateRenderService
 *         同时实现旧接口而产生 Bean 冲突。
 *     </li>
 * </ol>
 *
 * <p>
 * 本测试不启动完整 Aquafish Boot 应用，
 * 也不连接数据库、Redis或真实主题目录。
 * 它只启动模板模块所需的最小 Spring 容器，
 * 因此测试速度快，并且不会污染开发环境。
 * </p>
 */
class ThemeEngineSpringContextTest {

    /**
     * 验证双引擎和统一调度器能够在真实 Spring 容器中正确装配。
     */
    @Test
    void shouldRegisterBothEnginesAndSelectPrimaryRenderService() {
        /*
         * 使用 AnnotationConfigApplicationContext
         * 创建一个真实但最小化的 Spring 容器。
         *
         * try-with-resources 会在测试结束时自动关闭容器，
         * 防止测试线程或资源残留。
         */
        try (
            AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()
        ) {
            /*
             * ThymeleafTemplateRenderService 和
             * DefaultTemplateRenderService 都依赖
             * ThemeTemplateResolver。
             *
             * 本测试只验证依赖注入，不执行真实模板解析，
             * 因此注册一个不会实际调用的解析器实例即可。
             *
             * ActiveThemeResolver 和
             * ThemeInheritanceResolver 暂时传入 null，
             * 只有真正调用 resolve 时才会使用它们。
             */
            context.registerBean(
                ThemeTemplateResolver.class,
                () -> new ThemeTemplateResolver(
                    null,
                    null
                )
            );

            /*
             * 注册生产代码中的真实组件类。
             *
             * Spring 会读取这些类上的：
             * @Component
             * @Primary
             * 以及构造方法依赖。
             */
            context.register(
                ThymeleafTemplateRenderService.class,
                PebbleThemeEngine.class,
                ThemeEngineRegistry.class,
                EmergencyTemplateRenderer.class,
                DefaultTemplateRenderService.class
            );

            /*
             * 正式刷新容器并执行 Bean 创建和依赖注入。
             *
             * 如果存在循环依赖、Bean 缺失、
             * 重复标识或单 Bean 注入歧义，
             * 这里会直接抛出异常并使测试失败。
             */
            context.refresh();

            /*
             * 读取模板引擎注册中心。
             *
             * 构造注册中心时，
             * Spring 应自动注入全部 ThemeEngine Bean。
             */
            ThemeEngineRegistry registry =
                context.getBean(
                    ThemeEngineRegistry.class
                );

            assertTrue(
                registry.supports("thymeleaf")
            );

            assertTrue(
                registry.supports("pebble")
            );

            assertEquals(
                2,
                registry.engineIds().size()
            );

            /*
             * 获取 Spring 容器中的全部真实 ThemeEngine。
             *
             * 当前应只有：
             * 1. ThymeleafTemplateRenderService
             * 2. PebbleThemeEngine
             */
            Map<String, ThemeEngine> themeEngines =
                context.getBeansOfType(
                    ThemeEngine.class
                );

            assertEquals(
                2,
                themeEngines.size()
            );

            ThemeEngine thymeleafEngine =
                registry.require("thymeleaf");

            ThemeEngine pebbleEngine =
                registry.require("pebble");

            assertInstanceOf(
                ThymeleafTemplateRenderService.class,
                thymeleafEngine
            );

            assertInstanceOf(
                PebbleThemeEngine.class,
                pebbleEngine
            );

            /*
             * 注册中心返回的引擎必须就是 Spring 容器中的实例，
             * 不能额外创建脱离 Spring 生命周期的新对象。
             */
            assertSame(
                context.getBean(
                    ThymeleafTemplateRenderService.class
                ),
                thymeleafEngine
            );

            assertSame(
                context.getBean(
                    PebbleThemeEngine.class
                ),
                pebbleEngine
            );

            /*
             * 当前存在两个 TemplateRenderService 实现：
             *
             * 1. ThymeleafTemplateRenderService
             *    用于兼容旧渲染入口；
             *
             * 2. DefaultTemplateRenderService
             *    用于新的统一双引擎调度入口。
             */
            Map<String, TemplateRenderService> renderServices =
                context.getBeansOfType(
                    TemplateRenderService.class
                );

            assertEquals(
                2,
                renderServices.size()
            );

            /*
             * 当业务模块只声明需要一个
             * TemplateRenderService 时，
             * Spring 必须根据 @Primary
             * 返回统一调度服务。
             *
             * 如果 @Primary 没有生效，
             * 这里会因为存在两个实现而抛出
             * NoUniqueBeanDefinitionException。
             */
            TemplateRenderService primaryRenderService =
                context.getBean(
                    TemplateRenderService.class
                );

            assertInstanceOf(
                DefaultTemplateRenderService.class,
                primaryRenderService
            );

            assertSame(
                context.getBean(
                    DefaultTemplateRenderService.class
                ),
                primaryRenderService
            );
        }
    }
}
