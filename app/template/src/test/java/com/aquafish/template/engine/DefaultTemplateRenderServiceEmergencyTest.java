package com.aquafish.template.engine;


import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.core.TemplateTypes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.template.emergency.EmergencyTemplateRenderer;
import com.aquafish.template.resolve.CoreFallbackTemplateResolver;
import com.aquafish.template.resolve.ThemeTemplateResolver;
import com.aquafish.theme.core.ActiveThemeResolver;
import com.aquafish.theme.core.DefaultThemeResolver;
import com.aquafish.theme.core.ThemeInheritanceResolver;
import com.aquafish.theme.core.ThemeParentResolver;
import com.aquafish.theme.core.ThemeScanner;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DefaultTemplateRenderService 紧急页面完整集成测试。
 *
 * <p>
 * 验证第 38 步接入后的统一模板渲染链：
 * </p>
 *
 * <pre>
 * TemplateRenderRequest
 * -> ThemeTemplateResolver
 * -> ThemeEngineRegistry
 * -> ThemeEngine
 * -> 正常 TemplateRenderResult
 *
 * 解析、引擎选择或模板渲染失败
 * -> EmergencyTemplateRenderer
 * -> 最小紧急静态页面
 * </pre>
 *
 * <p>
 * 测试使用真实主题扫描器、真实模板解析器、
 * 真实核心 fallback 和真实 Thymeleaf 渲染器。
 * </p>
 *
 * <p>
 * 对于需要人为制造失败的场景，
 * 使用 Java 动态代理创建 ThemeEngine 测试替身。
 * </p>
 */
class DefaultTemplateRenderServiceEmergencyTest {

    /**
     * JUnit 临时 Aquafish 工作目录。
     *
     * 测试不会修改用户真实主题目录。
     */
    @TempDir
    Path temporaryWorkDir;

    /**
     * 动态测试模板引擎的运行行为。
     */
    private enum EngineBehavior {

        /**
         * 如果该引擎被意外调用，
         * 返回普通失败结果。
         */
        UNUSED,

        /**
         * 模板引擎主动返回 success=false。
         */
        RETURN_FAILURE,

        /**
         * 模板引擎直接抛出运行异常。
         */
        THROW_EXCEPTION
    }

    /**
     * 验证正式三参数构造模式下，
     * 空请求会进入紧急页面。
     *
     * @throws Exception 注册测试引擎失败时抛出
     */
    @Test
    void shouldUseEmergencyPageWhenRequestIsNull()
        throws Exception {

        ThemeTemplateResolver resolver =
            createTemplateResolver(
                "missing-theme"
            );

        ThemeEngineRegistry registry =
            createThemeEngineRegistry(
                createThemeEngineProxy(
                    "unused-engine",
                    EngineBehavior.UNUSED
                )
            );

        DefaultTemplateRenderService service =
            new DefaultTemplateRenderService(
                resolver,
                registry,
                new EmergencyTemplateRenderer()
            );

        TemplateRenderResult result =
            service.render(null);

        assertEmergencyResult(
            result
        );

        assertTrue(
            result.html().contains(
                "requested-template: unknown"
            )
        );
    }

    /**
     * 验证活动主题不存在导致模板解析失败时，
     * 统一服务会捕获异常并进入紧急页面。
     *
     * @throws Exception 注册测试引擎失败时抛出
     */
    @Test
    void shouldUseEmergencyPageWhenThemeResolutionFails()
        throws Exception {

        /*
         * 不创建 missing-theme。
         */
        ThemeTemplateResolver resolver =
            createTemplateResolver(
                "missing-theme"
            );

        ThemeEngineRegistry registry =
            createThemeEngineRegistry(
                createThemeEngineProxy(
                    "unused-engine",
                    EngineBehavior.UNUSED
                )
            );

        DefaultTemplateRenderService service =
            new DefaultTemplateRenderService(
                resolver,
                registry,
                new EmergencyTemplateRenderer()
            );

        TemplateRenderResult result =
            service.render(
                TemplateRenderRequest.of(
                    TemplateTypes.INDEX,
                    Map.of()
                )
            );

        assertEmergencyResult(
            result
        );

        assertTrue(
            result.html().contains(
                "requested-template: index.html"
            )
        );

        /*
         * 内部主题错误不得泄露给访客。
         */
        assertFalse(
            result.html().contains(
                "missing-theme"
            )
        );

        assertFalse(
            result.html().contains(
                "Exception"
            )
        );
    }

