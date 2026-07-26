package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.template.core.TemplateType;
import com.aquafish.template.core.TemplateTypes;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发阶段模板类型诊断接口。
 *
 * 当前阶段：
 * Step 17-20-2：验证 template 模块基础类是否可用。
 *
 * 当前作用：
 * 1. 查看 Aquafish 当前内置了哪些模板类型；
 * 2. 查看每个模板类型默认对应哪个模板文件；
 * 3. 验证 setup 模块可以正常依赖 template 模块；
 * 4. 为后续 theme 模块扫描主题模板做准备。
 *
 * 注意：
 * 这个接口是开发阶段诊断接口。
 * 正式上线前可以删除，或者移动到后台系统诊断页面。
 */
@RestController
@Profile("dev")
public class DevTemplateTypeController {

    /**
     * 模板类型诊断接口。
     *
     * 访问地址：
     * GET /api/dev/template-types
     *
     * @return 当前内置模板类型列表
     */
    @GetMapping("/api/dev/template-types")
    public ApiResult<TemplateTypesResponse> templateTypes() {
        List<TemplateTypeItem> items = TemplateTypes.all()
            .stream()
            .map(this::toItem)
            .toList();

        TemplateTypesResponse data = new TemplateTypesResponse(
            items.size(),
            items,
            "当前接口用于验证 app/template 模块的内置模板类型定义。"
        );

        return ApiResult.ok(data, "模板类型列表获取成功");
    }

    private TemplateTypeItem toItem(TemplateType type) {
        return new TemplateTypeItem(
            type.key(),
            type.defaultTemplatePath(),
            type.displayName(),
            type.description()
        );
    }

    /**
     * 模板类型列表响应结构。
     *
     * @param total 模板类型总数
     * @param items 模板类型列表
     * @param note 诊断说明
     */
    public record TemplateTypesResponse(
        int total,
        List<TemplateTypeItem> items,
        String note
    ) {
    }

    /**
     * 模板类型列表项。
     *
     * @param key 模板类型 key
     * @param defaultTemplatePath 默认模板路径
     * @param displayName 显示名称
     * @param description 模板说明
     */
    public record TemplateTypeItem(
        String key,
        String defaultTemplatePath,
        String displayName,
        String description
    ) {
    }
}
