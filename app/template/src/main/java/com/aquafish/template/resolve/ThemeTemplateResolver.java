package com.aquafish.template.resolve;

import com.aquafish.template.core.TemplateType;
import com.aquafish.template.core.TemplateTypes;
import com.aquafish.theme.core.ActiveThemeResolver;
import com.aquafish.theme.core.DefaultThemeResolver;
import com.aquafish.theme.core.ThemeDescriptor;
import com.aquafish.theme.core.ThemeInheritanceResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Aquafish 主题模板文件解析器。
 *
 * <p>
 * 本组件位于主题管理模块和模板渲染引擎之间，
 * 负责把平台定义的 {@link TemplateType} 转换成服务器上
 * 最终可供 Thymeleaf 或 Pebble 渲染的主题模板文件。
 * </p>
 *
 * <p>
 * 当前已经支持父主题继承。
 * 模板解析器不再只检查当前启用主题，
 * 而是按照完整主题继承链依次查找模板。
 * </p>
 *
 * <p>当前模板查找顺序：</p>
 *
 * <pre>
 * 当前启用主题
 * -> 直接父主题
 * -> 更上一级父主题
 * -> 最顶层根主题
 * -> 外置官方 default 主题
 * -> 核心内置只读 fallback
 * </pre>
 *
 * <p>
 * 外置 default 不属于父主题继承链。
 * 它是父主题继承链全部缺少目标模板后，
 * 才会进入的独立安全回退层。
 * </p>
 *
 * <p>
 * 因为 default 不是父主题继承，
 * 所以允许它与当前活动主题使用不同模板引擎。
 * 最终由 ResolvedTemplate.engineId
 * 决定调用 Thymeleaf 或 Pebble。
 * </p>
 *
 * <p>例如：</p>
 *
 * <pre>
 * child-theme
 *     缺少 forum/viewthread.html
 *
 * parent-theme
 *     存在 forum/viewthread.html
 * </pre>
 *
 * <p>
 * 最终返回的 {@link ResolvedTemplate} 会记录：
 * </p>
 *
 * <ul>
 *     <li>真正提供模板文件的主题名称；</li>
 *     <li>真正提供模板文件的主题引擎；</li>
 *     <li>模板相对路径；</li>
 *     <li>模板绝对路径；</li>
 *     <li>模板是否存在；</li>
 *     <li>模板来自继承链哪一层的诊断说明。</li>
 * </ul>
 *
 * <p>
 * 父主题与子主题必须使用同一种模板引擎。
 * 该规则由 ThemeParentResolver 和
 * {@link ThemeInheritanceResolver} 在生成继承链时校验。
 * </p>
 *
 * <p>
 * 当前版本已经接入：
 * </p>
 *
 * <pre>
 * 当前活动主题
 * -> 父主题继承链
 * -> 外置官方 default
 * -> 核心内置只读 fallback
 * </pre>
 *
 * <p>
 * 当前尚未加入最后的最小紧急静态页面。
 * 只有核心 fallback 资源也异常缺失时，
 * 本解析器才会返回 exists=false。
 * </p>
 *
 * <p>
 * 由于主题可能由第三方开发者提供，
 * 每一个主题目录中的模板路径都必须独立执行安全校验，
 * 防止通过路径跳转读取主题目录之外的服务器文件。
 * </p>
 */
@Component
public class ThemeTemplateResolver {

    /**
     * 当前启用主题解析器。
     *
     * <p>
     * 负责读取 Aquafish 当前启用的主题描述对象。
     * 本组件不会自行读取数据库或配置文件决定当前主题。
     * </p>
     */
    private final ActiveThemeResolver activeThemeResolver;

    /**
     * 完整主题继承链解析器。
     *
     * <p>
     * 用于生成：
     * </p>
     *
     * <pre>
     * 当前主题
     * -> 父主题
     * -> 根主题
     * </pre>
     *
     * <p>
     * 同时负责循环继承、继承深度、父主题缺失
     * 和跨模板引擎继承等规则校验。
     * </p>
     */
    private final ThemeInheritanceResolver
        themeInheritanceResolver;

    /**
     * 外置官方 default 主题解析器。
     *
     * <p>
     * 当前主题及全部父主题都缺少目标模板时，
     * 模板解析器会通过该组件查找固定名称为 default
     * 的外置官方主题。
     * </p>
     *
     * <p>
     * default 回退与父主题继承彼此独立，
     * 因此 default 可以使用与当前主题不同的模板引擎。
     * </p>
     */
    private final DefaultThemeResolver
        defaultThemeResolver;

