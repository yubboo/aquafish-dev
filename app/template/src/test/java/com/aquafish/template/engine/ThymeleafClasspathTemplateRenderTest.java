package com.aquafish.template.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.core.TemplateType;
import com.aquafish.template.core.TemplateTypes;
import com.aquafish.template.resolve.CoreFallbackTemplateResolver;
import com.aquafish.template.resolve.ResolvedTemplate;
import com.aquafish.template.resolve.ThemeTemplateResolver;
import com.aquafish.theme.core.ActiveThemeResolver;
import com.aquafish.theme.core.DefaultThemeResolver;
import com.aquafish.theme.core.ThemeInheritanceResolver;
import com.aquafish.theme.core.ThemeParentResolver;
import com.aquafish.theme.core.ThemeScanner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Thymeleaf classpath 模板真实渲染测试。
 *
 * <p>
 * 第 31 步只验证了核心 fallback 资源能够从
 * classpath 中被找到和读取。
 * </p>
 *
 * <p>
 * 本测试继续验证这些资源能够真正进入
 * Thymeleaf 模板引擎，并最终输出 HTML。
 * </p>
 *
 * <p>完整测试链路：</p>
 *
 * <pre>
 * CoreFallbackTemplateResolver
 * -> ResolvedTemplate
 * -> ThymeleafTemplateRenderService
 * -> ClassLoaderTemplateResolver
 * -> 最终 HTML
 * </pre>
 *
 * <p>主要验证：</p>
 *
 * <ol>
 *     <li>
 *         核心首页模板能够从 classpath 真实渲染；
 *     </li>
 *     <li>
 *         多级目录模板能够正确推导 classpath 根目录；
 *     </li>
 *     <li>
 *         classpath 模板可以正常读取 ViewModel；
 *     </li>
 *     <li>
 *         th:text 会自动转义不安全 HTML；
 *     </li>
 *     <li>
 *         classpath 绝对路径和相对路径不匹配时安全失败；
 *     </li>
 *     <li>
 *         第 32 步改造没有破坏普通磁盘模板渲染。
 *     </li>
 * </ol>
 */
class ThymeleafClasspathTemplateRenderTest {

    /**
     * JUnit 为每个测试方法创建的临时工作目录。
     *
     * <p>
     * 普通磁盘模板回归测试会使用该目录。
     * </p>
     */
    @TempDir
    Path temporaryWorkDir;

    /**
     * 验证真实核心首页模板能够由 Thymeleaf 渲染。
     */
    @Test
    void shouldRenderRealCoreFallbackIndexFromClasspath() {
        CoreFallbackTemplateResolver coreResolver =
            new CoreFallbackTemplateResolver();

        ResolvedTemplate resolvedTemplate =
            coreResolver.require(
                TemplateTypes.require("index")
            );

        TemplateRenderRequest request =
            TemplateRenderRequest.of(
                TemplateTypes.require("index"),
                Map.of()
            );

        TemplateRenderResult result =
            createThymeleafEngine().render(
                request,
                resolvedTemplate
            );

        assertTrue(
            result.success(),
            result.errorMessage()
        );

        assertTrue(
            result.html().contains(
                "<!DOCTYPE html>"
            )
                || result.html().contains(
                    "<!doctype html>"
                )
        );

        assertTrue(
            result.html().contains(
                "核心内置安全模板"
            )
        );

        assertTrue(
            result.html().contains(
                "网站首页"
            )
        );

        assertTrue(
            result.html().contains(
                "系统已自动切换到随核心程序发布的只读页面"
            )
        );

        assertEquals(
            "classpath:/aquafish/core-fallback/templates/index.html",
            result.templatePath()
        );

        assertEquals(
            "aquafish-core-fallback",
            result.themeName()
        );

        assertFalse(
            result.cacheHit()
        );

        assertNull(
            result.errorMessage()
        );
    }

    /**
     * 验证多级目录核心模板可以正确渲染。
     *
     * <p>
     * forum/viewthread.html 用来确认解析器不会把
     * forum 目录错误地当成 classpath 根目录。
     * </p>
     */
    @Test
    void shouldRenderNestedCoreFallbackTemplateFromClasspath() {
        CoreFallbackTemplateResolver coreResolver =
            new CoreFallbackTemplateResolver();

        ResolvedTemplate resolvedTemplate =
            coreResolver.require(
                TemplateTypes.require("thread")
            );

        TemplateRenderRequest request =
            TemplateRenderRequest.of(
                TemplateTypes.require("thread"),
                Map.of()
            );

        TemplateRenderResult result =
            createThymeleafEngine().render(
                request,
                resolvedTemplate
            );

        assertTrue(
            result.success(),
            result.errorMessage()
        );

        assertTrue(
            result.html().contains(
                "帖子详情页"
            )
        );

        assertTrue(
            result.html().contains(
                "fallback-type: thread"
            )
        );

        assertEquals(
            "classpath:/aquafish/core-fallback/templates/forum/viewthread.html",
            result.templatePath()
        );

        assertEquals(
            "aquafish-core-fallback",
            result.themeName()
        );
    }