    /**
     * 验证最终模板所需引擎没有注册时，
     * 统一服务会进入紧急页面。
     *
     * @throws Exception 创建主题或注册引擎失败时抛出
     */
    @Test
    void shouldUseEmergencyPageWhenRequiredEngineIsMissing()
        throws Exception {

        /*
         * 创建一个空 Pebble 活动主题。
         *
         * index.html 最终会回退到核心 fallback，
         * 核心 fallback 固定要求 thymeleaf。
         */
        createTheme(
            "empty-theme",
            "empty-theme",
            "pebble",
            null,
            null
        );

        ThemeTemplateResolver resolver =
            createTemplateResolver(
                "empty-theme"
            );

        /*
         * 注册中心故意只注册 other-engine。
         */
        ThemeEngineRegistry registry =
            createThemeEngineRegistry(
                createThemeEngineProxy(
                    "other-engine",
                    EngineBehavior.UNUSED
                )
            );

        DefaultTemplateRenderService service =
            new DefaultTemplateRenderService(
                resolver,
                registry,
                new EmergencyTemplateRenderer()
            );

        TemplateRenderResult result =
            service.render(
                TemplateRenderRequest.of(
                    TemplateTypes.INDEX,
                    Map.of()
                )
            );

        assertEmergencyResult(
            result
        );

        assertTrue(
            result.html().contains(
                "requested-template: index.html"
            )
        );
    }

    /**
     * 验证模板引擎主动返回失败结果时，
     * 正式生产构造模式会进入紧急页面。
     *
     * @throws Exception 创建主题或注册引擎失败时抛出
     */
    @Test
    void shouldUseEmergencyPageWhenEngineReturnsFailure()
        throws Exception {

        createTheme(
            "failure-theme",
            "failure-theme",
            "thymeleaf",
            "index.html",
            "<html><body>不会显示</body></html>"
        );

        ThemeTemplateResolver resolver =
            createTemplateResolver(
                "failure-theme"
            );

        ThemeEngineRegistry registry =
            createThemeEngineRegistry(
                createThemeEngineProxy(
                    "thymeleaf",
                    EngineBehavior.RETURN_FAILURE
                )
            );

        DefaultTemplateRenderService service =
            new DefaultTemplateRenderService(
                resolver,
                registry,
                new EmergencyTemplateRenderer()
            );

        TemplateRenderResult result =
            service.render(
                TemplateRenderRequest.of(
                    TemplateTypes.INDEX,
                    Map.of()
                )
            );

        assertEmergencyResult(
            result
        );

        /*
         * 模板引擎内部错误不能进入访客 HTML。
         */
        assertFalse(
            result.html().contains(
                "测试引擎主动返回失败"
            )
        );
    }

    /**
     * 验证模板引擎直接抛出异常时，
     * 异常不会继续抛到页面控制器。
     *
     * @throws Exception 创建主题或注册引擎失败时抛出
     */
    @Test
    void shouldUseEmergencyPageWhenEngineThrowsException()
        throws Exception {

        createTheme(
            "exception-theme",
            "exception-theme",
            "thymeleaf",
            "index.html",
            "<html><body>不会显示</body></html>"
        );

        ThemeTemplateResolver resolver =
            createTemplateResolver(
                "exception-theme"
            );

        ThemeEngineRegistry registry =
            createThemeEngineRegistry(
                createThemeEngineProxy(
                    "thymeleaf",
                    EngineBehavior.THROW_EXCEPTION
                )
            );

        DefaultTemplateRenderService service =
            new DefaultTemplateRenderService(
                resolver,
                registry,
                new EmergencyTemplateRenderer()
            );

        TemplateRenderResult result =
            service.render(
                TemplateRenderRequest.of(
                    TemplateTypes.INDEX,
                    Map.of()
                )
            );

        assertEmergencyResult(
            result
        );

        assertFalse(
            result.html().contains(
                "测试模板引擎故意抛出异常"
            )
        );

        assertFalse(
            result.html().contains(
                "IllegalStateException"
            )
        );
    }

