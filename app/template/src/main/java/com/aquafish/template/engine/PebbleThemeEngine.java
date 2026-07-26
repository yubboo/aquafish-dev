package com.aquafish.template.engine;

import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.resolve.ResolvedTemplate;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.FileLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Aquafish Pebble 主题模板引擎实现。
 *
 * <p>
 * 本组件是 Aquafish 多模板引擎架构中的 Pebble 实现，
 * 负责渲染 theme.yaml 中声明以下配置的主题：
 * </p>
 *
 * <pre>
 * engine: pebble
 * </pre>
 *
 * <p>
 * 本类只实现 {@link ThemeEngine} 接口，
 * 不直接实现 TemplateRenderService。
 * CMS、论坛、用户中心等业务模块仍然调用统一的
 * DefaultTemplateRenderService。
 * </p>
 *
 * <p>正式渲染调用链如下：</p>
 *
 * <ol>
 *     <li>业务模块提交 TemplateRenderRequest；</li>
 *     <li>DefaultTemplateRenderService 解析当前主题模板；</li>
 *     <li>ResolvedTemplate 提供 engineId；</li>
 *     <li>ThemeEngineRegistry 根据 pebble 找到本组件；</li>
 *     <li>本组件执行 Pebble 模板渲染；</li>
 *     <li>返回统一的 TemplateRenderResult。</li>
 * </ol>
 *
 * <p>
 * 本组件不会影响现有 Thymeleaf 主题。
 * 当主题声明 engine: thymeleaf 时，
 * ThemeEngineRegistry 仍然会选择
 * ThymeleafTemplateRenderService。
 * </p>
 *
 * <p>
 * Aquafish 规定 Thymeleaf 和 Pebble 主题模板
 * 都使用 .html 文件扩展名。
 * 模板引擎类型由 theme.yaml 中的 engine 字段决定，
 * 不通过文件扩展名判断。
 * </p>
 *
 * <p>
 * Pebble 模板中的 include 和 extends 应填写完整的
 * .html 相对路径，例如：
 * </p>
 *
 * <pre>
 * {% include "partial/header.html" %}
 * {% extends "layout/main.html" %}
 * </pre>
 *
 * <p>
 * 本组件不负责以下工作：
 * </p>
 *
 * <ul>
 *     <li>不负责安装、删除或启用主题；</li>
 *     <li>不负责选择当前主题；</li>
 *     <li>不负责解析父主题和子主题；</li>
 *     <li>不负责 default 和核心 fallback 回退；</li>
 *     <li>不负责应用中心下载和主题授权；</li>
 *     <li>不负责直接访问业务数据库。</li>
 * </ul>
 *
 * <p>
 * 安全方面，本组件只接受 ThemeTemplateResolver
 * 已经解析并校验过的模板路径。
 * 同时，传入 Pebble 上下文的数据必须是平台提供的
 * 安全 ViewModel，不能把 Repository、Service、
 * 数据库连接或其他核心内部对象暴露给第三方主题。
 * </p>
 */
@Component
public class PebbleThemeEngine implements ThemeEngine {

    /**
     * Pebble 模板引擎在 Aquafish 中的唯一标识。
     *
     * <p>
     * 该值必须与 theme.yaml 中的 engine 字段、
     * ThemeEngineRegistry 注册标识以及后台配置保持一致。
     * </p>
     */
    private static final String ENGINE_ID = "pebble";