    /**
     * 验证 classpath Thymeleaf 模板能够读取模型，
     * 并自动转义不安全 HTML。
     */
    @Test
    void shouldInjectModelAndEscapeUnsafeHtmlInClasspathTemplate() {
        TemplateType dynamicTemplateType =
            new TemplateType(
                "classpath-dynamic-test",
                "test/dynamic.html",
                "Classpath 动态模板",
                "验证 classpath 模板模型注入与 HTML 转义。"
            );

        CoreFallbackTemplateResolver coreResolver =
            new CoreFallbackTemplateResolver();

        ResolvedTemplate resolvedTemplate =
            coreResolver.require(
                dynamicTemplateType
            );

        TemplateRenderRequest request =
            new TemplateRenderRequest(
                dynamicTemplateType,
                Map.of(
                    "title",
                    "<script>alert('x')</script>",
                    "description",
                    "中文 ViewModel 注入正常"
                ),
                null,
                null,
                Locale.SIMPLIFIED_CHINESE,
                null
            );

        TemplateRenderResult result =
            createThymeleafEngine().render(
                request,
                resolvedTemplate
            );

        assertTrue(
            result.success(),
            result.errorMessage()
        );

        /*
         * th:text 必须对 script 标签执行 HTML 转义。
         */
        assertFalse(
            result.html().contains(
                "<script>alert('x')</script>"
            )
        );

        assertTrue(
            result.html().contains(
                "&lt;script&gt;"
            )
        );

        assertTrue(
            result.html().contains(
                "&lt;/script&gt;"
            )
        );

        assertTrue(
            result.html().contains(
                "中文 ViewModel 注入正常"
            )
        );

        assertEquals(
            "thymeleaf",
            resolvedTemplate.engineId()
        );
    }

    /**
     * 验证 classpath 绝对路径与模板相对路径不一致时，
     * Thymeleaf 引擎会安全返回失败结果。
     */
    @Test
    void shouldRejectMismatchedClasspathAndRelativePath() {
        TemplateType templateType =
            new TemplateType(
                "classpath-mismatch-test",
                "content/view.html",
                "Classpath 路径不匹配测试",
                "验证路径安全校验。"
            );

        ResolvedTemplate invalidTemplate =
            new ResolvedTemplate(
                templateType,
                "aquafish-core-fallback",
                "thymeleaf",
                "content/view.html",
                "classpath:/aquafish/core-fallback/templates/index.html",
                true,
                "故意构造的错误路径。"
            );

        TemplateRenderResult result =
            createThymeleafEngine().render(
                TemplateRenderRequest.of(
                    templateType,
                    Map.of()
                ),
                invalidTemplate
            );

        assertFalse(
            result.success()
        );

        assertTrue(
            result.errorMessage().contains(
                "classpath 模板路径与相对路径不匹配"
            )
        );

        assertTrue(
            result.html().isEmpty()
        );
    }

    /**
     * 验证新增 ClassLoaderTemplateResolver 后，
     * 普通主题磁盘模板仍然能够正常渲染。
     *
     * @throws Exception 创建测试模板失败时抛出
     */
    @Test
    void shouldContinueRenderingNormalFileTemplate()
        throws Exception {

        Path templatesDirectory =
            temporaryWorkDir.resolve(
                "themes"
            )
            .resolve("disk-theme")
            .resolve("templates");

        Path contentDirectory =
            templatesDirectory.resolve(
                "content"
            );

        Files.createDirectories(
            contentDirectory
        );

        Path templateFile =
            contentDirectory.resolve(
                "disk.html"
            );

        Files.writeString(
            templateFile,
            """
            <!doctype html>
            <html lang="zh-CN">
            <body>
                <h1 th:text="${title}">
                    默认磁盘标题
                </h1>
            </body>
            </html>
            """,
            StandardCharsets.UTF_8
        );

        TemplateType templateType =
            new TemplateType(
                "disk-template-regression-test",
                "content/disk.html",
                "磁盘模板回归测试",
                "验证普通主题文件模板没有被破坏。"
            );

        ResolvedTemplate resolvedTemplate =
            new ResolvedTemplate(
                templateType,
                "disk-theme",
                "thymeleaf",
                "content/disk.html",
                templateFile
                    .toAbsolutePath()
                    .normalize()
                    .toString(),
                true,
                "普通磁盘模板测试。"
            );

        TemplateRenderRequest request =
            TemplateRenderRequest.of(
                templateType,
                Map.of(
                    "title",
                    "普通磁盘模板仍然正常"
                )
            );

        TemplateRenderResult result =
            createThymeleafEngine().render(
                request,
                resolvedTemplate
            );

        assertTrue(
            result.success(),
            result.errorMessage()
        );

        assertTrue(
            result.html().contains(
                "普通磁盘模板仍然正常"
            )
        );

        assertEquals(
            "disk-theme",
            result.themeName()
        );

        assertEquals(
            templateFile
                .toAbsolutePath()
                .normalize()
                .toString(),
            result.templatePath()
        );
    }

    /**
     * 创建测试所需的真实 Thymeleaf 模板引擎。
     *
     * <p>
     * 双参数 render 方法不会再次调用 ThemeTemplateResolver，
     * 但生产类构造方法要求该依赖不能为空。
     * 因此这里使用完整真实依赖进行装配，
     * 不使用 Mock，也不使用已过时的兼容构造方法。
     * </p>
     *
     * @return Thymeleaf 模板引擎
     */
    private ThymeleafTemplateRenderService
        createThymeleafEngine() {

        AquafishProperties properties =
            new AquafishProperties(
                temporaryWorkDir.toString(),
                "http://127.0.0.1:8520",
                "aq_",
                "unused-test-theme"
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

        ThemeTemplateResolver themeTemplateResolver =
            new ThemeTemplateResolver(
                activeThemeResolver,
                inheritanceResolver,
                defaultThemeResolver
            );

        return new ThymeleafTemplateRenderService(
            themeTemplateResolver
        );
    }
}