    /**
     * 验证正常 Thymeleaf 页面成功渲染时，
     * 不会被紧急页面覆盖。
     *
     * @throws Exception 创建主题或注册引擎失败时抛出
     */
    @Test
    void shouldKeepNormalSuccessfulThymeleafRendering()
        throws Exception {

        createTheme(
            "normal-theme",
            "normal-theme",
            "thymeleaf",
            "index.html",
            """
            <!doctype html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <title>正常渲染测试</title>
            </head>
            <body>
                <main>
                    正常 Thymeleaf 渲染成功
                </main>
            </body>
            </html>
            """
        );

        ThemeTemplateResolver resolver =
            createTemplateResolver(
                "normal-theme"
            );

        ThymeleafTemplateRenderService thymeleafEngine =
            new ThymeleafTemplateRenderService(
                resolver
            );

        ThemeEngineRegistry registry =
            createThemeEngineRegistry(
                thymeleafEngine
            );

        EmergencyTemplateRenderer emergencyRenderer =
            new EmergencyTemplateRenderer();

        DefaultTemplateRenderService service =
            new DefaultTemplateRenderService(
                resolver,
                registry,
                emergencyRenderer
            );

        TemplateRenderResult result =
            service.render(
                TemplateRenderRequest.of(
                    TemplateTypes.INDEX,
                    Map.of()
                )
            );

        assertTrue(
            result.success()
        );

        assertEquals(
            "normal-theme",
            result.themeName()
        );

        assertTrue(
            result.html().contains(
                "正常 Thymeleaf 渲染成功"
            )
        );

        assertFalse(
            emergencyRenderer.isEmergencyResult(
                result
            )
        );

        assertFalse(
            result.templatePath().startsWith(
                "inline:/"
            )
        );
    }

    /**
     * 验证旧双参数构造方法继续保留失败结果语义。
     *
     * @throws Exception 注册测试引擎失败时抛出
     */
    @Test
    void shouldKeepLegacyFailureBehaviorForTwoArgumentConstructor()
        throws Exception {

        ThemeTemplateResolver resolver =
            createTemplateResolver(
                "missing-theme"
            );

        ThemeEngineRegistry registry =
            createThemeEngineRegistry(
                createThemeEngineProxy(
                    "unused-engine",
                    EngineBehavior.UNUSED
                )
            );

        DefaultTemplateRenderService service =
            new DefaultTemplateRenderService(
                resolver,
                registry
            );

        TemplateRenderResult result =
            service.render(null);

        assertFalse(
            result.success()
        );

        assertTrue(
            result.errorMessage().contains(
                "模板渲染请求不能为空"
            )
        );
    }

    /**
     * 统一验证紧急页面结果。
     *
     * @param result 实际渲染结果
     */
    private void assertEmergencyResult(
        TemplateRenderResult result
    ) {
        EmergencyTemplateRenderer renderer =
            new EmergencyTemplateRenderer();

        assertTrue(
            result.success()
        );

        assertEquals(
            "aquafish-emergency",
            result.themeName()
        );

        assertEquals(
            "inline:/aquafish/emergency.html",
            result.templatePath()
        );

        assertTrue(
            result.html().contains(
                "最小紧急安全页面"
            )
        );

        assertTrue(
            result.html().contains(
                "页面暂时无法正常显示"
            )
        );

        assertFalse(
            result.cacheHit()
        );

        assertNull(
            result.errorMessage()
        );

        assertTrue(
            renderer.isEmergencyResult(
                result
            )
        );
    }

