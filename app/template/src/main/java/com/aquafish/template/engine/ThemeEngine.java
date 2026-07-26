package com.aquafish.template.engine;

import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.resolve.ResolvedTemplate;

/**
 * Aquafish 主题模板引擎统一接口。
 *
 * <p>
 * 该接口位于模板系统的引擎抽象层，用于统一约束不同服务端模板引擎
 * 在 Aquafish 平台中的接入方式。
 * </p>
 *
 * <p>
 * 当前项目已经使用 Thymeleaf，后续还会接入 Pebble。
 * 两种模板引擎的语法、缓存方式和底层渲染实现不同，
 * 但对于 CMS、论坛、用户中心等上层业务模块来说，
 * 它们都只需要完成同一件事：
 * 将已经解析好的主题模板和页面数据渲染为最终 HTML。
 * </p>
 *
 * <p>
 * 因此，上层业务代码不得直接依赖
 * ThymeleafTemplateRenderService、PebbleThemeEngine
 * 或其他具体模板引擎实现，而应通过统一的模板渲染入口间接调用本接口。
 * 这样以后增加、替换或关闭某一种模板引擎时，
 * 不需要修改文章、论坛、搜索、用户等业务模块。
 * </p>
 *
 * <p>
 * 每个主题只能声明一种模板引擎。
 * 主题使用的引擎标识来自 theme.yaml 中的 engine 字段，例如：
 * </p>
 *
 * <pre>
 * engine: thymeleaf
 * </pre>
 *
 * <p>或者：</p>
 *
 * <pre>
 * engine: pebble
 * </pre>
 *
 * <p>
 * 模板引擎实现类需要由 Spring 管理，
 * 并由 ThemeEngineRegistry 自动收集和注册。
 * 每个实现必须返回唯一且稳定的 engineId，
 * 不允许两个实现使用相同的引擎标识。
 * </p>
 *
 * <p>
 * 本接口只负责“渲染”，不负责以下工作：
 * </p>
 *
 * <ol>
 *     <li>不负责决定当前启用哪个主题；</li>
 *     <li>不负责扫描或安装主题；</li>
 *     <li>不负责解析父主题和子主题；</li>
 *     <li>不负责 default 和核心 fallback 回退；</li>
 *     <li>不负责直接访问文章、论坛或用户数据库；</li>
 *     <li>不负责决定管理员是否允许某种模板引擎运行。</li>
 * </ol>
 *
 * <p>
 * 以上职责分别由主题模块、模板解析器、回退解析器、
 * 后台设置服务和统一 Theme ViewModel 负责。
 * 通过这种职责拆分，可以避免模板引擎与业务系统相互耦合。
 * </p>
 *
 * <p>
 * 实现类还必须遵守安全要求：
 * 第三方主题只能读取平台明确提供的只读模板数据，
 * 不能直接获得 Spring Bean、Repository、Service、
 * 数据库连接、服务器文件或其他核心内部对象。
 * </p>
 */
public interface ThemeEngine {

    /**
     * 返回模板引擎的唯一标识。
     *
     * <p>
     * 该标识用于连接以下几个位置：
     * </p>
     *
     * <ol>
     *     <li>主题 theme.yaml 中的 engine 字段；</li>
     *     <li>ThemeEngineRegistry 中的引擎注册名称；</li>
     *     <li>后台模板引擎配置中的引擎编号；</li>
     *     <li>应用中心主题兼容信息中的模板引擎类型。</li>
     * </ol>
     *
     * <p>
     * 标识应该使用稳定的小写英文名称，
     * 不能包含显示名称、版本号或会随版本变化的内容。
     * </p>
     *
     * <p>正确示例：</p>
     *
     * <pre>
     * thymeleaf
     * pebble
     * </pre>
     *
     * <p>不建议的示例：</p>
     *
     * <pre>
     * Thymeleaf 3.1
     * pebble-engine-v4
     * 我的模板引擎
     * </pre>
     *
     * @return 当前模板引擎唯一且稳定的标识，不能返回 null 或空字符串
     */
    String engineId();

    /**
     * 使用当前模板引擎渲染一个已经完成路径解析的主题模板。
     *
     * <p>正常调用流程为：</p>
     *
     * <ol>
     *     <li>业务模块创建 TemplateRenderRequest；</li>
     *     <li>ThemeTemplateResolver 解析对应模板文件；</li>
     *     <li>解析结果写入 ResolvedTemplate；</li>
     *     <li>ThemeEngineRegistry 根据 engineId 选择模板引擎；</li>
     *     <li>调用本方法渲染最终 HTML。</li>
     * </ol>
     *
     * <p>
     * request 中包含页面语言、模板类型和只读页面数据模型。
     * resolvedTemplate 中包含已经确认的主题名称、
     * 模板引擎标识、相对模板路径、绝对模板路径和文件存在状态。
     * </p>
     *
     * <p>
     * 实现类不应该重新决定当前主题，也不应该绕过
     * ThemeTemplateResolver 自己从任意目录寻找模板，
     * 否则会破坏父子主题、默认主题和核心兜底的统一解析规则。
     * </p>
     *
     * <p>
     * 渲染失败时，不建议直接把底层异常抛到用户页面。
     * 应转换成 TemplateRenderResult.failure，
     * 由上层渲染服务决定记录日志、显示诊断信息
     * 或继续进入下一层 fallback。
     * </p>
     *
     * @param request 模板渲染请求，包含语言、模板类型和页面数据；
     *                不允许为 null
     * @param resolvedTemplate 已完成路径解析的模板结果；
     *                         不允许为 null
     * @return 模板渲染结果；成功时包含最终 HTML，
     *         失败时包含可供日志和后台诊断使用的错误说明
     */
    TemplateRenderResult render(
        TemplateRenderRequest request,
        ResolvedTemplate resolvedTemplate
    );
}