    /**
     * Aquafish 核心内置只读 fallback 解析器。
     *
     * <p>
     * 当前主题、父主题继承链和外置 default
     * 都没有目标模板时，通过该组件查找
     * template 模块 JAR 中的核心安全模板。
     * </p>
     *
     * <p>
     * 核心 fallback 固定使用 Thymeleaf，
     * 模板路径使用 classpath:/ 协议。
     * </p>
     */
    private final CoreFallbackTemplateResolver
        coreFallbackTemplateResolver;

    /**
     * 创建生产环境使用的主题模板解析器。
     *
     * <p>
     * Spring 会优先使用本构造方法，
     * 自动注入当前启用主题解析器和完整主题继承链解析器。
     * </p>
     *
     * @param activeThemeResolver 当前启用主题解析器，
     *                            不允许为 null
     * @param themeInheritanceResolver 完整主题继承链解析器，
     *                                 不允许为 null
     */
    @Autowired
    public ThemeTemplateResolver(
        ActiveThemeResolver activeThemeResolver,
        ThemeInheritanceResolver themeInheritanceResolver,
        DefaultThemeResolver defaultThemeResolver,
        CoreFallbackTemplateResolver
            coreFallbackTemplateResolver
    ) {
        if (activeThemeResolver == null) {
            throw new IllegalArgumentException(
                "当前启用主题解析器不能为空。"
            );
        }

        if (themeInheritanceResolver == null) {
            throw new IllegalArgumentException(
                "主题继承链解析器不能为空。"
            );
        }

        if (defaultThemeResolver == null) {
            throw new IllegalArgumentException(
                "外置 default 主题解析器不能为空。"
            );
        }

        if (coreFallbackTemplateResolver == null) {
            throw new IllegalArgumentException(
                "核心 fallback 模板解析器不能为空。"
            );
        }

        this.activeThemeResolver =
            activeThemeResolver;

        this.themeInheritanceResolver =
            themeInheritanceResolver;

        this.defaultThemeResolver =
            defaultThemeResolver;

        this.coreFallbackTemplateResolver =
            coreFallbackTemplateResolver;
    }

    /**
     * 兼容第 28 步之后已有测试代码的构造方法。
     *
     * <p>
     * 使用该构造方法时可以使用父主题继承链
     * 和外置 default 回退，
     * 但不会继续进入核心内置 fallback。
     * </p>
     *
     * <p>
     * 正式 Spring 生产环境使用上面的四参数构造方法。
     * </p>
     *
     * @param activeThemeResolver 当前启用主题解析器
     * @param themeInheritanceResolver 主题继承链解析器
     * @param defaultThemeResolver 外置 default 主题解析器
     */
    public ThemeTemplateResolver(
        ActiveThemeResolver activeThemeResolver,
        ThemeInheritanceResolver themeInheritanceResolver,
        DefaultThemeResolver defaultThemeResolver
    ) {
        this.activeThemeResolver =
            activeThemeResolver;

        this.themeInheritanceResolver =
            themeInheritanceResolver;

        this.defaultThemeResolver =
            defaultThemeResolver;

        this.coreFallbackTemplateResolver =
            null;
    }

    /**
     * 兼容第 24 步之后已有测试代码的构造方法。
     *
     * <p>
     * 使用该构造方法时可以正常使用父主题继承链，
     * 但不会继续进入外置 default 回退。
     * </p>
     *
     * <p>
     * 新的生产环境代码应使用 Spring 自动注入的
     * 三参数构造方法。
     * </p>
     *
     * @param activeThemeResolver 当前启用主题解析器
     * @param themeInheritanceResolver 完整主题继承链解析器
     */
    public ThemeTemplateResolver(
        ActiveThemeResolver activeThemeResolver,
        ThemeInheritanceResolver themeInheritanceResolver
    ) {
        this.activeThemeResolver =
            activeThemeResolver;

        this.themeInheritanceResolver =
            themeInheritanceResolver;

        this.defaultThemeResolver =
            null;

        this.coreFallbackTemplateResolver =
            null;
    }