    /**
     * 创建完整生产模板解析链。
     *
     * @param activeThemeName 当前活动主题标识
     * @return 正式 ThemeTemplateResolver
     */
    private ThemeTemplateResolver createTemplateResolver(
        String activeThemeName
    ) {
        AquafishProperties properties =
            new AquafishProperties(
                temporaryWorkDir.toString(),
                "http://127.0.0.1:8080",
                "aq_",
                activeThemeName
            );

        WorkDirResolver workDirResolver =
            new WorkDirResolver(
                properties
            );

        ThemeScanner themeScanner =
            new ThemeScanner(
                workDirResolver
            );

        ActiveThemeResolver activeThemeResolver =
            new ActiveThemeResolver(
                properties,
                themeScanner
            );

        ThemeParentResolver parentResolver =
            new ThemeParentResolver(
                themeScanner
            );

        ThemeInheritanceResolver inheritanceResolver =
            new ThemeInheritanceResolver(
                parentResolver
            );

        DefaultThemeResolver defaultThemeResolver =
            new DefaultThemeResolver(
                themeScanner
            );

        CoreFallbackTemplateResolver coreResolver =
            new CoreFallbackTemplateResolver();

        return new ThemeTemplateResolver(
            activeThemeResolver,
            inheritanceResolver,
            defaultThemeResolver,
            coreResolver
        );
    }

    /**
     * 创建真实临时测试主题。
     *
     * @param directoryName 主题目录名称
     * @param themeId 主题唯一标识
     * @param engine thymeleaf 或 pebble
     * @param templateRelativePath 模板相对路径；
     *                             不创建时传入 null
     * @param templateContent 模板内容
     * @throws Exception 创建目录或文件失败时抛出
     */
    private void createTheme(
        String directoryName,
        String themeId,
        String engine,
        String templateRelativePath,
        String templateContent
    ) throws Exception {
        Path themeDirectory = temporaryWorkDir
            .resolve("themes")
            .resolve(directoryName);

        Path templatesDirectory =
            themeDirectory.resolve("templates");

        Path assetsDirectory =
            themeDirectory.resolve("assets");

        Files.createDirectories(
            templatesDirectory
        );

        Files.createDirectories(
            assetsDirectory
        );

        String themeYaml =
            "id: " + themeId + "\n"
                + "title: \""
                + themeId
                + " 紧急回退测试主题\"\n"
                + "version: 1.0.0\n"
                + "engine: "
                + engine
                + "\n"
                + "author:\n"
                + "  name: Aquafish Test\n"
                + "description: \"统一渲染紧急回退测试。\"\n";

        Files.writeString(
            themeDirectory.resolve(
                "theme.yaml"
            ),
            themeYaml,
            StandardCharsets.UTF_8
        );

        Files.writeString(
            themeDirectory.resolve(
                "settings.yaml"
            ),
            "# 测试主题设置\n",
            StandardCharsets.UTF_8
        );

        if (
            templateRelativePath == null
                || templateRelativePath.isBlank()
        ) {
            return;
        }

        Path templateFile =
            templatesDirectory.resolve(
                templateRelativePath.replace(
                    "/",
                    java.io.File.separator
                )
            );

        Files.createDirectories(
            templateFile.getParent()
        );

        Files.writeString(
            templateFile,
            templateContent == null
                ? "<html><body>测试模板</body></html>"
                : templateContent,
            StandardCharsets.UTF_8
        );
    }

