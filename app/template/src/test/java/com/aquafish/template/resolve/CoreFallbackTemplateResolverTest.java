package com.aquafish.template.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.template.core.TemplateType;
import com.aquafish.template.core.TemplateTypes;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * CoreFallbackTemplateResolver 自动化测试。
 *
 * <p>
 * 本测试验证第 30 步写入 template 模块 classpath 的
 * 核心内置只读 fallback 模板。
 * </p>
 *
 * <p>测试调用链：</p>
 *
 * <pre>
 * TemplateTypes
 * -> CoreFallbackTemplateResolver
 * -> ClassPathResource
 * -> 核心内置 HTML 模板
 * </pre>
 *
 * <p>主要验证：</p>
 *
 * <ol>
 *     <li>
 *         平台当前 16 个内置模板类型都有核心 fallback；
 *     </li>
 *     <li>
 *         所有资源都已经进入测试运行时 classpath；
 *     </li>
 *     <li>
 *         核心 fallback 固定使用 Thymeleaf；
 *     </li>
 *     <li>
 *         核心 fallback 使用固定虚拟主题名称；
 *     </li>
 *     <li>
 *         解析结果使用 classpath:/ 资源路径；
 *     </li>
 *     <li>
 *         每个 HTML 文件都可以按照 UTF-8 正确读取；
 *     </li>
 *     <li>
 *         中文安全提示内容不会出现编码损坏；
 *     </li>
 *     <li>
 *         不存在的核心资源安全返回空结果；
 *     </li>
 *     <li>
 *         根据模板类型 key 可以正确解析；
 *     </li>
 *     <li>
 *         全部核心模板结果列表不可修改。
 *     </li>
 * </ol>
 */
class CoreFallbackTemplateResolverTest {

    /**
     * 当前 Aquafish 已经定义的核心 fallback 模板数量。
     *
     * <p>
     * 如果未来新增正式模板类型，
     * 必须同步新增对应的核心 fallback 资源，
     * 然后明确更新该数量。
     * </p>
     */
    private static final int EXPECTED_TEMPLATE_COUNT =
        16;