    /**
     * 兼容早期测试和手动装配代码的构造方法。
     *
     * <p>
     * 早期测试只传入 ActiveThemeResolver，
     * 尚未创建 ThemeInheritanceResolver。
     * 为避免一次修改导致已有测试全部无法编译，
     * 暂时保留该构造方法。
     * </p>
     *
     * <p>
     * 使用该构造方法时只会查找当前主题，
     * 不会进入父主题继承链。
     * Spring 生产环境不会选择该构造方法，
     * 因为上面的双参数构造方法已经标记为
     * {@link Autowired}。
     * </p>
     *
     * @param activeThemeResolver 当前启用主题解析器
     * @deprecated 仅用于兼容早期测试和手动装配代码；
     *             新代码应传入 ThemeInheritanceResolver
     */
    @Deprecated(forRemoval = false)
    public ThemeTemplateResolver(
        ActiveThemeResolver activeThemeResolver
    ) {
        this.activeThemeResolver =
            activeThemeResolver;

        this.themeInheritanceResolver =
            null;

        this.defaultThemeResolver =
            null;

        this.coreFallbackTemplateResolver =
            null;
    }

    /**
     * 根据模板类型解析当前主题继承链中的模板文件。
     *
     * <p>处理流程：</p>
     *
     * <ol>
     *     <li>检查模板类型是否为空；</li>
     *     <li>读取当前启用主题；</li>
     *     <li>生成完整主题继承链；</li>
     *     <li>标准化模板相对路径；</li>
     *     <li>从当前主题开始逐级查找模板；</li>
     *     <li>为每一个主题执行独立目录边界校验；</li>
     *     <li>找到第一个普通模板文件后立即返回；</li>
     *     <li>全部主题均缺失时返回不存在结果。</li>
     * </ol>
     *
     * <p>
     * 查找遵循“越具体越优先”原则。
     * 子主题模板始终优先于父主题模板，
     * 父主题只用于补充子主题没有覆盖的模板。
     * </p>
     *
     * @param templateType 需要解析的模板类型，不允许为 null
     * @return 最先找到的主题模板解析结果；
     *         全部缺失时返回 exists=false 的结果
     * @throws IllegalArgumentException 当模板类型或模板路径非法时抛出
     * @throws IllegalStateException 当当前主题不可用、
     *                               父主题缺失、
     *                               循环继承、
     *                               继承过深或跨引擎继承时抛出
     */
    public ResolvedTemplate resolve(
        TemplateType templateType
    ) {
        if (templateType == null) {
            throw new IllegalArgumentException(
                "模板类型不能为空。"
            );
        }

        /*
         * 获取当前启用主题。
         *
         * 当前主题是整个模板查找继承链的起点。
         */
        ThemeDescriptor activeTheme =
            activeThemeResolver.requireActiveTheme();

        /*
         * 生成模板查找使用的主题继承链。
         *
         * 生产环境中会返回：
         * 当前主题 -> 父主题 -> 根主题。
         *
         * 使用早期兼容构造方法时，
         * 只返回当前主题自身。
         */
        List<ThemeDescriptor> searchChain =
            resolveSearchChain(activeTheme);

        /*
         * 模板相对路径只需要标准化一次。
         *
         * 之后会把同一个安全相对路径
         * 分别拼接到继承链中每个主题的 templates 目录。
         */
        String relativeTemplatePath =
            normalizeRelativeTemplatePath(
                templateType.defaultTemplatePath()
            );

        /*
         * 按照子主题到根主题的顺序依次查找。
         *
         * 找到第一个普通文件后立即返回，
         * 从而保证子主题能够覆盖父主题模板。
         */
        for (
            int index = 0;
            index < searchChain.size();
            index++
        ) {
            ThemeDescriptor candidateTheme =
                searchChain.get(index);

            Path templateFile =
                resolveTemplateFile(
                    candidateTheme,
                    relativeTemplatePath
                );

            if (!Files.isRegularFile(templateFile)) {
                continue;
            }

            boolean inherited =
                index > 0;

            String message = inherited
                ? "当前主题缺少模板，已从父主题 "
                    + candidateTheme.name()
                    + " 继承模板。查找链："
                    + formatSearchChain(searchChain)
                : "已找到当前主题模板。查找链："
                    + formatSearchChain(searchChain);

            /*
             * themeName 和 engineId 必须记录
             * 真正提供模板文件的主题。
             *
             * 当前父子主题引擎必须一致，
             * 但仍然记录实际来源主题的数据，
             * 为后台诊断和后续回退链提供准确信息。
             */
            return new ResolvedTemplate(
                templateType,
                candidateTheme.name(),
                candidateTheme.engine(),
                relativeTemplatePath,
                templateFile.toString(),
                true,
                message
            );
        }

        /*
         * 当前主题和全部父主题都没有找到模板后，
         * 继续尝试独立的外置官方 default 回退层。
         *
         * default 不是当前主题的父主题，
         * 因此允许使用不同模板引擎。
         */
        Optional<ResolvedTemplate> defaultTemplate =
            resolveExternalDefaultTemplate(
                templateType,
                relativeTemplatePath,
                searchChain
            );

        if (defaultTemplate.isPresent()) {
            return defaultTemplate.orElseThrow();
        }

        /*
         * 当前主题继承链和外置 default
         * 都没有找到模板后，
         * 继续尝试应用程序 JAR 中的核心只读 fallback。
         */
        Optional<ResolvedTemplate> coreFallbackTemplate =
            resolveCoreFallbackTemplate(
                templateType
            );

        if (coreFallbackTemplate.isPresent()) {
            return coreFallbackTemplate.orElseThrow();
        }

        /*
         * 正常发布版本的 16 个核心模板都应该存在。
         *
         * 只有程序包损坏、资源打包错误或核心模板被错误移除时，
         * 才会执行到这里。
         *
         * 本步骤暂时返回 exists=false，
         * 后续最小紧急静态页面会接管该异常情况。
         */
        Path activeThemeTemplateFile =
            resolveTemplateFile(
                activeTheme,
                relativeTemplatePath
            );

        return new ResolvedTemplate(
            templateType,
            activeTheme.name(),
            activeTheme.engine(),
            relativeTemplatePath,
            activeThemeTemplateFile.toString(),
            false,
            buildMissingTemplateMessage(
                searchChain
            )
        );
    }

