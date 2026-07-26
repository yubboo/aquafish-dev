package com.aquafish.template.emergency;

import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import org.springframework.stereotype.Component;

/**
 * Aquafish 最小紧急静态页面渲染器。
 *
 * <p>
 * 本组件是整个访客页面渲染链的最后一道保护。
 * </p>
 *
 * <p>完整目标回退顺序：</p>
 *
 * <pre>
 * 当前活动主题
 * -> 父主题继承链
 * -> 外置官方 default
 * -> 核心内置只读 fallback
 * -> 最小紧急静态页面
 * </pre>
 *
 * <p>
 * 与前面所有层级不同，紧急静态页面不再读取任何模板文件，
 * 也不调用 Thymeleaf 或 Pebble。
 * HTML 由 Java 代码直接生成。
 * </p>
 *
 * <p>因此该页面不依赖：</p>
 *
 * <ul>
 *     <li>workdir/themes 主题目录；</li>
 *     <li>父主题继承结构；</li>
 *     <li>外置 default 主题；</li>
 *     <li>classpath 核心模板资源；</li>
 *     <li>Thymeleaf 模板引擎；</li>
 *     <li>Pebble 模板引擎；</li>
 *     <li>外部 CSS、JavaScript、字体或图片；</li>
 *     <li>数据库中的主题设置。</li>
 * </ul>
 *
 * <p>
 * 即使程序包中的核心模板资源损坏，
 * 或模板引擎在运行阶段发生异常，
 * 上层统一模板渲染服务仍可调用本组件返回最小 HTML。
 * </p>
 *
 * <p>
 * 紧急页面不会把异常堆栈、数据库信息、服务器路径
 * 或第三方主题错误直接展示给访客，
 * 避免泄露敏感运行信息。
 * </p>
 */
@Component
public class EmergencyTemplateRenderer {

    /**
     * 紧急页面使用的固定虚拟主题名称。
     *
     * <p>
     * 它不是真正安装的主题，
     * 只用于渲染结果、日志和后台诊断。
     * </p>
     */
    public static final String EMERGENCY_THEME_NAME =
        "aquafish-emergency";

    /**
     * 紧急页面使用的虚拟模板路径。
     *
     * <p>
     * inline:/ 表示页面由 Java 内联生成，
     * 不是磁盘文件，也不是 classpath 资源。
     * </p>
     */
    public static final String EMERGENCY_TEMPLATE_PATH =
        "inline:/aquafish/emergency.html";

    /**
     * 生成最小紧急静态页面。
     *
     * <p>
     * 为了保证最后回退层足够可靠，
     * 即使 request 为 null，本方法仍会返回成功页面，
     * 而不是再次抛出异常。
     * </p>
     *
     * <p>
     * 请求存在时，只会读取模板类型的安全相对路径，
     * 并经过 HTML 转义后显示在页面底部诊断标识中。
     * </p>
     *
     * @param request 原始模板渲染请求；允许为 null
     * @return 不依赖任何模板引擎的成功渲染结果
     */
    public TemplateRenderResult render(
        TemplateRenderRequest request
    ) {
        String requestedTemplatePath =
            resolveRequestedTemplatePath(
                request
            );

        String html =
            buildEmergencyHtml(
                requestedTemplatePath
            );

        return TemplateRenderResult.success(
            html,
            EMERGENCY_TEMPLATE_PATH,
            EMERGENCY_THEME_NAME,
            false
        );
    }

    /**
     * 判断指定渲染结果是否来自最小紧急静态页面。
     *
     * @param result 模板渲染结果
     * @return 来自紧急页面时返回 true
     */
    public boolean isEmergencyResult(
        TemplateRenderResult result
    ) {
        return result != null
            && EMERGENCY_THEME_NAME.equals(
                result.themeName()
            )
            && EMERGENCY_TEMPLATE_PATH.equals(
                result.templatePath()
            );
    }

    /**
     * 获取原请求需要的模板相对路径。
     *
     * <p>
     * 该值仅用于页面底部的非敏感诊断标识。
     * 不会展示绝对服务器路径。
     * </p>
     *
     * @param request 原始模板请求
     * @return 模板相对路径；无法获取时返回 unknown
     */
    private String resolveRequestedTemplatePath(
        TemplateRenderRequest request
    ) {
        if (
            request == null
                || request.templateType() == null
                || request
                    .templateType()
                    .defaultTemplatePath() == null
                || request
                    .templateType()
                    .defaultTemplatePath()
                    .isBlank()
        ) {
            return "unknown";
        }

        return request
            .templateType()
            .defaultTemplatePath()
            .trim();
    }