    /**
     * 验证全部内置模板类型都能解析到核心 fallback。
     *
     * @throws Exception 当 classpath 资源读取失败时抛出
     */
    @Test
    void shouldResolveAllBuiltInCoreFallbackTemplates()
        throws Exception {

        CoreFallbackTemplateResolver resolver =
            new CoreFallbackTemplateResolver();

        List<TemplateType> templateTypes =
            TemplateTypes.all();

        List<ResolvedTemplate> results =
            resolver.resolveAllBuiltInTypes();

        /*
         * 锁定当前平台基础模板数量。
         *
         * 新增 TemplateType 时，测试会立即提醒开发者
         * 同步增加核心 fallback。
         */
        assertEquals(
            EXPECTED_TEMPLATE_COUNT,
            templateTypes.size()
        );

        assertEquals(
            templateTypes.size(),
            results.size()
        );

        for (
            int index = 0;
            index < templateTypes.size();
            index++
        ) {
            TemplateType templateType =
                templateTypes.get(index);

            ResolvedTemplate result =
                results.get(index);

            String expectedResourcePath =
                CoreFallbackTemplateResolver
                    .CORE_FALLBACK_RESOURCE_ROOT
                    + templateType
                        .defaultTemplatePath();

            String expectedAbsolutePath =
                "classpath:/"
                    + expectedResourcePath;

            assertTrue(
                result.exists(),
                "核心 fallback 应存在："
                    + templateType
                        .defaultTemplatePath()
            );

            assertEquals(
                CoreFallbackTemplateResolver
                    .CORE_FALLBACK_THEME_NAME,
                result.themeName()
            );

            assertEquals(
                "aquafish-core-fallback",
                result.themeName()
            );

            /*
             * 核心 fallback 固定使用 Thymeleaf，
             * 不随后台活动主题引擎变化。
             */
            assertEquals(
                CoreFallbackTemplateResolver
                    .CORE_FALLBACK_ENGINE_ID,
                result.engineId()
            );

            assertEquals(
                "thymeleaf",
                result.engineId()
            );

            assertEquals(
                templateType
                    .defaultTemplatePath(),
                result.relativeTemplatePath()
            );

            assertEquals(
                expectedAbsolutePath,
                result.absoluteTemplatePath()
            );

            assertTrue(
                result.message().contains(
                    "核心内置只读 fallback"
                )
            );

            assertTrue(
                resolver.isCoreFallback(result)
            );

            /*
             * 使用 Spring ClassPathResource
             * 真实检查资源是否已经进入运行时 classpath。
             */
            Resource resource =
                new ClassPathResource(
                    expectedResourcePath
                );

            assertTrue(
                resource.exists(),
                "classpath 资源不存在："
                    + expectedResourcePath
            );

            assertTrue(
                resource.isReadable(),
                "classpath 资源不可读："
                    + expectedResourcePath
            );

            /*
             * 按 UTF-8 读取 HTML，
             * 确认中文没有被错误编码。
             */
            String html;

            try (
                InputStream inputStream =
                    resource.getInputStream()
            ) {
                html = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
                );
            }

            assertTrue(
                html.contains("<!doctype html>"),
                "核心模板缺少 HTML 文档声明："
                    + expectedResourcePath
            );

            assertTrue(
                html.contains("Aquafish"),
                "核心模板缺少 Aquafish 标识："
                    + expectedResourcePath
            );

            assertTrue(
                html.contains("核心内置安全模板"),
                "核心模板 UTF-8 中文内容异常："
                    + expectedResourcePath
            );

            assertTrue(
                html.contains(
                    "系统已自动切换到随核心程序发布的只读页面"
                ),
                "核心模板安全说明缺失或出现乱码："
                    + expectedResourcePath
            );
        }
    }

    /**
     * 验证可以根据模板类型唯一 key 解析核心 fallback。
     */
    @Test
    void shouldResolveCoreFallbackByTemplateTypeKey() {
        CoreFallbackTemplateResolver resolver =
            new CoreFallbackTemplateResolver();

        Optional<ResolvedTemplate> result =
            resolver.resolve("index");

        assertTrue(
            result.isPresent()
        );

        ResolvedTemplate template =
            result.orElseThrow();

        assertTrue(
            template.exists()
        );

        assertEquals(
            "index.html",
            template.relativeTemplatePath()
        );

        assertEquals(
            "thymeleaf",
            template.engineId()
        );

        assertTrue(
            resolver.isCoreFallback(template)
        );
    }

    /**
     * 验证 require 可以获取必须存在的核心模板。
     */
    @Test
    void shouldRequireExistingCoreFallback() {
        CoreFallbackTemplateResolver resolver =
            new CoreFallbackTemplateResolver();

        TemplateType indexType =
            TemplateTypes.require("index");

        ResolvedTemplate result =
            resolver.require(indexType);

        assertTrue(
            result.exists()
        );

        assertEquals(
            "classpath:/aquafish/core-fallback/templates/index.html",
            result.absoluteTemplatePath()
        );
    }

    /**
     * 验证不存在的 classpath 核心模板返回空结果。
     *
     * <p>
     * 当前测试类型不是平台注册类型，
     * 只用于验证解析器缺失资源分支。
     * </p>
     */
    @Test
    void shouldReturnEmptyWhenCoreFallbackResourceIsMissing() {
        CoreFallbackTemplateResolver resolver =
            new CoreFallbackTemplateResolver();

        TemplateType missingType =
            new TemplateType(
                "missing-core-fallback-test",
                "missing/not-found.html",
                "不存在的核心模板",
                "用于验证核心 fallback 资源缺失处理。"
            );

        Optional<ResolvedTemplate> result =
            resolver.resolve(missingType);

        assertTrue(
            result.isEmpty()
        );

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () -> resolver.require(
                    missingType
                )
            );

        assertTrue(
            error.getMessage().contains(
                "核心内置 fallback 模板不存在"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "missing/not-found.html"
            )
        );
    }

    /**
     * 验证解析器拒绝 null 模板类型。
     *
     * <p>
     * 因为 resolve 同时存在 String 和 TemplateType 重载，
     * 这里显式转换为 TemplateType，避免 Java 调用歧义。
     * </p>
     */
    @Test
    void shouldRejectNullTemplateType() {
        CoreFallbackTemplateResolver resolver =
            new CoreFallbackTemplateResolver();

        IllegalArgumentException error =
            assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(
                    (TemplateType) null
                )
            );

        assertTrue(
            error.getMessage().contains(
                "核心 fallback 模板类型不能为空"
            )
        );
    }

    /**
     * 验证 isCoreFallback 只识别真正的核心解析结果。
     */
    @Test
    void shouldIdentifyOnlyCoreFallbackResults() {
        CoreFallbackTemplateResolver resolver =
            new CoreFallbackTemplateResolver();

        ResolvedTemplate coreResult =
            resolver.require(
                TemplateTypes.require("index")
            );

        assertTrue(
            resolver.isCoreFallback(
                coreResult
            )
        );

        /*
         * null 不属于核心 fallback。
         */
        assertFalse(
            resolver.isCoreFallback(null)
        );

        ResolvedTemplate normalThemeResult =
            new ResolvedTemplate(
                TemplateTypes.require("index"),
                "custom-theme",
                "pebble",
                "index.html",
                "classpath:/custom-theme/index.html",
                true,
                "普通主题测试结果。"
            );

        assertFalse(
            resolver.isCoreFallback(
                normalThemeResult
            )
        );
    }

    /**
     * 验证核心 fallback 的协议常量保持稳定。
     */
    @Test
    void shouldExposeStableCoreFallbackConstants() {
        assertEquals(
            "aquafish-core-fallback",
            CoreFallbackTemplateResolver
                .CORE_FALLBACK_THEME_NAME
        );

        assertEquals(
            "thymeleaf",
            CoreFallbackTemplateResolver
                .CORE_FALLBACK_ENGINE_ID
        );

        assertEquals(
            "aquafish/core-fallback/templates/",
            CoreFallbackTemplateResolver
                .CORE_FALLBACK_RESOURCE_ROOT
        );
    }

    /**
     * 验证全部核心模板列表不能被外部修改。
     */
    @Test
    void shouldReturnImmutableCoreFallbackList() {
        CoreFallbackTemplateResolver resolver =
            new CoreFallbackTemplateResolver();

        List<ResolvedTemplate> results =
            resolver.resolveAllBuiltInTypes();

        assertEquals(
            EXPECTED_TEMPLATE_COUNT,
            results.size()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> results.remove(0)
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> results.add(
                results.get(0)
            )
        );
    }
}
