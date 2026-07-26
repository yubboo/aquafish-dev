package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.core.TemplateRenderService;
import com.aquafish.template.core.TemplateTypes;
import com.aquafish.template.model.StandardTemplateModelFactory;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发阶段模板渲染诊断接口。
 *
 * 当前阶段：
 * Step 17-20-7：接入 CMS / BBS 标准模板变量模型。
 *
 * 当前作用：
 * 1. 根据模板类型渲染 HTML；
 * 2. 返回渲染状态、模板路径、主题名称、HTML 长度和预览；
 * 3. 提供一个直接返回 HTML 的测试接口；
 * 4. 使用 StandardTemplateModelFactory 生成统一模板变量；
 * 5. 验证 CMS 和 BBS 模板是否都能正常拿到变量。
 *
 * 注意：
 * 这个 Controller 是开发阶段诊断接口。
 * 正式上线前可以删除，或者移动到后台系统诊断页面。
 */
@RestController
@Profile("dev")
public class DevTemplateRenderController {

    /**
     * 模板渲染服务。
     *
     * 当前实际实现是：
     * ThymeleafTemplateRenderService
     */
    private final TemplateRenderService templateRenderService;

    public DevTemplateRenderController(TemplateRenderService templateRenderService) {
        this.templateRenderService = templateRenderService;
    }

    /**
     * 模板渲染 JSON 诊断接口。
     *
     * 示例：
     * GET /api/dev/template-render?type=index
     * GET /api/dev/template-render?type=post
     * GET /api/dev/template-render?type=thread
     *
     * @param type 模板类型 key
     * @return 模板渲染诊断结果
     */
    @GetMapping("/api/dev/template-render")
    public ApiResult<TemplateRenderDevResponse> templateRender(
        @RequestParam(name = "type", defaultValue = "index") String type
    ) {
        TemplateRenderResult result = renderByType(type);

        TemplateRenderDevResponse data = new TemplateRenderDevResponse(
            result.success(),
            type,
            result.themeName(),
            result.templatePath(),
            result.cacheHit(),
            result.html().length(),
            previewHtml(result.html()),
            result.errorMessage(),
            "当前接口用于验证 CMS / BBS 标准模板变量模型是否可以正常渲染。"
        );

        if (result.success()) {
            return ApiResult.ok(data, "模板渲染成功");
        }

        return ApiResult.fail("TEMPLATE_RENDER_FAILED", "模板渲染失败", data);
    }

    /**
     * 模板渲染 HTML 预览接口。
     *
     * 示例：
     * GET /api/dev/template-render-html?type=index
     * GET /api/dev/template-render-html?type=post
     * GET /api/dev/template-render-html?type=thread
     *
     * 这个接口会直接返回 text/html。
     * 用浏览器打开可以看到页面效果。
     *
     * @param type 模板类型 key
     * @return HTML 字符串
     */
    @GetMapping(
        value = "/api/dev/template-render-html",
        produces = MediaType.TEXT_HTML_VALUE
    )
    public String templateRenderHtml(
        @RequestParam(name = "type", defaultValue = "index") String type
    ) {
        TemplateRenderResult result = renderByType(type);

        if (result.success()) {
            return result.html();
        }

        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8">
              <title>Aquafish 模板渲染失败</title>
            </head>
            <body>
              <h1>Aquafish 模板渲染失败</h1>
              <p>%s</p>
            </body>
            </html>
            """.formatted(escapeHtml(result.errorMessage()));
    }

    /**
     * 根据模板类型执行渲染。
     *
     * @param type 模板类型 key
     * @return 模板渲染结果
     */
    private TemplateRenderResult renderByType(String type) {
        Map<String, Object> model = StandardTemplateModelFactory.demoModel(type);

        TemplateRenderRequest request = TemplateRenderRequest.of(
            TemplateTypes.require(type),
            model
        );

        return templateRenderService.render(request);
    }

    /**
     * 生成 HTML 预览。
     *
     * 避免 JSON 接口返回过长内容。
     *
     * @param html 完整 HTML
     * @return 预览 HTML
     */
    private String previewHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        String compact = html
            .replace("\r", "")
            .replace("\n", " ")
            .replaceAll("\\s+", " ")
            .trim();

        int maxLength = 1200;

        if (compact.length() <= maxLength) {
            return compact;
        }

        return compact.substring(0, maxLength) + "...";
    }

    /**
     * 简单 HTML 转义。
     *
     * 用于渲染失败时把错误信息安全输出到 HTML 页面。
     *
     * @param value 原始文本
     * @return 转义后的文本
     */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    /**
     * 模板渲染诊断响应结构。
     *
     * @param success 是否成功
     * @param type 模板类型
     * @param themeName 主题名称
     * @param templatePath 模板路径
     * @param cacheHit 是否命中缓存
     * @param htmlLength HTML 长度
     * @param htmlPreview HTML 预览
     * @param errorMessage 错误信息
     * @param note 诊断说明
     */
    public record TemplateRenderDevResponse(
        boolean success,
        String type,
        String themeName,
        String templatePath,
        boolean cacheHit,
        int htmlLength,
        String htmlPreview,
        String errorMessage,
        String note
    ) {
    }
}
