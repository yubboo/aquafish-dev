package com.aquafish.template.engine;


import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.core.TemplateRenderService;
import com.aquafish.template.emergency.EmergencyTemplateRenderer;
import com.aquafish.template.resolve.ResolvedTemplate;
import com.aquafish.template.resolve.ThemeTemplateResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Aquafish 默认统一模板渲染服务。
 *
 * <p>
 * 业务模块不需要直接判断当前主题使用 Thymeleaf
 * 还是 Pebble，也不需要自行处理主题模板回退。
 * </p>
 *
 * <p>统一调用流程：</p>
 *
 * <pre>
 * 业务模块
 * -> TemplateRenderService
 * -> ThemeTemplateResolver
 * -> ThemeEngineRegistry
 * -> ThymeleafThemeEngine / PebbleThemeEngine
 * </pre>
 *
 * <p>ThemeTemplateResolver 内部负责：</p>
 *
 * <pre>
 * 当前活动主题
 * -> 父主题继承链
 * -> 外置官方 default
 * -> 核心内置只读 fallback
 * </pre>
 *
 * <p>
 * 如果模板解析、模板引擎选择或实际模板渲染
 * 仍然发生异常，本服务会调用
 * {@link EmergencyTemplateRenderer}
 * 返回完全不依赖模板文件和模板引擎的最小安全页面。
 * </p>
 *
 * <p>紧急页面会在以下情况启用：</p>
 *
 * <ul>
 *     <li>模板请求为空；</li>
 *     <li>当前活动主题解析失败；</li>
 *     <li>父主题结构异常；</li>
 *     <li>模板解析器抛出运行异常；</li>
 *     <li>最终模板仍然不存在；</li>
 *     <li>模板声明的引擎没有注册；</li>
 *     <li>Thymeleaf 或 Pebble 返回失败结果；</li>
 *     <li>模板引擎直接抛出运行异常；</li>
 *     <li>模板引擎错误返回 null。</li>
 * </ul>
 *
 * <p>
 * 本服务不会捕获 {@link Error}，
 * 例如虚拟机内存耗尽等严重错误仍应交给 JVM
 * 和平台级故障处理机制。
 * </p>
 */
