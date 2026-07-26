package com.aquafish.template.emergency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.core.TemplateType;
import com.aquafish.template.core.TemplateTypes;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * EmergencyTemplateRenderer 自动化测试。
 *
 * <p>
 * 本测试验证最小紧急静态页面能够脱离主题文件、
 * classpath 模板和模板引擎独立生成完整 HTML。
 * </p>
 *
 * <p>主要验证：</p>
 *
 * <ol>
 *     <li>正常请求能够生成紧急页面；</li>
 *     <li>request 为 null 时仍然能够安全返回；</li>
 *     <li>模板路径中的特殊字符会被正确转义；</li>
 *     <li>紧急页面不会直接输出 script 标签；</li>
 *     <li>紧急页面不会泄露服务器绝对路径或异常堆栈；</li>
 *     <li>固定虚拟主题名称和 inline 路径保持稳定；</li>
 *     <li>isEmergencyResult 能正确识别紧急页面结果。</li>
 * </ol>
 */
class EmergencyTemplateRendererTest {

    /**
     * 验证正常模板请求能够生成完整紧急 HTML。
     */
    @Test
    void shouldRenderEmergencyHtmlForNormalRequest() {
        EmergencyTemplateRenderer renderer =
            new EmergencyTemplateRenderer();

        TemplateRenderRequest request =
            TemplateRenderRequest.of(
                TemplateTypes.THREAD,
                Map.of(
                    "unused",
                    "紧急页面不依赖 ViewModel"
                )
            );

        TemplateRenderResult result =
            renderer.render(request);

        assertTrue(
            result.success()
        );

        assertTrue(
            result.html().contains(
                "<!doctype html>"
            )
        );

        assertTrue(
            result.html().contains(
                "Aquafish"
            )
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

        assertTrue(
            result.html().contains(
                "requested-template: forum/viewthread.html"
            )
        );

        assertEquals(
            EmergencyTemplateRenderer
                .EMERGENCY_THEME_NAME,
            result.themeName()
        );

        assertEquals(
            "aquafish-emergency",
            result.themeName()
        );

        assertEquals(
            EmergencyTemplateRenderer
                .EMERGENCY_TEMPLATE_PATH,
            result.templatePath()
        );

        assertEquals(
            "inline:/aquafish/emergency.html",
            result.templatePath()
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
     * 验证 request 为 null 时，
     * 最后一层保护不会再次抛出异常。
     */
    @Test
    void shouldRenderEmergencyHtmlWhenRequestIsNull() {
        EmergencyTemplateRenderer renderer =
            new EmergencyTemplateRenderer();

        TemplateRenderResult result =
            renderer.render(null);

        assertTrue(
            result.success()
        );

        assertTrue(
            result.html().contains(
                "requested-template: unknown"
            )
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
            renderer.isEmergencyResult(
                result
            )
        );
    }

    /**
     * 验证模板相对路径中的 HTML 特殊字符
     * 不会直接进入紧急页面。
     */
    @Test
    void shouldEscapeUnsafeTemplatePath() {
        EmergencyTemplateRenderer renderer =
            new EmergencyTemplateRenderer();

        TemplateType unsafeTemplateType =
            new TemplateType(
                "emergency-escape-test",
                "safe/<script>alert(\"x\")</script>'test'.html",
                "紧急页面转义测试",
                "验证 Java 内联 HTML 转义。"
            );

        TemplateRenderRequest request =
            TemplateRenderRequest.of(
                unsafeTemplateType,
                Map.of()
            );

        TemplateRenderResult result =
            renderer.render(request);

        assertTrue(
            result.success()
        );

        /*
         * 原始 script 标签不能直接出现在页面中。
         */
        assertFalse(
            result.html().contains(
                "<script>alert(\"x\")</script>"
            )
        );

        /*
         * 小于号、大于号、双引号和单引号
         * 都应该被转换为 HTML 实体。
         */
        assertTrue(
            result.html().contains(
                "&lt;script&gt;"
            )
        );

        assertTrue(
            result.html().contains(
                "alert(&quot;x&quot;)"
            )
        );

        assertTrue(
            result.html().contains(
                "&lt;/script&gt;"
            )
        );

        assertTrue(
            result.html().contains(
                "&#39;test&#39;.html"
            )
        );

        assertTrue(
            renderer.isEmergencyResult(
                result
            )
        );
    }

    /**
     * 验证紧急页面不会展示敏感运行信息。
     */
    @Test
    void shouldNotExposeSensitiveRuntimeInformation() {
        EmergencyTemplateRenderer renderer =
            new EmergencyTemplateRenderer();

        TemplateRenderResult result =
            renderer.render(
                TemplateRenderRequest.of(
                    TemplateTypes.INDEX,
                    Map.of()
                )
            );

        assertTrue(
            result.success()
        );

        /*
         * 不允许出现常见 Windows 服务器绝对路径。
         */
        assertFalse(
            result.html().contains(
                "H:\\javaweb\\aquafish"
            )
        );

        assertFalse(
            result.html().contains(
                "C:\\"
            )
        );

        /*
         * 不允许向访客显示 Java 异常或堆栈关键词。
         */
        assertFalse(
            result.html().contains(
                "java.lang."
            )
        );

        assertFalse(
            result.html().contains(
                "Exception at"
            )
        );

        assertFalse(
            result.html().contains(
                "StackTrace"
            )
        );

        /*
         * 页面不依赖外部主题 CSS 或 JavaScript。
         */
        assertFalse(
            result.html().contains(
                "<script src="
            )
        );

        assertFalse(
            result.html().contains(
                "<link rel=\"stylesheet\""
            )
        );
    }

    /**
     * 验证 isEmergencyResult 只识别真正的紧急结果。
     */
    @Test
    void shouldIdentifyOnlyEmergencyResults() {
        EmergencyTemplateRenderer renderer =
            new EmergencyTemplateRenderer();

        TemplateRenderResult emergencyResult =
            renderer.render(
                TemplateRenderRequest.of(
                    TemplateTypes.ERROR,
                    Map.of()
                )
            );

        assertTrue(
            renderer.isEmergencyResult(
                emergencyResult
            )
        );

        assertFalse(
            renderer.isEmergencyResult(null)
        );

        TemplateRenderResult normalResult =
            TemplateRenderResult.success(
                "<html><body>普通页面</body></html>",
                "classpath:/normal/index.html",
                "normal-theme",
                false
            );

        assertFalse(
            renderer.isEmergencyResult(
                normalResult
            )
        );

        TemplateRenderResult wrongPathResult =
            TemplateRenderResult.success(
                "<html><body>测试页面</body></html>",
                "inline:/other/emergency.html",
                "aquafish-emergency",
                false
            );

        assertFalse(
            renderer.isEmergencyResult(
                wrongPathResult
            )
        );

        TemplateRenderResult wrongThemeResult =
            TemplateRenderResult.success(
                "<html><body>测试页面</body></html>",
                "inline:/aquafish/emergency.html",
                "other-theme",
                false
            );

        assertFalse(
            renderer.isEmergencyResult(
                wrongThemeResult
            )
        );
    }

    /**
     * 验证紧急页面协议常量保持稳定。
     */
    @Test
    void shouldExposeStableEmergencyConstants() {
        assertEquals(
            "aquafish-emergency",
            EmergencyTemplateRenderer
                .EMERGENCY_THEME_NAME
        );

        assertEquals(
            "inline:/aquafish/emergency.html",
            EmergencyTemplateRenderer
                .EMERGENCY_TEMPLATE_PATH
        );
    }
}