    /**
     * 返回当前模板引擎的唯一标识。
     *
     * @return 固定返回 pebble
     */
    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    /**
     * 使用 Pebble 渲染已经完成路径解析的主题模板。
     *
     * <p>本方法执行以下步骤：</p>
     *
     * <ol>
     *     <li>检查渲染请求是否为空；</li>
     *     <li>检查模板解析结果是否为空；</li>
     *     <li>确认解析结果确实属于 Pebble 引擎；</li>
     *     <li>确认模板文件真实存在；</li>
     *     <li>计算当前主题的 templates 根目录；</li>
     *     <li>创建以该目录为边界的 FileLoader；</li>
     *     <li>创建 PebbleEngine；</li>
     *     <li>加载并编译模板；</li>
     *     <li>传入页面数据和 Locale 执行渲染；</li>
     *     <li>返回统一模板渲染结果。</li>
     * </ol>
     *
     * <p>
     * 本方法不会自行选择其他主题或模板。
     * 如果模板不存在，后续应由统一回退解析链继续寻找
     * 父主题、default 或核心 fallback。
     * </p>
     *
     * @param request 模板渲染请求，
     *                包含模板类型、页面语言和页面数据
     * @param resolvedTemplate 已经完成路径解析和安全校验的模板结果
     * @return Pebble 模板渲染结果
     */
    @Override
    public TemplateRenderResult render(
        TemplateRenderRequest request,
        ResolvedTemplate resolvedTemplate
    ) {
        if (request == null) {
            return TemplateRenderResult.failure(
                "模板渲染请求不能为空。"
            );
        }

        if (resolvedTemplate == null) {
            return TemplateRenderResult.failure(
                "已解析模板不能为空。"
            );
        }

        /*
         * 防止调度器或未来代码错误地把其他引擎模板
         * 交给 Pebble 处理。
         *
         * 正常情况下 DefaultTemplateRenderService
         * 已经通过 ThemeEngineRegistry 完成正确选择。
         * 这里属于第二层防御性校验。
         */
        if (
            !ENGINE_ID.equalsIgnoreCase(
                resolvedTemplate.engineId()
            )
        ) {
            return TemplateRenderResult.failure(
                "模板引擎不匹配，Pebble 不能渲染："
                    + resolvedTemplate.engineId()
            );
        }

        if (!resolvedTemplate.exists()) {
            return TemplateRenderResult.failure(
                "Pebble 模板文件不存在："
                    + resolvedTemplate.absoluteTemplatePath()
            );
        }

        try {
            /*
             * 将最终模板文件路径转换为规范化绝对路径。
             *
             * ThemeTemplateResolver 已经执行过目录边界检查，
             * 此处继续使用规范化路径，避免工作目录变化
             * 导致 FileLoader 定位到错误目录。
             */
            Path templateFile = Path.of(
                    resolvedTemplate.absoluteTemplatePath()
                )
                .toAbsolutePath()
                .normalize();

            /*
             * 根据具体模板文件和相对模板路径，
             * 反推出当前主题的 templates 根目录。
             */
            Path templatesDir = resolveTemplatesDir(
                templateFile,
                resolvedTemplate.relativeTemplatePath()
            );

            /*
             * Pebble 4.1.x 的 FileLoader
             * 必须在构造时提供基础目录。
             *
             * 这里传入规范化绝对路径，
             * 使所有 include、extends 和模板加载
             * 都被限制在当前主题 templates 目录中。
             */
            FileLoader loader = new FileLoader(
                templatesDir.toString()
            );

            /*
             * 所有 Aquafish 主题文件统一使用 UTF-8，
             * 防止中文模板在不同系统中出现乱码。
             */
            loader.setCharset(
                StandardCharsets.UTF_8.name()
            );

            /*
             * 此处不设置 FileLoader suffix。
             *
             * 因为 Aquafish Pebble 主题统一在模板名称、
             * include 和 extends 中使用完整 .html 路径。
             *
             * 如果同时设置 suffix = .html，
             * partial/header.html 可能被错误解析成
             * partial/header.html.html。
             */
            PebbleEngine pebbleEngine =
                createPebbleEngine(loader);

            /*
             * Pebble 使用相对于 templates 根目录的路径加载模板。
             *
             * 路径中的反斜杠统一转换成正斜杠，
             * 保证主题包可以跨 Windows 和 Linux 使用。
             */
            String templateName =
                normalizeTemplateName(
                    resolvedTemplate.relativeTemplatePath()
                );

            PebbleTemplate template =
                pebbleEngine.getTemplate(templateName);

            /*
             * StringWriter 用于接收 Pebble 输出的最终 HTML。
             *
             * 模板渲染结束后直接通过 toString()
             * 取得完整页面内容。
             */
            StringWriter writer = new StringWriter();

            /*
             * 将 Aquafish 统一页面模型和请求 Locale
             * 一同传入 Pebble。
             *
             * Thymeleaf 与 Pebble 共用相同的 ViewModel key，
             * 但各自使用自己的模板语法。
             */
            template.evaluate(
                writer,
                request.model(),
                request.locale()
            );

            return TemplateRenderResult.success(
                writer.toString(),
                resolvedTemplate.absoluteTemplatePath(),
                resolvedTemplate.themeName(),
                false
            );
        } catch (Exception error) {
            /*
             * 模板语法错误、文件加载错误和执行错误
             * 统一转换成 TemplateRenderResult。
             *
             * 普通访客页面不应该直接看到服务器堆栈；
             * 完整异常后续由日志和后台主题诊断处理。
             */
            return TemplateRenderResult.failure(
                "Pebble 模板渲染失败："
                    + error.getMessage()
            );
        }
    }