    /**
     * 根据模板类型唯一 key 解析模板文件。
     *
     * @param templateTypeKey 模板类型唯一 key
     * @return 对应模板类型的解析结果
     * @throws IllegalArgumentException 当 key 为空或不存在时抛出
     */
    public ResolvedTemplate resolve(
        String templateTypeKey
    ) {
        return resolve(
            TemplateTypes.require(
                templateTypeKey
            )
        );
    }

    /**
     * 解析平台当前注册的全部内置模板类型。
     *
     * <p>
     * 每一个模板类型都会使用完整主题继承链进行查找。
     * 该方法可用于后台主题完整性诊断和主题启用前检查。
     * </p>
     *
     * @return 全部内置模板类型对应的不可修改解析结果列表
     */
    public List<ResolvedTemplate>
        resolveAllBuiltInTypes() {

        return TemplateTypes.all()
            .stream()
            .map(this::resolve)
            .toList();
    }

    /**
     * 生成本次模板查找使用的主题顺序。
     *
     * <p>
     * 正常生产环境使用完整主题继承链解析器。
     * 早期兼容构造方法没有继承解析器时，
     * 只返回当前主题。
     * </p>
     *
     * @param activeTheme 当前启用主题
     * @return 从当前主题到根主题的不可修改列表
     */
    private List<ThemeDescriptor> resolveSearchChain(
        ThemeDescriptor activeTheme
    ) {
        if (themeInheritanceResolver == null) {
            return List.of(activeTheme);
        }

        return themeInheritanceResolver.resolveChain(
            activeTheme
        );
    }

    /**
     * 根据当前解析器实际装配的回退层级，
     * 生成准确且向后兼容的模板缺失说明。
     *
     * <p>
     * 不同构造方法对应不同历史阶段：
     * </p>
     *
     * <ul>
     *     <li>单参数：只检查当前主题；</li>
     *     <li>双参数：检查当前主题和全部父主题；</li>
     *     <li>三参数：继续检查外置 default；</li>
     *     <li>四参数：继续检查核心 fallback。</li>
     * </ul>
     *
     * @param searchChain 本次实际使用的主题查找链
     * @return 与实际回退层级一致的诊断说明
     */
    private String buildMissingTemplateMessage(
        List<ThemeDescriptor> searchChain
    ) {
        String formattedSearchChain =
            formatSearchChain(
                searchChain
            );

        /*
         * 最早期单参数兼容构造方法没有主题继承解析器。
         */
        if (themeInheritanceResolver == null) {
            return "当前主题未找到模板。查找链："
                + formattedSearchChain
                + "。后续需要进入 default/fallback。";
        }

        /*
         * 双参数兼容构造方法只检查活动主题和父主题链。
         */
        if (defaultThemeResolver == null) {
            return "当前主题及全部父主题均未找到模板。查找链："
                + formattedSearchChain
                + "。后续需要进入 default/fallback。";
        }

        /*
         * 三参数兼容构造方法已经检查外置 default，
         * 但还没有接入核心 fallback。
         */
        if (coreFallbackTemplateResolver == null) {
            return "当前主题继承链和外置 default 均未找到模板。"
                + "主题查找链："
                + formattedSearchChain
                + "。后续需要进入核心 fallback。";
        }

        /*
         * 四参数正式生产构造方法已经执行完整模板查找链。
         *
         * 正常发布包中的核心模板都存在，
         * 只有资源损坏等异常情况才会到达这里。
         */
        return "当前主题继承链和外置 default 均未找到模板，"
            + "核心 fallback 也未提供可用模板。主题查找链："
            + formattedSearchChain
            + "。后续需要进入最小紧急静态页面。";
    }

