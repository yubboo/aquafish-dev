package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.core.TemplateRenderService;
import com.aquafish.template.core.TemplateType;
import com.aquafish.template.core.TemplateTypes;
import com.aquafish.template.model.StandardTemplateModelFactory;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发阶段全部模板批量渲染诊断接口。
 *
 * 当前阶段：
 * Step 17-20-8：批量渲染全部 16 个 CMS / BBS 模板。
 *
 * 当前作用：
 * 1. 一次性渲染 TemplateTypes.all() 里的全部内置模板；
 * 2. 检查 CMS 相关模板是否能正常渲染；
 * 3. 检查 BBS 相关模板是否能正常渲染；
 * 4. 检查默认主题是否还有语法错误；
 * 5. 检查 StandardTemplateModelFactory 的标准变量是否覆盖模板需求；
 * 6. 给后续主题开发、主题市场、主题健康检查做基础。
 *
 * 访问地址：
 * GET /api/dev/template-render-all
 *
 * 注意：
 * 这个接口是开发阶段诊断接口。
 * 正式上线前可以删除，或者移动到后台系统诊断页面。
 */
@RestController
@Profile("dev")
public class DevTemplateRenderAllController {

    /**
     * 模板渲染服务。
     *
     * 当前实际实现：
     * ThymeleafTemplateRenderService
     */
    private final TemplateRenderService templateRenderService;

    public DevTemplateRenderAllController(TemplateRenderService templateRenderService) {
        this.templateRenderService = templateRenderService;
    }

    /**
     * 批量渲染全部内置模板。
     *
     * @return 全部模板渲染诊断结果
     */
    @GetMapping("/api/dev/template-render-all")
    public ApiResult<TemplateRenderAllDevResponse> renderAll() {
        List<TemplateRenderItem> items = TemplateTypes.all()
            .stream()
            .map(this::renderOne)
            .toList();

        long successCount = items
            .stream()
            .filter(TemplateRenderItem::success)
            .count();

        long failedCount = items.size() - successCount;

        TemplateRenderAllDevResponse data = new TemplateRenderAllDevResponse(
            items.size(),
            successCount,
            failedCount,
            items,
            "当前接口用于批量验证 CMS / BBS 全部内置模板是否可以被当前主题正常渲染。"
        );

        if (failedCount == 0) {
            return ApiResult.ok(data, "全部模板渲染成功");
        }

        return ApiResult.fail("TEMPLATE_RENDER_ALL_HAS_FAILED_ITEMS", "部分模板渲染失败", data);
    }

    /**
     * 渲染单个模板类型。
     *
     * 这里每个模板单独 try/catch。
     *
     * 原因：
     * 如果其中一个模板炸了，不能影响其它模板的诊断结果。
     *
     * @param templateType 模板类型
     * @return 单个模板渲染结果
     */
    private TemplateRenderItem renderOne(TemplateType templateType) {
        try {
            Map<String, Object> model = StandardTemplateModelFactory.demoModel(templateType.key());

            TemplateRenderRequest request = TemplateRenderRequest.of(
                templateType,
                model
            );

            TemplateRenderResult result = templateRenderService.render(request);

            return new TemplateRenderItem(
                templateType.key(),
                templateType.displayName(),
                templateType.defaultTemplatePath(),
                result.success(),
                result.themeName(),
                result.templatePath(),
                result.cacheHit(),
                result.html().length(),
                result.errorMessage()
            );
        } catch (Exception error) {
            return new TemplateRenderItem(
                templateType.key(),
                templateType.displayName(),
                templateType.defaultTemplatePath(),
                false,
                null,
                null,
                false,
                0,
                error.getMessage()
            );
        }
    }

    /**
     * 批量渲染响应结构。
     *
     * @param total 模板总数
     * @param successCount 成功数量
     * @param failedCount 失败数量
     * @param items 每个模板的渲染结果
     * @param note 诊断说明
     */
    public record TemplateRenderAllDevResponse(
        int total,
        long successCount,
        long failedCount,
        List<TemplateRenderItem> items,
        String note
    ) {
    }

    /**
     * 单个模板渲染结果。
     *
     * @param type 模板类型 key
     * @param displayName 模板显示名称
     * @param defaultTemplatePath 默认模板路径
     * @param success 是否渲染成功
     * @param themeName 实际主题名称
     * @param templatePath 实际模板路径
     * @param cacheHit 是否命中缓存
     * @param htmlLength HTML 长度
     * @param errorMessage 错误信息
     */
    public record TemplateRenderItem(
        String type,
        String displayName,
        String defaultTemplatePath,
        boolean success,
        String themeName,
        String templatePath,
        boolean cacheHit,
        int htmlLength,
        String errorMessage
    ) {
    }
}
