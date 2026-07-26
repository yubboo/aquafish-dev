package com.aquafish.template.core;

/**
 * Aquafish 模板渲染服务接口。
 *
 * 当前阶段：
 * Step 17-20-2：只定义统一入口。
 *
 * 后续真实实现会负责：
 * 1. 根据模板类型查找当前主题模板；
 * 2. 调用 Thymeleaf 渲染；
 * 3. 处理模板缓存；
 * 4. 处理模板安全；
 * 5. 处理模板错误兜底；
 * 6. 支持 aq:* 语法；
 * 7. 支持 Discuz 风格短语法兼容层。
 *
 * 业务模块后续只调用这个接口。
 *
 * 例如：
 *
 * TemplateRenderRequest request = TemplateRenderRequest.of(
 *     TemplateTypes.THREAD,
 *     model
 * );
 *
 * TemplateRenderResult result = templateRenderService.render(request);
 */
public interface TemplateRenderService {

    /**
     * 渲染模板。
     *
     * @param request 模板渲染请求
     * @return 模板渲染结果
     */
    TemplateRenderResult render(TemplateRenderRequest request);
}