    /**
     * 尝试解析 Aquafish 核心内置只读 fallback。
     *
     * <p>
     * 该方法只会在以下层级都没有目标模板时调用：
     * </p>
     *
     * <pre>
     * 当前活动主题
     * -> 父主题继承链
     * -> 外置官方 default
     * </pre>
     *
     * <p>
     * 核心 fallback 模板位于 template 模块 classpath，
     * 不属于任何可安装主题。
     * </p>
     *
     * <p>
     * 返回结果固定记录：
     * </p>
     *
     * <pre>
     * themeName = aquafish-core-fallback
     * engineId = thymeleaf
     * path = classpath:/aquafish/core-fallback/templates/...
     * </pre>
     *
     * @param templateType 当前需要解析的模板类型
     * @return 找到核心资源时返回解析结果；
     *         核心资源异常缺失时返回 Optional.empty()
     */
    private Optional<ResolvedTemplate>
        resolveCoreFallbackTemplate(
            TemplateType templateType
        ) {

        /*
         * 兼容早期手动构造和自动化测试代码。
         *
         * 旧构造方法没有注入核心解析器时，
         * 保持原来只到外置 default 的行为。
         */
        if (coreFallbackTemplateResolver == null) {
            return Optional.empty();
        }

        return coreFallbackTemplateResolver.resolve(
            templateType
        );
    }

    /**
     * 尝试从外置官方 default 主题中解析目标模板。
     *
     * <p>
     * 该方法只会在当前活动主题和全部父主题
     * 都缺少目标模板后调用。
     * </p>
     *
     * <p>处理流程：</p>
     *
     * <ol>
     *     <li>确认当前实例已经配置 DefaultThemeResolver；</li>
     *     <li>查找已安装的外置 default 主题；</li>
     *     <li>避免同一个 default 主题被重复查找；</li>
     *     <li>计算 default 主题中的模板文件路径；</li>
     *     <li>检查模板是否为真实普通文件；</li>
     *     <li>返回记录 default 真实引擎的解析结果。</li>
     * </ol>
     *
     * <p>
     * 外置 default 被视为独立完整主题，
     * 不会与当前主题合并布局、设置或模板片段。
     * </p>
     *
     * <p>
     * 如果当前主题是 Pebble，而 default 是 Thymeleaf，
     * 返回结果会记录 engineId=thymeleaf，
     * 统一模板调度器将自动选择 Thymeleaf。
     * </p>
     *
     * @param templateType 当前需要解析的模板类型
     * @param relativeTemplatePath 已标准化的模板相对路径
     * @param activeSearchChain 当前主题及父主题查找链
     * @return default 提供模板时返回解析结果；
     *         default 缺失或模板不存在时返回 Optional.empty()
     */
    private Optional<ResolvedTemplate>
        resolveExternalDefaultTemplate(
            TemplateType templateType,
            String relativeTemplatePath,
            List<ThemeDescriptor> activeSearchChain
        ) {

        /*
         * 兼容旧测试构造方法。
         *
         * 旧代码没有注入 DefaultThemeResolver 时，
         * 保持原有行为，不进入 default 回退。
         */
        if (defaultThemeResolver == null) {
            return Optional.empty();
        }

        Optional<ThemeDescriptor> defaultThemeResult =
            defaultThemeResolver.defaultTheme();

        if (defaultThemeResult.isEmpty()) {
            /*
             * 外置 default 没有安装时，
             * 上层仍可继续进入核心内置 fallback。
             */
            return Optional.empty();
        }

        ThemeDescriptor defaultTheme =
            defaultThemeResult.orElseThrow();

        /*
         * 如果 default 已经出现在当前主题继承链中，
         * 说明它已经被正常查找过。
         *
         * 此时不应重复读取同一个主题目录。
         */
        boolean alreadySearched =
            activeSearchChain
                .stream()
                .anyMatch(
                    theme -> theme.name().equals(
                        defaultTheme.name()
                    )
                );

        if (alreadySearched) {
            return Optional.empty();
        }

        Path defaultTemplateFile =
            resolveTemplateFile(
                defaultTheme,
                relativeTemplatePath
            );

        if (
            !Files.isRegularFile(
                defaultTemplateFile
            )
        ) {
            return Optional.empty();
        }

        /*
         * 记录真正提供模板的 default 主题名称和引擎。
         *
         * 不能继续填写活动主题的 engine，
         * 否则调度器可能使用错误模板引擎。
         */
        return Optional.of(
            new ResolvedTemplate(
                templateType,
                defaultTheme.name(),
                defaultTheme.engine(),
                relativeTemplatePath,
                defaultTemplateFile.toString(),
                true,
                "当前主题及全部父主题均缺少模板，"
                    + "已回退到外置官方 default 主题。"
                    + "原主题查找链："
                    + formatSearchChain(
                        activeSearchChain
                    )
                    + " -> "
                    + defaultTheme.name()
            )
        );
    }

