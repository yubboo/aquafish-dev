package com.aquafish.template.resolve;

import com.aquafish.template.core.TemplateType;

/**
 * Aquafish 已解析主题模板结果。
 *
 * <p>
 * 本记录对象用于保存 ThemeTemplateResolver 完成模板路径解析后，
 * 得到的最终模板信息。
 * </p>
 *
 * <p>
 * 它位于“模板查找”和“模板渲染”之间：
 * </p>
 *
 * <ol>
 *     <li>业务模块提出需要渲染的模板类型；</li>
 *     <li>ThemeTemplateResolver 查找对应的主题模板文件；</li>
 *     <li>将查找结果封装为 ResolvedTemplate；</li>
 *     <li>模板渲染服务根据 engineId 选择对应模板引擎；</li>
 *     <li>具体模板引擎读取模板并生成最终 HTML。</li>
 * </ol>
 *
 * <p>
 * 本对象只描述“解析结果”，不负责以下工作：
 * </p>
 *
 * <ul>
 *     <li>不负责扫描或安装主题；</li>
 *     <li>不负责选择当前启用主题；</li>
 *     <li>不负责执行 Thymeleaf 或 Pebble 渲染；</li>
 *     <li>不负责访问文章、论坛或用户数据库；</li>
 *     <li>不负责决定管理员是否允许某种模板引擎运行。</li>
 * </ul>
 *
 * <p>
 * 当前阶段主要记录当前主题中的模板文件。
 * 后续实现完整主题回退后，本对象还会用于表示：
 * </p>
 *
 * <pre>
 * 当前子主题
 * -> 父主题
 * -> 外置 default 主题
 * -> 核心内置 fallback 主题
 * </pre>
 *
 * <p>
 * 例如，用户当前启用了一个 Pebble 主题，
 * 但该主题缺少论坛详情模板，
 * 系统最终可能使用核心 Thymeleaf 兜底模板。
 * 此时 themeName 和 engineId 应记录真正提供模板的主题和引擎，
 * 而不是只记录用户最初启用的主题。
 * </p>
 *
 * @param templateType 模板类型，用于标识首页、文章详情、
 *                     论坛首页或帖子详情等页面类型
 * @param themeName 最终提供当前模板文件的主题名称
 * @param engineId 最终渲染当前模板时使用的模板引擎标识
 * @param relativeTemplatePath 相对于主题 templates 目录的模板路径
 * @param absoluteTemplatePath 模板文件在服务器上的规范化绝对路径
 * @param exists 模板文件在解析时是否真实存在
 * @param message 模板解析诊断说明
 */