    /**
     * 构建完全独立的紧急 HTML。
     *
     * <p>
     * 页面只使用内联 CSS，
     * 不引用任何主题静态资源。
     * </p>
     *
     * @param requestedTemplatePath 原请求模板相对路径
     * @return 完整 HTML 文档
     */
    private String buildEmergencyHtml(
        String requestedTemplatePath
    ) {
        String safeTemplatePath =
            escapeHtml(
                requestedTemplatePath
            );

        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta
                    name="viewport"
                    content="width=device-width, initial-scale=1"
                >
                <title>页面暂时不可用 - Aquafish</title>

                <style>
                    * {
                        box-sizing: border-box;
                    }

                    html {
                        color-scheme: light;
                        background: #f3f5f7;
                    }

                    body {
                        min-height: 100vh;
                        margin: 0;
                        padding: 24px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        color: #172033;
                        background:
                            linear-gradient(
                                145deg,
                                #ffffff 0%,
                                #edf1f5 100%
                            );
                        font-family:
                            Inter,
                            "PingFang SC",
                            "Microsoft YaHei",
                            Arial,
                            sans-serif;
                    }

                    .aq-emergency {
                        width: min(660px, 100%);
                        padding: 40px;
                        border: 1px solid #dce2e8;
                        border-radius: 20px;
                        background: #ffffff;
                        box-shadow:
                            0 24px 70px
                            rgba(23, 32, 51, 0.12);
                    }

                    .aq-brand {
                        margin: 0 0 10px;
                        font-size: 14px;
                        font-weight: 800;
                        letter-spacing: 0.16em;
                        text-transform: uppercase;
                    }

                    .aq-status {
                        display: inline-flex;
                        margin-bottom: 22px;
                        padding: 7px 12px;
                        border-radius: 999px;
                        color: #5a6474;
                        background: #eef2f6;
                        font-size: 13px;
                    }

                    h1 {
                        margin: 0 0 16px;
                        font-size: clamp(30px, 7vw, 46px);
                        line-height: 1.15;
                    }

                    .aq-description {
                        margin: 0;
                        color: #5f6978;
                        font-size: 17px;
                        line-height: 1.8;
                    }

                    .aq-notice {
                        margin-top: 26px;
                        padding: 18px 20px;
                        border-left: 4px solid #334155;
                        border-radius: 10px;
                        color: #4d596a;
                        background: #f6f8fa;
                        line-height: 1.75;
                    }

                    .aq-actions {
                        margin-top: 28px;
                    }

                    .aq-actions a {
                        display: inline-flex;
                        min-height: 42px;
                        padding: 0 18px;
                        align-items: center;
                        justify-content: center;
                        border-radius: 10px;
                        color: #ffffff;
                        background: #172033;
                        text-decoration: none;
                        font-weight: 700;
                    }

                    .aq-reference {
                        margin-top: 28px;
                        color: #8a94a3;
                        font-size: 12px;
                        overflow-wrap: anywhere;
                    }

                    @media (max-width: 560px) {
                        body {
                            padding: 14px;
                        }

                        .aq-emergency {
                            padding: 28px 22px;
                            border-radius: 15px;
                        }
                    }
                </style>
            </head>

            <body>
                <main class="aq-emergency">
                    <p class="aq-brand">
                        Aquafish
                    </p>

                    <div class="aq-status">
                        最小紧急安全页面
                    </div>

                    <h1>
                        页面暂时无法正常显示
                    </h1>

                    <p class="aq-description">
                        系统已经启动最后一层安全保护，
                        当前请求没有继续显示主题或模板错误。
                    </p>

                    <div class="aq-notice">
                        网站数据通常不会因此丢失。
                        管理员可以检查当前主题、外置 default、
                        核心模板资源和模板引擎运行状态。
                    </div>

                    <div class="aq-actions">
                        <a href="/">
                            返回网站首页
                        </a>
                    </div>

                    <div class="aq-reference">
                        requested-template: __AQUAFISH_REQUESTED_TEMPLATE__
                    </div>
                </main>
            </body>
            </html>
            """.replace(
                "__AQUAFISH_REQUESTED_TEMPLATE__",
                safeTemplatePath
            );
    }

    /**
     * 对即将进入 HTML 的文本执行最小必要转义。
     *
     * <p>
     * 紧急页面不依赖 Thymeleaf 自动转义，
     * 因此必须在 Java 层手动处理。
     * </p>
     *
     * @param value 原始文本
     * @return 可安全进入 HTML 的文本
     */
    private String escapeHtml(
        String value
    ) {
        if (value == null) {
            return "";
        }

        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