@Primary
@Service
public class DefaultTemplateRenderService
    implements TemplateRenderService {

    /**
     * 主题模板查找解析器。
     *
     * <p>
     * 负责从活动主题开始执行完整模板回退链。
     * </p>
     */
    private final ThemeTemplateResolver
        themeTemplateResolver;

    /**
     * 模板引擎注册中心。
     *
     * <p>
     * 根据 ResolvedTemplate.engineId()
     * 选择 Thymeleaf 或 Pebble。
     * </p>
     */
    private final ThemeEngineRegistry
        themeEngineRegistry;

    /**
     * 最小紧急静态页面渲染器。
     *
     * <p>
     * 正式 Spring 生产环境中必须存在。
     * 早期兼容构造方法中允许为空，
     * 用于保持前面已有测试的原始失败行为。
     * </p>
     */
    private final EmergencyTemplateRenderer
        emergencyTemplateRenderer;

    /**
     * 正式生产环境构造方法。
     *
     * <p>
     * Spring 会使用该构造方法注入完整依赖，
     * 因此正式运行时会启用紧急页面保护。
     * </p>
     *
     * @param themeTemplateResolver 主题模板解析器
     * @param themeEngineRegistry 模板引擎注册中心
     * @param emergencyTemplateRenderer 紧急页面渲染器
     */
    @Autowired
    public DefaultTemplateRenderService(
        ThemeTemplateResolver themeTemplateResolver,
        ThemeEngineRegistry themeEngineRegistry,
        EmergencyTemplateRenderer
            emergencyTemplateRenderer
    ) {
        if (themeTemplateResolver == null) {
            throw new IllegalArgumentException(
                "主题模板解析器不能为空。"
            );
        }

        if (themeEngineRegistry == null) {
            throw new IllegalArgumentException(
                "模板引擎注册中心不能为空。"
            );
        }

        if (emergencyTemplateRenderer == null) {
            throw new IllegalArgumentException(
                "紧急页面渲染器不能为空。"
            );
        }

        this.themeTemplateResolver =
            themeTemplateResolver;

        this.themeEngineRegistry =
            themeEngineRegistry;

        this.emergencyTemplateRenderer =
            emergencyTemplateRenderer;
    }

    /**
     * 兼容前面已有自动化测试和手动装配代码的构造方法。
     *
     * <p>
     * 使用该构造方法时不会自动生成紧急页面，
     * 而是保持原先的 TemplateRenderResult.failure() 行为。
     * </p>
     *
     * <p>
     * 正式业务代码应通过 Spring 注入
     * TemplateRenderService，而不是手动调用该构造方法。
     * </p>
     *
     * @param themeTemplateResolver 主题模板解析器
     * @param themeEngineRegistry 模板引擎注册中心
     */
    public DefaultTemplateRenderService(
        ThemeTemplateResolver themeTemplateResolver,
        ThemeEngineRegistry themeEngineRegistry
    ) {
        if (themeTemplateResolver == null) {
            throw new IllegalArgumentException(
                "主题模板解析器不能为空。"
            );
        }

        if (themeEngineRegistry == null) {
            throw new IllegalArgumentException(
                "模板引擎注册中心不能为空。"
            );
        }

        this.themeTemplateResolver =
            themeTemplateResolver;

        this.themeEngineRegistry =
            themeEngineRegistry;

        this.emergencyTemplateRenderer =
            null;
    }

    /**
     * 使用统一模板回退链和统一引擎注册中心渲染页面。
     *
     * @param request 模板渲染请求
     * @return 正常模板结果、失败结果或紧急安全页面
     */
    @Override
    public TemplateRenderResult render(
        TemplateRenderRequest request
    ) {
        /*
         * 正式生产环境中，空请求也不能直接导致白屏。
         *
         * 兼容构造模式下则保持早期测试所要求的失败结果。
         */
        if (request == null) {
            return emergencyOrFailure(
                null,
                "模板渲染请求不能为空。"
            );
        }

        final ResolvedTemplate resolvedTemplate;

        /*
         * 第一阶段：解析最终模板。
         *
         * 这里可能出现活动主题不存在、父主题缺失、
         * 循环继承、路径非法等异常。
         */
        try {
            resolvedTemplate =
                themeTemplateResolver.resolve(
                    request.templateType()
                );
        } catch (Exception error) {
            return emergencyOrFailure(
                request,
                "模板解析失败："
                    + safeErrorMessage(error)
            );
        }

        if (resolvedTemplate == null) {
            return emergencyOrFailure(
                request,
                "模板解析器返回了 null。"
            );
        }

        /*
         * 正常情况下核心 fallback 已经保证内置模板存在。
         *
         * 如果程序资源损坏导致 exists=false，
         * 则直接进入最后的紧急静态页面。
         */
        if (!resolvedTemplate.exists()) {
            return emergencyOrFailure(
                request,
                "最终模板不存在："
                    + resolvedTemplate
                        .absoluteTemplatePath()
            );
        }

        final ThemeEngine themeEngine;

        /*
         * 在查询模板引擎注册中心之前，
         * 必须先验证最终模板是否声明了有效的 engineId。
         *
         * 这样可以区分：
         *
         * 1. 主题根本没有声明模板引擎；
         * 2. 主题声明了模板引擎，但系统没有注册该引擎。
         *
         * 两种错误的诊断含义不同，不能混为一谈。
         */
        String engineId =
            resolvedTemplate.engineId();

        if (
            engineId == null
                || engineId.isBlank()
        ) {
            return emergencyOrFailure(
                request,
                "当前主题没有声明模板引擎。"
            );
        }

        /*
         * 第二阶段：根据最终模板自己的 engineId
         * 选择真正的模板引擎。
         *
         * 例如 Pebble 活动主题可以回退到
         * Thymeleaf default 或 Thymeleaf 核心 fallback。
         */
        try {
            themeEngine =
                themeEngineRegistry.require(
                    engineId
                );
        } catch (Exception error) {
            return emergencyOrFailure(
                request,
                "模板引擎选择失败："
                    + safeErrorMessage(error)
            );
        }

        if (themeEngine == null) {
            return emergencyOrFailure(
                request,
                "模板引擎注册中心返回了 null："
                    + engineId
            );
        }

        final TemplateRenderResult renderResult;

        /*
         * 第三阶段：调用实际模板引擎执行渲染。
         */
        try {
            renderResult = themeEngine.render(
                request,
                resolvedTemplate
            );
        } catch (Exception error) {
            return emergencyOrFailure(
                request,
                "模板引擎执行失败："
                    + safeErrorMessage(error)
            );
        }

        /*
         * 第三方引擎或未来扩展引擎不应返回 null，
         * 但统一入口仍然需要进行最后保护。
         */
        if (renderResult == null) {
            return emergencyOrFailure(
                request,
                "模板引擎返回了 null 渲染结果。"
            );
        }

        /*
         * 模板引擎正常返回失败结果时，
         * 正式生产环境进入紧急页面。
         *
         * 兼容构造模式保留原始失败结果，
         * 防止破坏前面已经建立的自动化测试语义。
         */
        if (!renderResult.success()) {
            if (emergencyTemplateRenderer == null) {
                return renderResult;
            }

            return renderEmergencySafely(
                request,
                "模板引擎返回失败结果："
                    + normalizeFailureMessage(
                        renderResult.errorMessage()
                    )
            );
        }

        return renderResult;
    }

    /**
     * 根据当前运行模式返回紧急页面或普通失败结果。
     *
     * @param request 原模板请求
     * @param errorMessage 内部失败说明
     * @return 紧急页面或 TemplateRenderResult.failure()
     */
    private TemplateRenderResult emergencyOrFailure(
        TemplateRenderRequest request,
        String errorMessage
    ) {
        if (emergencyTemplateRenderer == null) {
            return TemplateRenderResult.failure(
                normalizeFailureMessage(
                    errorMessage
                )
            );
        }

        return renderEmergencySafely(
            request,
            errorMessage
        );
    }

    /**
     * 安全调用最小紧急静态页面。
     *
     * <p>
     * EmergencyTemplateRenderer 按设计不应失败，
     * 但最后保护层仍然进行异常捕获。
     * 如果紧急页面自身出现意外异常，
     * 则返回普通失败结果，避免递归调用。
     * </p>
     *
     * @param request 原模板请求
     * @param originalError 原始模板失败说明
     * @return 紧急页面结果或最终失败结果
     */
    private TemplateRenderResult renderEmergencySafely(
        TemplateRenderRequest request,
        String originalError
    ) {
        try {
            TemplateRenderResult emergencyResult =
                emergencyTemplateRenderer.render(
                    request
                );

            if (emergencyResult != null) {
                return emergencyResult;
            }

            return TemplateRenderResult.failure(
                "紧急页面渲染器返回了 null。原始错误："
                    + normalizeFailureMessage(
                        originalError
                    )
            );
        } catch (Exception emergencyError) {
            return TemplateRenderResult.failure(
                "紧急页面渲染失败："
                    + safeErrorMessage(
                        emergencyError
                    )
                    + "。原始错误："
                    + normalizeFailureMessage(
                        originalError
                    )
            );
        }
    }

    /**
     * 获取可安全用于内部失败结果的异常说明。
     *
     * <p>
     * 该信息不会直接写入紧急 HTML，
     * 只用于失败结果和后续日志诊断。
     * </p>
     *
     * @param error 捕获到的异常
     * @return 非空异常说明
     */
    private String safeErrorMessage(
        Exception error
    ) {
        if (error == null) {
            return "未知异常";
        }

        if (
            error.getMessage() == null
                || error.getMessage().isBlank()
        ) {
            return error
                .getClass()
                .getSimpleName();
        }

        return error.getMessage().trim();
    }

    /**
     * 标准化普通失败说明。
     *
     * @param value 原始说明
     * @return 非空失败说明
     */
    private String normalizeFailureMessage(
        String value
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            return "模板渲染失败，但未提供具体错误信息。";
        }

        return value.trim();
    }
}