    /**
     * 计算指定主题中的最终模板文件路径。
     *
     * <p>
     * 每个继承链主题都必须独立执行路径规范化和目录边界检查。
     * 不能只校验子主题路径后直接信任父主题目录。
     * </p>
     *
     * @param theme 当前准备查找模板的主题
     * @param relativeTemplatePath 已标准化的模板相对路径
     * @return 当前主题中的规范化模板绝对路径
     */
    private Path resolveTemplateFile(
        ThemeDescriptor theme,
        String relativeTemplatePath
    ) {
        Path templatesDir = Path.of(
                theme.templatesDir()
            )
            .toAbsolutePath()
            .normalize();

        Path templateFile = templatesDir
            .resolve(relativeTemplatePath)
            .normalize();

        validateTemplateInsideTemplatesDir(
            templatesDir,
            templateFile
        );

        return templateFile;
    }

    /**
     * 标准化并验证模板相对路径。
     *
     * <p>当前安全规则：</p>
     *
     * <ol>
     *     <li>路径不允许为空；</li>
     *     <li>反斜杠统一转换为正斜杠；</li>
     *     <li>不允许绝对路径；</li>
     *     <li>不允许上级目录跳转；</li>
     *     <li>模板文件必须以 .html 结尾。</li>
     * </ol>
     *
     * @param value 原始模板相对路径
     * @return 标准化后的安全模板相对路径
     */
    private String normalizeRelativeTemplatePath(
        String value
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                "模板相对路径不能为空。"
            );
        }

        String templatePath = value
            .trim()
            .replace("\\", "/");

        if (templatePath.startsWith("/")) {
            throw new IllegalArgumentException(
                "模板路径不能是绝对路径："
                    + value
            );
        }

        if (
            templatePath.equals("..")
                || templatePath.startsWith("../")
                || templatePath.contains("/../")
                || templatePath.endsWith("/..")
        ) {
            throw new IllegalArgumentException(
                "模板路径不能包含上级目录跳转："
                    + value
            );
        }

        if (!templatePath.endsWith(".html")) {
            throw new IllegalArgumentException(
                "模板路径必须以 .html 结尾："
                    + value
            );
        }

        return templatePath;
    }

    /**
     * 验证最终模板文件仍位于当前主题 templates 目录内部。
     *
     * <p>
     * 该校验会对继承链中的每一个主题独立执行，
     * 防止第三方父主题通过特殊路径读取服务器其他文件。
     * </p>
     *
     * @param templatesDir 当前主题 templates 根目录
     * @param templateFile 当前准备读取的模板文件
     */
    private void validateTemplateInsideTemplatesDir(
        Path templatesDir,
        Path templateFile
    ) {
        if (!templateFile.startsWith(templatesDir)) {
            throw new IllegalArgumentException(
                "模板路径非法，不能逃逸当前主题 templates 目录："
                    + templateFile
            );
        }
    }

    /**
     * 把本次模板查找继承链格式化为可读文本。
     *
     * <p>示例：</p>
     *
     * <pre>
     * child-theme -> parent-theme -> root-theme
     * </pre>
     *
     * @param searchChain 当前模板查找使用的主题继承链
     * @return 用于日志和后台诊断的主题路径
     */
    private String formatSearchChain(
        List<ThemeDescriptor> searchChain
    ) {
        return searchChain
            .stream()
            .map(ThemeDescriptor::name)
            .collect(
                Collectors.joining(" -> ")
            );
    }
}
