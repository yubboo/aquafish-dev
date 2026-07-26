package com.aquafish.template.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.core.TemplateType;
import com.aquafish.template.resolve.ResolvedTemplate;
import com.aquafish.template.resolve.ThemeTemplateResolver;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * DefaultTemplateRenderService 自动化测试。
 *
 * <p>
 * 本测试用于验证 Aquafish 统一模板渲染调度器
 * 能否根据 {@link ResolvedTemplate#engineId()}
 * 把渲染请求准确交给对应的模板引擎。
 * </p>
 *
 * <p>重点验证：</p>
 *
 * <ol>
 *     <li>Thymeleaf 模板只会交给 Thymeleaf 引擎；</li>
 *     <li>Pebble 模板只会交给 Pebble 引擎；</li>
 *     <li>不会因为两个引擎同时存在而随机选择；</li>
 *     <li>未知模板引擎会返回明确失败结果；</li>
 *     <li>空模板引擎标识会返回明确失败结果；</li>
 *     <li>空渲染请求会被安全拦截。</li>
 * </ol>
 *
 * <p>
 * 本测试使用记录型测试引擎，
 * 不读取真实主题文件，也不执行真实模板语法。
 * Pebble 的真实运行能力已经由 PebbleThemeEngineTest
 * 单独验证。
 * </p>
 */
class DefaultTemplateRenderServiceTest {

    /**
     * 验证 engineId 为 pebble 时，
     * 调度器只调用 Pebble 引擎。
     */
    @Test
    void shouldDispatchPebbleTemplateToPebbleEngine() {
        TemplateType templateType =
            createTemplateType();

        TemplateRenderRequest request =
            createRenderRequest(templateType);

        ResolvedTemplate resolvedTemplate =
            createResolvedTemplate(
                templateType,
                "pebble"
            );

        RecordingThemeEngine thymeleafEngine =
            new RecordingThemeEngine(
                "thymeleaf",
                "thymeleaf-rendered"
            );

        RecordingThemeEngine pebbleEngine =
            new RecordingThemeEngine(
                "pebble",
                "pebble-rendered"
            );

        DefaultTemplateRenderService renderService =
            createRenderService(
                resolvedTemplate,
                thymeleafEngine,
                pebbleEngine
            );

        TemplateRenderResult result =
            renderService.render(request);

        /*
         * 调度结果必须成功，
         * 并且 HTML 必须来自 Pebble 测试引擎。
         */
        assertTrue(
            result.success(),
            () -> "Pebble 调度失败："
                + result.errorMessage()
        );

        assertEquals(
            "pebble-rendered",
            result.html()
        );

        /*
         * Pebble 引擎必须被调用一次，
         * Thymeleaf 引擎不能被误调用。
         */
        assertTrue(pebbleEngine.called());
        assertFalse(thymeleafEngine.called());

        /*
         * 调度器必须把原始请求和同一个模板解析结果
         * 原样传递给目标模板引擎。
         */
        assertSame(
            request,
            pebbleEngine.lastRequest()
        );

        assertSame(
            resolvedTemplate,
            pebbleEngine.lastResolvedTemplate()
        );
    }

    /**
     * 验证 engineId 为 thymeleaf 时，
     * 调度器只调用 Thymeleaf 引擎。
     */
    @Test
    void shouldDispatchThymeleafTemplateToThymeleafEngine() {
        TemplateType templateType =
            createTemplateType();

        TemplateRenderRequest request =
            createRenderRequest(templateType);

        ResolvedTemplate resolvedTemplate =
            createResolvedTemplate(
                templateType,
                "thymeleaf"
            );

        RecordingThemeEngine thymeleafEngine =
            new RecordingThemeEngine(
                "thymeleaf",
                "thymeleaf-rendered"
            );

        RecordingThemeEngine pebbleEngine =
            new RecordingThemeEngine(
                "pebble",
                "pebble-rendered"
            );

        DefaultTemplateRenderService renderService =
            createRenderService(
                resolvedTemplate,
                thymeleafEngine,
                pebbleEngine
            );

        TemplateRenderResult result =
            renderService.render(request);

        assertTrue(
            result.success(),
            () -> "Thymeleaf 调度失败："
                + result.errorMessage()
        );

        assertEquals(
            "thymeleaf-rendered",
            result.html()
        );

        assertTrue(thymeleafEngine.called());
        assertFalse(pebbleEngine.called());

        assertSame(
            request,
            thymeleafEngine.lastRequest()
        );

        assertSame(
            resolvedTemplate,
            thymeleafEngine.lastResolvedTemplate()
        );
    }

    /**
     * 验证主题声明系统不支持的模板引擎时，
     * 调度器会返回明确失败结果。
     *
     * <p>
     * 不能把未知模板引擎偷偷替换成 Thymeleaf，
     * 因为不同模板语法无法互相兼容。
     * </p>
     */
    @Test
    void shouldRejectUnsupportedThemeEngine() {
        TemplateType templateType =
            createTemplateType();

        TemplateRenderRequest request =
            createRenderRequest(templateType);

        ResolvedTemplate resolvedTemplate =
            createResolvedTemplate(
                templateType,
                "freemarker"
            );

        RecordingThemeEngine thymeleafEngine =
            new RecordingThemeEngine(
                "thymeleaf",
                "thymeleaf-rendered"
            );

        RecordingThemeEngine pebbleEngine =
            new RecordingThemeEngine(
                "pebble",
                "pebble-rendered"
            );

        DefaultTemplateRenderService renderService =
            createRenderService(
                resolvedTemplate,
                thymeleafEngine,
                pebbleEngine
            );

        TemplateRenderResult result =
            renderService.render(request);

        assertFalse(result.success());

        assertTrue(
            result.errorMessage().contains(
                "freemarker"
            )
        );

        /*
         * 未知引擎不能导致现有任何模板引擎被误调用。
         */
        assertFalse(thymeleafEngine.called());
        assertFalse(pebbleEngine.called());
    }

    /**
     * 验证主题没有声明 engine 时，
     * 调度器会在查找注册中心之前直接返回失败。
     */
    @Test
    void shouldRejectBlankThemeEngineId() {
        TemplateType templateType =
            createTemplateType();

        TemplateRenderRequest request =
            createRenderRequest(templateType);

        ResolvedTemplate resolvedTemplate =
            createResolvedTemplate(
                templateType,
                "   "
            );

        RecordingThemeEngine thymeleafEngine =
            new RecordingThemeEngine(
                "thymeleaf",
                "thymeleaf-rendered"
            );

        RecordingThemeEngine pebbleEngine =
            new RecordingThemeEngine(
                "pebble",
                "pebble-rendered"
            );

        DefaultTemplateRenderService renderService =
            createRenderService(
                resolvedTemplate,
                thymeleafEngine,
                pebbleEngine
            );

        TemplateRenderResult result =
            renderService.render(request);

        assertFalse(result.success());

        assertTrue(
            result.errorMessage().contains(
                "没有声明模板引擎"
            )
        );

        assertFalse(thymeleafEngine.called());
        assertFalse(pebbleEngine.called());
    }

    /**
     * 验证空渲染请求不会进入模板解析器和模板引擎。
     */
    @Test
    void shouldRejectNullRenderRequest() {
        TemplateType templateType =
            createTemplateType();

        ResolvedTemplate resolvedTemplate =
            createResolvedTemplate(
                templateType,
                "thymeleaf"
            );

        RecordingThemeEngine thymeleafEngine =
            new RecordingThemeEngine(
                "thymeleaf",
                "thymeleaf-rendered"
            );

        RecordingThemeEngine pebbleEngine =
            new RecordingThemeEngine(
                "pebble",
                "pebble-rendered"
            );

        DefaultTemplateRenderService renderService =
            createRenderService(
                resolvedTemplate,
                thymeleafEngine,
                pebbleEngine
            );

        TemplateRenderResult result =
            renderService.render(null);

        assertFalse(result.success());

        assertTrue(
            result.errorMessage().contains(
                "模板渲染请求不能为空"
            )
        );

        assertFalse(thymeleafEngine.called());
        assertFalse(pebbleEngine.called());
    }

    /**
     * 创建测试使用的统一模板类型。
     *
     * @return 调度器测试模板类型
     */
    private TemplateType createTemplateType() {
        return new TemplateType(
            "dispatcher-test",
            "index.html",
            "模板调度器测试",
            "验证模板引擎分发是否准确。"
        );
    }

    /**
     * 创建测试使用的统一模板渲染请求。
     *
     * @param templateType 请求对应的模板类型
     * @return 模板渲染请求
     */
    private TemplateRenderRequest createRenderRequest(
        TemplateType templateType
    ) {
        return new TemplateRenderRequest(
            templateType,
            Map.of(
                "siteName",
                "Aquafish"
            ),
            "dispatcher-test",
            null,
            Locale.SIMPLIFIED_CHINESE,
            null
        );
    }

    /**
     * 创建测试使用的模板解析结果。
     *
     * <p>
     * 这里不读取真实文件，
     * 因为本测试只验证调度器选择哪个引擎。
     * </p>
     *
     * @param templateType 模板类型
     * @param engineId 本用例需要测试的模板引擎标识
     * @return 固定模板解析结果
     */
    private ResolvedTemplate createResolvedTemplate(
        TemplateType templateType,
        String engineId
    ) {
        return new ResolvedTemplate(
            templateType,
            "dispatcher-test-theme",
            engineId,
            "index.html",
            "H:\\aquafish-test\\templates\\index.html",
            true,
            "统一模板调度器测试解析结果。"
        );
    }

    /**
     * 创建统一模板调度服务。
     *
     * @param resolvedTemplate 模板解析器固定返回的结果
     * @param themeEngines 需要注册的测试模板引擎
     * @return 可用于当前测试的模板调度服务
     */
    private DefaultTemplateRenderService createRenderService(
        ResolvedTemplate resolvedTemplate,
        ThemeEngine... themeEngines
    ) {
        ThemeTemplateResolver templateResolver =
            new FixedThemeTemplateResolver(
                resolvedTemplate
            );

        ThemeEngineRegistry engineRegistry =
            new ThemeEngineRegistry(
                List.of(themeEngines)
            );

        return new DefaultTemplateRenderService(
            templateResolver,
            engineRegistry
        );
    }

    /**
     * 测试专用固定模板解析器。
     *
     * <p>
     * 本实现不读取当前启用主题，
     * 每次都返回测试提前准备好的 ResolvedTemplate。
     * </p>
     */
    private static final class FixedThemeTemplateResolver
        extends ThemeTemplateResolver {

        /**
         * 每次 resolve 调用固定返回的模板结果。
         */
        private final ResolvedTemplate resolvedTemplate;

        /**
         * 创建固定模板解析器。
         *
         * <p>
         * 父类依赖的 ActiveThemeResolver 和
         * ThemeInheritanceResolver 均传入 null，
         * 因为本测试已经重写 resolve 方法，
         * 不会执行父类真实主题查找逻辑。
         * </p>
         *
         * @param resolvedTemplate 固定返回的模板解析结果
         */
        private FixedThemeTemplateResolver(
            ResolvedTemplate resolvedTemplate
        ) {
            super(
                null,
                null
            );
            this.resolvedTemplate = resolvedTemplate;
        }

        /**
         * 忽略传入模板类型并返回固定测试结果。
         *
         * @param templateType 调度器请求的模板类型
         * @return 测试预设模板解析结果
         */
        @Override
        public ResolvedTemplate resolve(
            TemplateType templateType
        ) {
            return resolvedTemplate;
        }
    }

    /**
     * 可以记录调用状态的测试模板引擎。
     *
     * <p>
     * 该实现不执行真实 Thymeleaf 或 Pebble 语法，
     * 只记录自己是否被调度器选中。
     * </p>
     */
    private static final class RecordingThemeEngine
        implements ThemeEngine {

        /**
         * 当前测试引擎标识。
         */
        private final String engineId;

        /**
         * 当前测试引擎被调用后返回的 HTML。
         */
        private final String renderedHtml;

        /**
         * 是否已经调用 render 方法。
         */
        private boolean called;

        /**
         * 最后一次收到的模板渲染请求。
         */
        private TemplateRenderRequest lastRequest;

        /**
         * 最后一次收到的模板解析结果。
         */
        private ResolvedTemplate lastResolvedTemplate;

        /**
         * 创建记录型测试模板引擎。
         *
         * @param engineId 模板引擎唯一标识
         * @param renderedHtml 测试时返回的固定 HTML
         */
        private RecordingThemeEngine(
            String engineId,
            String renderedHtml
        ) {
            this.engineId = engineId;
            this.renderedHtml = renderedHtml;
        }

        /**
         * 返回测试模板引擎标识。
         *
         * @return thymeleaf 或 pebble
         */
        @Override
        public String engineId() {
            return engineId;
        }

        /**
         * 记录调度器传入的参数并返回固定成功结果。
         *
         * @param request 调度器传入的原始请求
         * @param resolvedTemplate 调度器传入的模板解析结果
         * @return 固定成功模板渲染结果
         */
        @Override
        public TemplateRenderResult render(
            TemplateRenderRequest request,
            ResolvedTemplate resolvedTemplate
        ) {
            called = true;
            lastRequest = request;
            lastResolvedTemplate = resolvedTemplate;

            return TemplateRenderResult.success(
                renderedHtml,
                resolvedTemplate.absoluteTemplatePath(),
                resolvedTemplate.themeName(),
                false
            );
        }

        /**
         * 返回当前测试引擎是否被调用。
         *
         * @return 已调用返回 true
         */
        private boolean called() {
            return called;
        }

        /**
         * 返回最后一次收到的渲染请求。
         *
         * @return 最后一次模板渲染请求
         */
        private TemplateRenderRequest lastRequest() {
            return lastRequest;
        }

        /**
         * 返回最后一次收到的模板解析结果。
         *
         * @return 最后一次模板解析结果
         */
        private ResolvedTemplate lastResolvedTemplate() {
            return lastResolvedTemplate;
        }
    }
}