public record ResolvedTemplate(

    /**
     * 当前解析结果对应的模板类型。
     *
     * <p>
     * 模板类型由平台统一定义，
     * 用于把业务页面映射到稳定的模板路径。
     * </p>
     *
     * <p>示例：</p>
     *
     * <pre>
     * 首页
     * 文章列表
     * 文章详情
     * 论坛首页
     * 论坛版块
     * 帖子详情
     * </pre>
     *
     * <p>
     * 该字段不能为空。
     * 如果为空，说明业务层没有明确告诉模板系统需要渲染什么页面，
     * 此时系统无法安全确定模板路径。
     * </p>
     */
    TemplateType templateType,

    /**
     * 最终提供当前模板文件的主题名称。
     *
     * <p>
     * 当前阶段通常等于用户正在使用的主题名称，例如：
     * </p>
     *
     * <pre>
     * default
     * modern-blog
     * community-pro
     * </pre>
     *
     * <p>
     * 后续加入父子主题和多级回退后，
     * 该字段应该保存“真正找到模板文件的主题”，
     * 而不一定是用户最初启用的主题。
     * </p>
     *
     * <p>
     * 例如：
     * 当前启用主题为 modern-child，
     * 但文章详情模板来自 modern-parent，
     * 那么这里应记录 modern-parent。
     * </p>
     */
    String themeName,

    /**
     * 最终渲染当前模板时使用的模板引擎标识。
     *
     * <p>
     * 该值来源于最终提供模板的主题 theme.yaml 中的 engine 字段。
     * </p>
     *
     * <p>当前支持或计划支持：</p>
     *
     * <pre>
     * thymeleaf
     * pebble
     * </pre>
     *
     * <p>
     * 该字段用于让统一模板渲染入口通过 ThemeEngineRegistry
     * 查找到正确的 ThemeEngine 实现。
     * </p>
     *
     * <p>
     * 不应该使用显示名称或版本号作为引擎标识，例如：
     * Thymeleaf 3.1 或 Pebble 4。
     * 版本兼容信息后续应由独立字段或主题清单负责。
     * </p>
     */
    String engineId,

    /**
     * 相对于主题 templates 目录的模板路径。
     *
     * <p>例如：</p>
     *
     * <pre>
     * index.html
     * content/view.html
     * forum/index.html
     * forum/viewthread.html
     * user/profile.html
     * </pre>
     *
     * <p>
     * 该路径必须是主题内部相对路径，
     * 不能包含逃逸主题目录的路径片段，例如：
     * </p>
     *
     * <pre>
     * ../
     * ../../
     * </pre>
     *
     * <p>
     * 模板解析器后续必须负责路径规范化和目录边界检查，
     * 防止第三方主题读取主题目录之外的服务器文件。
     * </p>
     */
    String relativeTemplatePath,

    /**
     * 模板文件在服务器上的绝对路径。
     *
     * <p>Windows 环境示例：</p>
     *
     * <pre>
     * %USERPROFILE%\\.aquafish\\dev\\themes
     * \\default\\templates\\forum\\viewthread.html
     * </pre>
     *
     * <p>
     * 该路径仅供模板引擎在服务端读取模板使用，
     * 不能直接输出给普通前台用户。
     * </p>
     *
     * <p>
     * 后台主题诊断页面可以在管理员权限下显示必要路径信息，
     * 但公开错误页面不应该暴露服务器真实目录结构。
     * </p>
     */
    String absoluteTemplatePath,

    /**
     * 模板文件在完成路径解析时是否真实存在。
     *
     * <p>
     * true 表示解析器已经找到对应文件；
     * false 表示当前解析位置没有找到模板。
     * </p>
     *
     * <p>
     * 当前实现中，如果 exists 为 false，
     * 模板渲染服务会返回失败结果。
     * 后续完整回退机制实现后，
     * 解析器应继续寻找父主题、default 或核心 fallback，
     * 直到找到可用模板或进入最终紧急页面。
     * </p>
     */
    boolean exists,

    /**
     * 模板解析过程的诊断说明。
     *
     * <p>
     * 该字段用于记录模板查找结果或失败原因，例如：
     * </p>
     *
     * <pre>
     * 已找到当前主题模板。
     * 当前主题模板不存在。
     * 父主题不存在。
     * 当前主题声明了不支持的模板引擎。
     * 已回退到核心内置模板。
     * </pre>
     *
     * <p>
     * 诊断信息主要用于后台管理、日志和开发调试，
     * 不应未经处理直接显示给普通网站访客。
     * </p>
     */
    String message
) {

    /**
     * ResolvedTemplate 的紧凑构造方法。
     *
     * <p>
     * Java record 会在创建对象时自动调用该构造方法。
     * 当前构造方法负责完成基础参数校验和字符串标准化，
     * 保证模板解析结果在进入渲染层之前保持稳定格式。
     * </p>
     *
     * <p>当前处理规则：</p>
     *
     * <ol>
     *     <li>templateType 不允许为 null；</li>
     *     <li>themeName 为 null 时转换为空字符串；</li>
     *     <li>engineId 为 null 时转换为空字符串；</li>
     *     <li>相对路径、绝对路径和诊断信息统一去除首尾空格；</li>
     *     <li>字符串字段不会以 null 形式继续传入渲染层。</li>
     * </ol>
     *
     * <p>
     * 当前只对 templateType 执行强制非空校验，
     * 是为了保持现有运行行为不变。
     * 后续主题安装和模板解析规则完善后，
     * engineId、themeName 和模板路径的合法性应在更合适的解析层验证，
     * 而不是全部堆积在本记录对象中。
     * </p>
     *
     * @throws IllegalArgumentException 当 templateType 为空时抛出
     */
    public ResolvedTemplate {
        if (templateType == null) {
            throw new IllegalArgumentException(
                "已解析模板结果 templateType 不能为空。"
            );
        }

        /*
         * 将可能为空的字符串统一转换为空字符串并去除首尾空格，
         * 避免后续代码频繁进行 null 判断。
         *
         * 这里只进行格式标准化，不负责判断主题、引擎或路径是否合法。
         */
        themeName = normalizeText(themeName);
        engineId = normalizeText(engineId);
        relativeTemplatePath = normalizeText(relativeTemplatePath);
        absoluteTemplatePath = normalizeText(absoluteTemplatePath);
        message = normalizeText(message);
    }

    /**
     * 标准化可为空的字符串。
     *
     * <p>处理规则：</p>
     *
     * <pre>
     * null          -> ""
     * " thymeleaf " -> "thymeleaf"
     * " default "   -> "default"
     * </pre>
     *
     * <p>
     * 本方法只负责去除首尾空格，
     * 不会把文本转换为小写，也不会修改路径中的合法空格。
     * </p>
     *
     * @param value 待标准化的字符串，允许为 null
     * @return 非 null 的标准化字符串
     */
    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        return value.trim();
    }
}