    /**
     * 创建动态 ThemeEngine 测试替身。
     *
     * @param engineId 引擎唯一标识
     * @param behavior render 阶段行为
     * @return ThemeEngine 动态代理对象
     * @throws Exception 加载 ThemeEngine 接口失败时抛出
     */
    private Object createThemeEngineProxy(
        String engineId,
        EngineBehavior behavior
    ) throws Exception {
        Class<?> themeEngineType =
            Class.forName(
                "com.aquafish.template.engine.ThemeEngine"
            );

        InvocationHandler handler =
            (
                Object proxy,
                Method method,
                Object[] arguments
            ) -> {
                String methodName =
                    method.getName();

                /*
                 * Object 基础方法。
                 */
                if (
                    methodName.equals("toString")
                        && method.getParameterCount() == 0
                ) {
                    return "TestThemeEngine["
                        + engineId
                        + "]";
                }

                if (
                    methodName.equals("hashCode")
                        && method.getParameterCount() == 0
                ) {
                    return System.identityHashCode(
                        proxy
                    );
                }

                if (
                    methodName.equals("equals")
                        && method.getParameterCount() == 1
                ) {
                    return proxy == arguments[0];
                }

                /*
                 * 模板引擎唯一标识方法返回 engineId。
                 */
                if (
                    method.getReturnType()
                        .equals(String.class)
                ) {
                    return engineId;
                }

                /*
                 * 模板渲染方法。
                 */
                if (
                    methodName.equals("render")
                        && TemplateRenderResult.class
                            .isAssignableFrom(
                                method.getReturnType()
                            )
                ) {
                    if (
                        behavior
                            == EngineBehavior
                                .THROW_EXCEPTION
                    ) {
                        throw new IllegalStateException(
                            "测试模板引擎故意抛出异常。"
                        );
                    }

                    if (
                        behavior
                            == EngineBehavior
                                .RETURN_FAILURE
                    ) {
                        return TemplateRenderResult.failure(
                            "测试引擎主动返回失败。"
                        );
                    }

                    return TemplateRenderResult.failure(
                        "未使用的测试引擎被意外调用。"
                    );
                }

                if (
                    method.getReturnType()
                        .equals(boolean.class)
                    || method.getReturnType()
                        .equals(Boolean.class)
                ) {
                    return true;
                }

                if (
                    method.getReturnType()
                        .equals(int.class)
                    || method.getReturnType()
                        .equals(Integer.class)
                ) {
                    return 0;
                }

                if (
                    method.getReturnType()
                        .equals(long.class)
                    || method.getReturnType()
                        .equals(Long.class)
                ) {
                    return 0L;
                }

                if (
                    Optional.class.isAssignableFrom(
                        method.getReturnType()
                    )
                ) {
                    return Optional.empty();
                }

                return null;
            };

        return Proxy.newProxyInstance(
            themeEngineType.getClassLoader(),
            new Class<?>[] {
                themeEngineType
            },
            handler
        );
    }

    /**
     * 根据 ThemeEngineRegistry 的真实构造方法
     * 创建模板引擎注册中心。
     *
     * <p>
     * 自动兼容以下构造参数：
     * </p>
     *
     * <ul>
     *     <li>List 或 Collection；</li>
     *     <li>Set；</li>
     *     <li>Map；</li>
     *     <li>ThemeEngine 数组或可变参数；</li>
     *     <li>单个 ThemeEngine。</li>
     * </ul>
     *
     * @param engines 要注册的模板引擎
     * @return 创建完成的注册中心
     * @throws Exception 无法适配构造方法时抛出
     */
    private ThemeEngineRegistry createThemeEngineRegistry(
        Object... engines
    ) throws Exception {
        Class<?> themeEngineType =
            Class.forName(
                "com.aquafish.template.engine.ThemeEngine"
            );

        for (Object engine : engines) {
            if (!themeEngineType.isInstance(engine)) {
                throw new IllegalArgumentException(
                    "对象没有实现 ThemeEngine："
                        + engine
                );
            }
        }

        List<Constructor<?>> constructors =
            new ArrayList<>(
                List.of(
                    ThemeEngineRegistry.class
                        .getDeclaredConstructors()
                )
            );

        constructors.sort(
            Comparator
                .comparingInt(
                    (
                        Constructor<?> constructor
                    ) -> constructor
                        .getParameterCount()
                )
                .reversed()
        );

        List<String> failures =
            new ArrayList<>();

        for (
            Constructor<?> constructor
                : constructors
        ) {
            Object[] arguments =
                buildRegistryConstructorArguments(
                    constructor
                        .getParameterTypes(),
                    themeEngineType,
                    engines
                );

            if (arguments == null) {
                continue;
            }

            try {
                constructor.setAccessible(true);

                return (ThemeEngineRegistry)
                    constructor.newInstance(
                        arguments
                    );
            } catch (
                ReflectiveOperationException
                    | RuntimeException error
            ) {
                failures.add(
                    constructor.toGenericString()
                        + " -> "
                        + error
                            .getClass()
                            .getSimpleName()
                        + ": "
                        + error.getMessage()
                );
            }
        }

        throw new IllegalStateException(
            "无法根据真实构造方法创建 "
                + "ThemeEngineRegistry。尝试结果："
                + failures
        );
    }

