package com.aquafish.template.engine;

import com.aquafish.template.core.TemplateRenderRequest;
import com.aquafish.template.core.TemplateRenderResult;
import com.aquafish.template.core.TemplateRenderService;
import com.aquafish.template.resolve.ResolvedTemplate;
import com.aquafish.template.resolve.ThemeTemplateResolver;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;

/**
 * Aquafish Thymeleaf 模板引擎。
 *
 * <p>
 * 本组件既保留早期的 {@link TemplateRenderService} 入口，
 * 也实现统一双引擎架构中的 {@link ThemeEngine}。
 * </p>
 *
 * <p>当前支持两种模板来源：</p>
 *
 * <ol>
 *     <li>
 *         外置主题磁盘文件；
 *     </li>
 *     <li>
 *         打包在应用程序 JAR 中的 classpath 核心模板。
 *     </li>
 * </ol>
 *
 * <p>外置主题路径示例：</p>
 *
 * <pre>
 * ${user.home}/.aquafish/dev/themes/default/templates/index.html
 * </pre>
 *
 * <p>核心内置模板路径示例：</p>
 *
 * <pre>
 * classpath:/aquafish/core-fallback/templates/index.html
 * </pre>
 *
 * <p>两种模板分别使用：</p>
 *
 * <pre>
 * 普通磁盘路径
 * -> FileTemplateResolver
 *
 * classpath:/ 路径
 * -> ClassLoaderTemplateResolver
 * </pre>
 *
 * <p>
 * 核心 fallback 资源不能通过 {@link Path#of(String)}
 * 当作普通磁盘文件读取，因为它在正式发布后可能位于 JAR 内部。
 * 因此必须使用 ClassLoaderTemplateResolver。
 * </p>
 */
