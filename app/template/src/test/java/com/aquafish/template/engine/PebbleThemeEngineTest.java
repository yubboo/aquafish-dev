package com.aquafish.template.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.core.TemplateType;
import com.aquafish.template.resolve.ResolvedTemplate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PebbleThemeEngine 自动化测试。
 *
 * <p>
 * 本测试不会读取用户当前安装的真实主题，
 * 而是由 JUnit 创建一个临时主题目录和临时模板文件。
 * 测试结束后，临时目录会被自动删除。
 * </p>
 *
 * <p>本测试重点验证：</p>
 *
 * <ol>
 *     <li>PebbleThemeEngine 能够读取外部 .html 模板；</li>
 *     <li>模板能够获取 TemplateRenderRequest 中的页面数据；</li>
 *     <li>中文内容能够按照 UTF-8 正常渲染；</li>
 *     <li>Pebble HTML 自动转义已经开启；</li>
 *     <li>渲染结果能够正确封装为 TemplateRenderResult；</li>
 *     <li>实际主题名称和模板路径能够正确返回。</li>
 * </ol>
 *
 * <p>
 * 该测试直接验证 Pebble 运行时行为，
 * 能够发现“代码可以编译，但实际渲染时报错”的问题。
 * </p>
 */
class PebbleThemeEngineTest {

    /**
     * JUnit 为每次测试创建的独立临时目录。
     *
     * <p>
     * 不使用 Aquafish 项目的真实 themes 目录，
     * 避免自动化测试修改、覆盖或污染用户主题。
     * </p>
     */
    @TempDir
    Path temporaryDirectory;

    /**
     * 验证 Pebble 能够完成基础页面渲染和 HTML 自动转义。
     *
     * <p>测试流程：</p>
     *
     * <ol>
     *     <li>创建临时 templates 目录；</li>
     *     <li>写入一个包含 Pebble 变量的 index.html；</li>
     *     <li>创建平台统一 TemplateType；</li>
     *     <li>创建统一 TemplateRenderRequest；</li>
     *     <li>创建已经解析完成的 ResolvedTemplate；</li>
     *     <li>调用 PebbleThemeEngine.render；</li>
     *     <li>检查最终 HTML 和渲染结果。</li>
     * </ol>
     *
     * @throws Exception 当测试模板文件无法创建或写入时抛出
     */
    @Test
    void shouldRenderPebbleTemplateAndEscapeHtml()
        throws Exception {

        /*
         * 模拟一个 Pebble 主题的 templates 目录。
         *
         * 实际主题结构类似：
         *
         * themes/pebble-demo/templates/index.html
         */
        Path templatesDirectory =
            temporaryDirectory.resolve("templates");

        Files.createDirectories(
            templatesDirectory
        );

        /*
         * 创建本次测试使用的 Pebble 模板。
         *
         * siteName 用于验证普通页面数据注入。
         * unsafeHtml 用于验证 HTML 自动转义。
         */
        Path templateFile =
            templatesDirectory.resolve("index.html");

        String templateSource =
            "<h1>你好，{{ siteName }}</h1>"
                + "<div>{{ unsafeHtml }}</div>";

        Files.writeString(
            templateFile,
            templateSource,
            StandardCharsets.UTF_8
        );

        /*
         * 创建平台模板类型。
         *
         * Pebble 和 Thymeleaf 共用同一套 TemplateType，
         * 不会为每种引擎创建两套业务页面类型。
         */
        TemplateType templateType =
            new TemplateType(
                "pebble-engine-test",
                "index.html",
                "Pebble 引擎测试",
                "验证 Pebble 真实模板渲染。"
            );

        /*
         * 创建平台统一渲染请求。
         *
         * unsafeHtml 故意包含 HTML 标签，
         * 用来确认 Pebble 自动转义没有被关闭。
         */
        TemplateRenderRequest request =
            new TemplateRenderRequest(
                templateType,
                Map.of(
                    "siteName",
                    "Aquafish",
                    "unsafeHtml",
                    "<b>危险内容</b>"
                ),
                "pebble-test",
                null,
                Locale.SIMPLIFIED_CHINESE,
                null
            );

        /*
         * 模拟 ThemeTemplateResolver 已经完成模板解析。
         *
         * engineId 必须填写 pebble，
         * 否则 PebbleThemeEngine 会拒绝渲染。
         */
        ResolvedTemplate resolvedTemplate =
            new ResolvedTemplate(
                templateType,
                "pebble-test",
                "pebble",
                "index.html",
                templateFile
                    .toAbsolutePath()
                    .normalize()
                    .toString(),
                Files.isRegularFile(templateFile),
                "JUnit 临时 Pebble 模板已经解析完成。"
            );

        PebbleThemeEngine themeEngine =
            new PebbleThemeEngine();

        /*
         * 真正调用 Pebble 模板引擎。
         *
         * 这一步会实际创建 FileLoader、
         * 编译模板并执行 template.evaluate。
         */
        TemplateRenderResult result =
            themeEngine.render(
                request,
                resolvedTemplate
            );

        /*
         * 如果渲染失败，将 errorMessage 作为断言提示，
         * 方便直接看到 Pebble 的真实错误原因。
         */
        assertTrue(
            result.success(),
            () -> "Pebble 渲染失败："
                + result.errorMessage()
        );

        /*
         * 验证普通变量已经正确写入最终 HTML。
         */
        assertTrue(
            result.html().contains(
                "<h1>你好，Aquafish</h1>"
            )
        );

        /*
         * 验证危险 HTML 没有原样进入最终页面。
         *
         * 如果这一项失败，说明自动转义没有生效。
         */
        assertFalse(
            result.html().contains(
                "<b>危险内容</b>"
            )
        );

        /*
         * 验证 HTML 标签已经被安全转义。
         */
        assertTrue(
            result.html().contains(
                "&lt;b&gt;危险内容&lt;/b&gt;"
            )
        );

        /*
         * 验证模板引擎标识保持稳定。
         */
        assertEquals(
            "pebble",
            themeEngine.engineId()
        );

        /*
         * 验证成功结果记录了真正提供模板的主题。
         */
        assertEquals(
            "pebble-test",
            result.themeName()
        );

        /*
         * 当前尚未启用模板缓存，
         * 因此本次渲染不应该命中缓存。
         */
        assertFalse(
            result.cacheHit()
        );

        /*
         * 成功结果不应该包含错误信息。
         */
        assertEquals(
            null,
            result.errorMessage()
        );
    }
}
