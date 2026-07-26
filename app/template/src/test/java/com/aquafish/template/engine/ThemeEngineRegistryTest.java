package com.aquafish.template.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.resolve.ResolvedTemplate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ThemeEngineRegistry 自动化测试。
 *
 * <p>
 * 本测试验证 Aquafish 双模板引擎注册中心的核心行为，
 * 不启动完整 Spring Boot 应用，也不读取真实主题文件。
 * </p>
 *
 * <p>主要验证以下规则：</p>
 *
 * <ol>
 *     <li>Thymeleaf 和 Pebble 可以同时注册；</li>
 *     <li>引擎标识会统一去除首尾空格并转换为小写；</li>
 *     <li>可以通过 find、require 和 supports 查询引擎；</li>
 *     <li>注册中心对外提供的引擎标识集合保持稳定；</li>
 *     <li>重复引擎标识会在初始化阶段被拦截；</li>
 *     <li>空引擎标识会在初始化阶段被拦截；</li>
 *     <li>不存在的引擎不会被静默替换成其他引擎。</li>
 * </ol>
 *
 * <p>
 * 测试使用内部测试引擎代替真实 Thymeleaf 和 Pebble 实现，
 * 目的是单独验证注册中心本身，不让模板文件或外部库
 * 影响注册逻辑的测试结果。
 * </p>
 */
class ThemeEngineRegistryTest {

    /**
     * 验证 Thymeleaf 和 Pebble 能够同时注册并正常查询。
     *
     * <p>
     * 测试中故意给引擎标识加入大写字母和首尾空格，
     * 用于确认注册中心会统一标准化为：
     * </p>
     *
     * <pre>
     * thymeleaf
     * pebble
     * </pre>
     */
    @Test
    void shouldRegisterAndFindBothThemeEngines() {
        TestThemeEngine thymeleafEngine =
            new TestThemeEngine(" Thymeleaf ");

        TestThemeEngine pebbleEngine =
            new TestThemeEngine("PEBBLE");

        ThemeEngineRegistry registry =
            new ThemeEngineRegistry(
                List.of(
                    thymeleafEngine,
                    pebbleEngine
                )
            );

        /*
         * supports 应该忽略调用方传入的大小写和首尾空格。
         */
        assertTrue(
            registry.supports("thymeleaf")
        );

        assertTrue(
            registry.supports(" THYMELEAF ")
        );

        assertTrue(
            registry.supports("pebble")
        );

        assertTrue(
            registry.supports(" Pebble ")
        );

        /*
         * 系统没有注册其他模板引擎时，
         * supports 必须明确返回 false。
         */
        assertFalse(
            registry.supports("freemarker")
        );

        assertFalse(
            registry.supports(null)
        );

        assertFalse(
            registry.supports("   ")
        );

        /*
         * find 找到引擎时应返回原始注册对象，
         * 不应该偷偷创建新的包装对象。
         */
        assertSame(
            thymeleafEngine,
            registry.find("THYMELEAF").orElseThrow()
        );

        assertSame(
            pebbleEngine,
            registry.find(" pebble ").orElseThrow()
        );

        /*
         * require 适用于正式渲染流程，
         * 找到后同样应返回已经注册的同一个实例。
         */
        assertSame(
            thymeleafEngine,
            registry.require("thymeleaf")
        );

        assertSame(
            pebbleEngine,
            registry.require("PEBBLE")
        );

        /*
         * 注册中心对外展示的是经过标准化后的稳定标识。
         *
         * LinkedHashMap 会保留注入顺序，
         * 因此这里预期先 thymeleaf，再 pebble。
         */
        assertEquals(
            Set.of("thymeleaf", "pebble"),
            registry.engineIds()
        );
    }

    /**
     * 验证 require 在引擎不存在时抛出明确异常。
     *
     * <p>
     * Aquafish 不允许主题声明 pebble，
     * 却在 Pebble 引擎不存在时偷偷改用 Thymeleaf。
     * 不同模板引擎语法并不兼容，错误替换只会产生
     * 更难诊断的模板错误。
     * </p>
     */
    @Test
    void shouldRejectUnsupportedRequiredEngine() {
        ThemeEngineRegistry registry =
            new ThemeEngineRegistry(
                List.of(
                    new TestThemeEngine("thymeleaf")
                )
            );

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () -> registry.require("pebble")
            );

        assertTrue(
            error.getMessage().contains("pebble")
        );
    }

    /**
     * 验证两个实现不能使用相同引擎标识。
     *
     * <p>
     * 即使两个标识的大小写和空格不同，
     * 标准化后只要相同，就必须被视为冲突。
     * </p>
     */
    @Test
    void shouldRejectDuplicateEngineIds() {
        TestThemeEngine firstEngine =
            new TestThemeEngine("pebble");

        TestThemeEngine secondEngine =
            new TestThemeEngine(" PEBBLE ");

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () -> new ThemeEngineRegistry(
                    List.of(
                        firstEngine,
                        secondEngine
                    )
                )
            );

        assertTrue(
            error.getMessage().contains(
                "重复的模板引擎标识"
            )
        );

        assertTrue(
            error.getMessage().contains("pebble")
        );
    }

    /**
     * 验证模板引擎不能返回空标识。
     *
     * <p>
     * 如果允许空标识进入注册表，
     * theme.yaml、后台配置和模板调度器都无法稳定引用该引擎。
     * 所以应在应用启动阶段立即拒绝。
     * </p>
     */
    @Test
    void shouldRejectBlankEngineId() {
        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () -> new ThemeEngineRegistry(
                    List.of(
                        new TestThemeEngine("   ")
                    )
                )
            );

        assertTrue(
            error.getMessage().contains(
                "模板引擎标识不能为空"
            )
        );
    }

    /**
     * 验证 null 引擎标识同样会被拒绝。
     */
    @Test
    void shouldRejectNullEngineId() {
        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () -> new ThemeEngineRegistry(
                    List.of(
                        new TestThemeEngine(null)
                    )
                )
            );

        assertTrue(
            error.getMessage().contains(
                "模板引擎标识不能为空"
            )
        );
    }

    /**
     * 测试专用模板引擎。
     *
     * <p>
     * 该实现只用于验证 ThemeEngineRegistry，
     * 不会读取模板文件，也不会真正执行模板语法。
     * </p>
     */
    private static final class TestThemeEngine
        implements ThemeEngine {

        /**
         * 测试时由用例指定的原始模板引擎标识。
         *
         * <p>
         * 允许包含大写字母、空格或 null，
         * 用于覆盖注册中心的标准化和校验分支。
         * </p>
         */
        private final String engineId;

        /**
         * 创建测试模板引擎。
         *
         * @param engineId 测试需要返回的原始引擎标识
         */
        private TestThemeEngine(String engineId) {
            this.engineId = engineId;
        }

        /**
         * 返回测试用模板引擎标识。
         *
         * @return 创建测试引擎时传入的原始值
         */
        @Override
        public String engineId() {
            return engineId;
        }

        /**
         * 测试注册中心时不需要真正执行模板渲染。
         *
         * <p>
         * 如果未来测试意外调用到该方法，
         * 会返回明确失败结果，而不是返回 null。
         * </p>
         *
         * @param request 模板渲染请求
         * @param resolvedTemplate 已解析模板
         * @return 固定的测试失败结果
         */
        @Override
        public TemplateRenderResult render(
            TemplateRenderRequest request,
            ResolvedTemplate resolvedTemplate
        ) {
            return TemplateRenderResult.failure(
                "测试模板引擎不执行真实渲染。"
            );
        }
    }
}