@Component
public class ThymeleafTemplateRenderService
    implements TemplateRenderService, ThemeEngine {

    /**
     * Thymeleaf 模板引擎固定标识。
     */
    public static final String ENGINE_ID =
        "thymeleaf";

    /**
     * classpath 路径协议前缀。
     */
    private static final String CLASSPATH_PREFIX =
        "classpath:/";

    /**
     * 当前主题模板解析器。
     *
     * <p>
     * 旧的 TemplateRenderService 入口会通过它
     * 自动解析当前模板。
     * </p>
     *
     * <p>
     * 新的统一调度器会先解析模板，
     * 再调用双参数 render 方法。
     * </p>
     */
    private final ThemeTemplateResolver
        themeTemplateResolver;

    /**
     * 创建 Thymeleaf 模板引擎。
     *
     * @param themeTemplateResolver 主题模板解析器
     */
    public ThymeleafTemplateRenderService(
        ThemeTemplateResolver themeTemplateResolver
    ) {
        if (themeTemplateResolver == null) {
            throw new IllegalArgumentException(
                "主题模板解析器不能为空。"
            );
        }

        this.themeTemplateResolver =
            themeTemplateResolver;
    }

    /**
     * 返回当前模板引擎标识。
     *
     * @return 固定返回 thymeleaf
     */
    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    /**
     * 兼容早期 TemplateRenderService 的统一渲染入口。
     *
     * <p>
     * 当前方法会先通过 ThemeTemplateResolver
     * 找到最终模板，再调用 ThemeEngine 双参数入口。
     * </p>
     *
     * @param request 模板渲染请求
     * @return 模板渲染结果
     */
    @Override
    public TemplateRenderResult render(
        TemplateRenderRequest request
    ) {
        if (request == null) {
            return TemplateRenderResult.failure(
                "模板渲染请求不能为空。"
            );
        }

        try {
            ResolvedTemplate resolvedTemplate =
                themeTemplateResolver.resolve(
                    request.templateType()
                );

            return render(
                request,
                resolvedTemplate
            );
        } catch (Exception error) {
            return TemplateRenderResult.failure(
                "Thymeleaf 模板解析失败："
                    + safeErrorMessage(error)
            );
        }
    }

    /**
     * 使用已经解析完成的模板执行 Thymeleaf 渲染。
     *
     * <p>
     * 该方法由 ThemeEngineRegistry 和
     * DefaultTemplateRenderService 调用。
     * </p>
     *
     * <p>处理流程：</p>
     *
     * <ol>
     *     <li>检查请求和解析结果；</li>
     *     <li>确认模板真实存在；</li>
     *     <li>确认模板声明的引擎为 Thymeleaf；</li>
     *     <li>识别磁盘模板或 classpath 模板；</li>
     *     <li>创建对应的 Thymeleaf resolver；</li>
     *     <li>注入安全 ViewModel；</li>
     *     <li>渲染并返回 HTML。</li>
     * </ol>
     *
     * @param request 模板渲染请求
     * @param resolvedTemplate 已解析模板
     * @return 模板渲染结果
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
                "已解析模板结果不能为空。"
            );
        }

        if (!resolvedTemplate.exists()) {
            return TemplateRenderResult.failure(
                "模板文件不存在："
                    + resolvedTemplate
                        .absoluteTemplatePath()
            );
        }

        if (
            !ENGINE_ID.equals(
                resolvedTemplate.engineId()
            )
        ) {
            return TemplateRenderResult.failure(
                "模板引擎不匹配：Thymeleaf 不能渲染 "
                    + resolvedTemplate.engineId()
                    + " 模板。"
            );
        }

        try {
            String templateName =
                toThymeleafTemplateName(
                    resolvedTemplate
                        .relativeTemplatePath()
                );

            TemplateEngine templateEngine;

            if (
                isClasspathTemplate(
                    resolvedTemplate
                        .absoluteTemplatePath()
                )
            ) {
                /*
                 * 核心内置 fallback 或其他 classpath 模板。
                 */
                String classpathRoot =
                    resolveClasspathTemplateRoot(
                        resolvedTemplate
                    );

                templateEngine =
                    createClasspathTemplateEngine(
                        classpathRoot
                    );
            } else {
                /*
                 * 普通外置主题磁盘模板。
                 */
                Path templateFile = Path.of(
                        resolvedTemplate
                            .absoluteTemplatePath()
                    )
                    .toAbsolutePath()
                    .normalize();

                Path templatesDir =
                    resolveTemplatesDir(
                        templateFile,
                        resolvedTemplate
                            .relativeTemplatePath()
                    );

                templateEngine =
                    createFileTemplateEngine(
                        templatesDir
                    );
            }

            Context context =
                new Context(request.locale());

            for (
                Map.Entry<String, Object> entry
                    : request.model().entrySet()
            ) {
                context.setVariable(
                    entry.getKey(),
                    entry.getValue()
                );
            }

            String html = templateEngine.process(
                templateName,
                context
            );

            return TemplateRenderResult.success(
                html,
                resolvedTemplate
                    .absoluteTemplatePath(),
                resolvedTemplate.themeName(),
                false
            );
        } catch (Exception error) {
            return TemplateRenderResult.failure(
                "Thymeleaf 模板渲染失败："
                    + safeErrorMessage(error)
            );
        }
    }

    /**
     * 创建用于外置主题文件的 Thymeleaf 引擎。
     *
     * @param templatesDir 当前主题 templates 目录
     * @return Thymeleaf 模板引擎
     */
    private TemplateEngine createFileTemplateEngine(
        Path templatesDir
    ) {
        FileTemplateResolver resolver =
            new FileTemplateResolver();

        resolver.setPrefix(
            ensureTrailingSeparator(
                templatesDir.toString()
            )
        );

        configureCommonResolverOptions(
            resolver
        );

        TemplateEngine engine =
            new TemplateEngine();

        engine.setTemplateResolver(
            resolver
        );

        return engine;
    }

    /**
     * 创建用于 JAR/classpath 模板的 Thymeleaf 引擎。
     *
     * <p>
     * ClassLoaderTemplateResolver 的 prefix
     * 不能以正斜杠开头。
     * </p>
     *
     * @param classpathRoot classpath 中的模板资源根目录
     * @return Thymeleaf 模板引擎
     */
    private TemplateEngine
        createClasspathTemplateEngine(
            String classpathRoot
        ) {
        ClassLoaderTemplateResolver resolver =
            new ClassLoaderTemplateResolver();

        resolver.setPrefix(
            ensureClasspathTrailingSlash(
                classpathRoot
            )
        );

        configureCommonResolverOptions(
            resolver
        );

        TemplateEngine engine =
            new TemplateEngine();

        engine.setTemplateResolver(
            resolver
        );

        return engine;
    }

    /**
     * 为 FileTemplateResolver 设置公共选项。
     *
     * @param resolver 文件模板解析器
     */
    private void configureCommonResolverOptions(
        FileTemplateResolver resolver
    ) {
        resolver.setSuffix(".html");
        resolver.setTemplateMode(
            TemplateMode.HTML
        );
        resolver.setCharacterEncoding(
            StandardCharsets.UTF_8.name()
        );
        resolver.setCacheable(false);
        resolver.setCheckExistence(true);
    }

    /**
     * 为 ClassLoaderTemplateResolver 设置公共选项。
     *
     * @param resolver classpath 模板解析器
     */
    private void configureCommonResolverOptions(
        ClassLoaderTemplateResolver resolver
    ) {
        resolver.setSuffix(".html");
        resolver.setTemplateMode(
            TemplateMode.HTML
        );
        resolver.setCharacterEncoding(
            StandardCharsets.UTF_8.name()
        );
        resolver.setCacheable(false);
        resolver.setCheckExistence(true);
    }

    /**
     * 判断模板是否位于 classpath。
     *
     * @param absoluteTemplatePath 已解析模板路径
     * @return 以 classpath:/ 开头时返回 true
     */
    private boolean isClasspathTemplate(
        String absoluteTemplatePath
    ) {
        return absoluteTemplatePath != null
            && absoluteTemplatePath
                .startsWith(CLASSPATH_PREFIX);
    }

    /**
     * 从完整 classpath 模板路径中计算资源根目录。
     *
     * <p>示例：</p>
     *
     * <pre>
     * absoluteTemplatePath:
     * classpath:/aquafish/core-fallback/templates/content/view.html
     *
     * relativeTemplatePath:
     * content/view.html
     *
     * 返回：
     * aquafish/core-fallback/templates/
     * </pre>
     *
     * @param resolvedTemplate 已解析模板
     * @return 不以斜杠开头、以斜杠结尾的 classpath 根目录
     */
    private String resolveClasspathTemplateRoot(
        ResolvedTemplate resolvedTemplate
    ) {
        String absolutePath =
            resolvedTemplate
                .absoluteTemplatePath()
                .trim()
                .replace("\\", "/");

        String relativePath =
            resolvedTemplate
                .relativeTemplatePath()
                .trim()
                .replace("\\", "/");

        if (
            !absolutePath.startsWith(
                CLASSPATH_PREFIX
            )
        ) {
            throw new IllegalArgumentException(
                "模板不是 classpath 资源："
                    + absolutePath
            );
        }

        String resourcePath =
            absolutePath.substring(
                CLASSPATH_PREFIX.length()
            );

        if (
            !resourcePath.endsWith(
                relativePath
            )
        ) {
            throw new IllegalArgumentException(
                "classpath 模板路径与相对路径不匹配："
                    + absolutePath
                    + "，相对路径："
                    + relativePath
            );
        }

        String root = resourcePath.substring(
            0,
            resourcePath.length()
                - relativePath.length()
        );

        return ensureClasspathTrailingSlash(
            root
        );
    }

    /**
     * 根据普通模板文件反推出 templates 根目录。
     *
     * @param templateFile 模板绝对文件路径
     * @param relativeTemplatePath 模板相对路径
     * @return templates 根目录
     */
    private Path resolveTemplatesDir(
        Path templateFile,
        String relativeTemplatePath
    ) {
        String normalizedRelativePath =
            relativeTemplatePath
                .replace("\\", "/");

        Path relativePath = Path.of(
            normalizedRelativePath
        );

        Path cursor = templateFile;

        for (
            int index = 0;
            index < relativePath.getNameCount();
            index++
        ) {
            cursor = cursor.getParent();

            if (cursor == null) {
                throw new IllegalArgumentException(
                    "无法根据模板路径推导 templates 目录："
                        + templateFile
                );
            }
        }

        return cursor
            .toAbsolutePath()
            .normalize();
    }

    /**
     * 把相对模板路径转换成 Thymeleaf 模板名称。
     *
     * <p>输入：</p>
     *
     * <pre>
     * forum/viewthread.html
     * </pre>
     *
     * <p>输出：</p>
     *
     * <pre>
     * forum/viewthread
     * </pre>
     *
     * @param relativeTemplatePath 模板相对路径
     * @return 不带 .html 后缀的模板名称
     */
    private String toThymeleafTemplateName(
        String relativeTemplatePath
    ) {
        if (
            relativeTemplatePath == null
                || relativeTemplatePath.isBlank()
        ) {
            throw new IllegalArgumentException(
                "Thymeleaf 模板相对路径不能为空。"
            );
        }

        String value = relativeTemplatePath
            .replace("\\", "/")
            .trim();

        if (value.endsWith(".html")) {
            return value.substring(
                0,
                value.length()
                    - ".html".length()
            );
        }

        return value;
    }

    /**
     * 保证磁盘目录以系统分隔符结尾。
     *
     * @param value 原始目录
     * @return 带结尾目录分隔符的路径
     */
    private String ensureTrailingSeparator(
        String value
    ) {
        if (
            value.endsWith("/")
                || value.endsWith("\\")
        ) {
            return value;
        }

        return value
            + java.io.File.separator;
    }

    /**
     * 保证 classpath 资源根目录格式正确。
     *
     * <p>返回结果：</p>
     *
     * <ul>
     *     <li>不以 / 开头；</li>
     *     <li>以 / 结尾；</li>
     *     <li>统一使用正斜杠。</li>
     * </ul>
     *
     * @param value 原始 classpath 根目录
     * @return 标准化资源根目录
     */
    private String ensureClasspathTrailingSlash(
        String value
    ) {
        if (value == null) {
            return "";
        }

        String normalized = value
            .trim()
            .replace("\\", "/");

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (
            !normalized.isEmpty()
                && !normalized.endsWith("/")
        ) {
            normalized = normalized + "/";
        }

        return normalized;
    }

    /**
     * 获取适合返回给调用方的异常信息。
     *
     * @param error 捕获到的异常
     * @return 非空错误说明
     */
    private String safeErrorMessage(
        Exception error
    ) {
        if (
            error.getMessage() == null
                || error.getMessage().isBlank()
        ) {
            return error
                .getClass()
                .getSimpleName();
        }

        return error.getMessage();
    }
}
