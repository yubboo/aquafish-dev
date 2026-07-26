package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.template.resolve.ResolvedTemplate;
import com.aquafish.template.resolve.ThemeTemplateResolver;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发阶段模板解析诊断接口。
 *
 * 当前阶段：
 * Step 17-20-4：验证 ThemeTemplateResolver 是否能根据模板类型找到当前主题模板文件。
 *
 * 当前作用：
 * 1. 查看当前主题下每个模板类型对应哪个文件；
 * 2. 判断模板文件是否存在；
 * 3. 为后续真正渲染 HTML 做准备；
 * 4. 提前发现默认主题缺失哪些模板文件。
 *
 * 注意：
 * 这个接口是开发阶段诊断接口。
 * 正式上线前可以删除，或者移动到后台系统诊断页面。
 */
@RestController
@Profile("dev")
public class DevTemplateResolveController {

    /**
     * 当前主题模板解析器。
     */
    private final ThemeTemplateResolver themeTemplateResolver;

    public DevTemplateResolveController(ThemeTemplateResolver themeTemplateResolver) {
        this.themeTemplateResolver = themeTemplateResolver;
    }

    /**
     * 模板解析诊断接口。
     *
     * 访问全部：
     * GET /api/dev/template-resolve
     *
     * 访问单个：
     * GET /api/dev/template-resolve?type=thread
     *
     * @param type 模板类型 key，可选
     * @return 模板解析结果
     */
    @GetMapping("/api/dev/template-resolve")
    public ApiResult<TemplateResolveResponse> templateResolve(
        @RequestParam(name = "type", required = false) String type
    ) {
        List<ResolvedTemplate> items;

        if (type == null || type.isBlank()) {
            items = themeTemplateResolver.resolveAllBuiltInTypes();
        } else {
            items = List.of(themeTemplateResolver.resolve(type));
        }

        long existsCount = items
            .stream()
            .filter(ResolvedTemplate::exists)
            .count();

        TemplateResolveResponse data = new TemplateResolveResponse(
            items.size(),
            existsCount,
            items.size() - existsCount,
            items,
            "当前接口用于验证模板类型到当前主题模板文件的解析结果。"
        );

        return ApiResult.ok(data, "模板解析诊断成功");
    }

    /**
     * 模板解析诊断响应。
     *
     * @param total 总数量
     * @param existsCount 存在数量
     * @param missingCount 缺失数量
     * @param items 解析结果列表
     * @param note 诊断说明
     */
    public record TemplateResolveResponse(
        int total,
        long existsCount,
        long missingCount,
        List<ResolvedTemplate> items,
        String note
    ) {
    }
}