    /**
     * 为注册中心构造方法生成参数。
     *
     * @param parameterTypes 构造参数类型
     * @param themeEngineType ThemeEngine 接口类型
     * @param engines 要注册的模板引擎
     * @return 构造参数；无法适配时返回 null
     */
    private Object[] buildRegistryConstructorArguments(
        Class<?>[] parameterTypes,
        Class<?> themeEngineType,
        Object[] engines
    ) {
        if (parameterTypes.length != 1) {
            return null;
        }

        Class<?> parameterType =
            parameterTypes[0];

        /*
         * Set 必须先于普通 Collection 判断。
         */
        if (
            Set.class.isAssignableFrom(
                parameterType
            )
        ) {
            return new Object[] {
                new LinkedHashSet<>(
                    List.of(engines)
                )
            };
        }

        /*
         * List、Collection 或 Iterable。
         */
        if (
            parameterType
                .isAssignableFrom(
                    ArrayList.class
                )
            || Collection.class
                .isAssignableFrom(
                    parameterType
                )
            || Iterable.class.equals(
                parameterType
            )
        ) {
            return new Object[] {
                new ArrayList<>(
                    List.of(engines)
                )
            };
        }

        /*
         * Map<String, ThemeEngine>。
         */
        if (
            Map.class.isAssignableFrom(
                parameterType
            )
        ) {
            Map<String, Object> engineMap =
                new LinkedHashMap<>();

            for (Object engine : engines) {
                engineMap.put(
                    readEngineId(engine),
                    engine
                );
            }

            return new Object[] {
                engineMap
            };
        }

        /*
         * ThemeEngine[] 或 ThemeEngine...。
         */
        if (
            parameterType.isArray()
                && parameterType
                    .getComponentType()
                    .isAssignableFrom(
                        themeEngineType
                    )
        ) {
            Object engineArray =
                Array.newInstance(
                    themeEngineType,
                    engines.length
                );

            for (
                int index = 0;
                index < engines.length;
                index++
            ) {
                Array.set(
                    engineArray,
                    index,
                    engines[index]
                );
            }

            return new Object[] {
                engineArray
            };
        }

        /*
         * 单个 ThemeEngine。
         */
        if (
            engines.length == 1
                && parameterType.isInstance(
                    engines[0]
                )
        ) {
            return new Object[] {
                engines[0]
            };
        }

        return null;
    }

    /**
     * 从模板引擎中读取唯一标识。
     *
     * @param engine 模板引擎
     * @return 引擎唯一标识
     */
    private String readEngineId(
        Object engine
    ) {
        String[] candidateMethodNames = {
            "engineId",
            "id",
            "getEngineId",
            "getId"
        };

        for (
            String methodName
                : candidateMethodNames
        ) {
            try {
                Method method =
                    engine
                        .getClass()
                        .getMethod(
                            methodName
                        );

                Object value =
                    method.invoke(
                        engine
                    );

                if (
                    value
                        instanceof String stringValue
                    && !stringValue.isBlank()
                ) {
                    return stringValue.trim();
                }
            } catch (
                ReflectiveOperationException ignored
            ) {
                /*
                 * 当前候选方法不存在时，
                 * 继续尝试下一个名称。
                 */
            }
        }

        throw new IllegalStateException(
            "无法读取 ThemeEngine 唯一标识："
                + engine
        );
    }
}