    /**
     * 创建当前主题使用的 PebbleEngine。
     *
     * <p>当前配置说明：</p>
     *
     * <ul>
     *     <li>使用当前主题专属 FileLoader；</li>
     *     <li>开启 HTML 自动转义；</li>
     *     <li>暂时关闭模板缓存，方便开发阶段刷新；</li>
     *     <li>暂时关闭严格变量模式，兼容不完整页面数据。</li>
     * </ul>
     *
     * <p>
     * 自动转义用于降低普通模板变量造成 HTML 注入的风险。
     * 但自动转义不能代替安全 ViewModel 和内容清洗。
     * </p>
     *
     * <p>
     * 后续进入生产缓存阶段后，
     * 缓存必须按照主题名称、主题版本和模板路径隔离，
     * 不能让不同主题错误共用编译模板。
     * </p>
     *
     * @param loader 限制在当前主题 templates 目录的文件加载器
     * @return 配置完成的 PebbleEngine
     */
    private PebbleEngine createPebbleEngine(
        FileLoader loader
    ) {
        return new PebbleEngine.Builder()
            .loader(loader)
            .autoEscaping(true)
            .strictVariables(false)
            .cacheActive(false)
            .build();
    }

    /**
     * 根据最终模板文件和相对路径，
     * 反推出当前主题的 templates 根目录。
     *
     * <p>例如：</p>
     *
     * <pre>
     * 模板绝对路径：
     * H:/javaweb/aquafish/themes/demo/templates/forum/index.html
     *
     * 相对模板路径：
     * forum/index.html
     *
     * 最终 templates 目录：
     * H:/javaweb/aquafish/themes/demo/templates
     * </pre>
     *
     * @param templateFile 模板文件规范化绝对路径
     * @param relativeTemplatePath 相对于 templates 的模板路径
     * @return 当前主题规范化绝对 templates 目录
     */
    private Path resolveTemplatesDir(
        Path templateFile,
        String relativeTemplatePath
    ) {
        String normalizedRelativePath =
            normalizeTemplateName(
                relativeTemplatePath
            );

        Path relativePath = Path.of(
            normalizedRelativePath
        );

        Path cursor = templateFile;

        /*
         * 相对模板路径包含几个名称段，
         * 就从模板文件位置向上返回几级。
         *
         * forum/index.html 包含两个名称段，
         * 因而向上两级后得到 templates 目录。
         */
        for (
            int index = 0;
            index < relativePath.getNameCount();
            index++
        ) {
            cursor = cursor.getParent();

            if (cursor == null) {
                throw new IllegalArgumentException(
                    "无法根据模板路径计算 templates 目录："
                        + templateFile
                );
            }
        }

        return cursor
            .toAbsolutePath()
            .normalize();
    }

    /**
     * 标准化 Pebble 使用的模板名称。
     *
     * <p>处理规则：</p>
     *
     * <ol>
     *     <li>去除首尾空格；</li>
     *     <li>将 Windows 反斜杠转换为正斜杠；</li>
     *     <li>保留完整 .html 扩展名。</li>
     * </ol>
     *
     * <p>
     * 与 Thymeleaf 实现不同，Pebble 当前不配置统一 suffix，
     * 所以模板名称必须保留 .html。
     * </p>
     *
     * @param relativeTemplatePath 原始模板相对路径
     * @return Pebble 使用的标准化模板名称
     */
    private String normalizeTemplateName(
        String relativeTemplatePath
    ) {
        if (
            relativeTemplatePath == null
                || relativeTemplatePath.isBlank()
        ) {
            throw new IllegalArgumentException(
                "Pebble 模板相对路径不能为空。"
            );
        }

        return relativeTemplatePath
            .trim()
            .replace("\\", "/");
    }
}
