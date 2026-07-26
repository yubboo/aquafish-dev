package com.aquafish.theme.core;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Aquafish 主题描述对象。
 *
 * <p>
 * 本记录对象表示一个已经安装到 Aquafish 运行主题目录中的主题。
 * ThemeScanner 读取主题目录中的 theme.yaml 后，
 * 会把主题清单中的基础信息和实际目录信息封装到本对象中。
 * </p>
 *
 * <p>典型主题目录结构：</p>
 *
 * <pre>
 * workdir/themes/default
 * ├─ theme.yaml
 * ├─ settings.yaml
 * ├─ templates
 * └─ assets
 * </pre>
 *
 * <p>
 * 业务模块不应该自行重复读取 theme.yaml，
 * 而应该统一使用 ThemeDescriptor。
 * 这样后台主题列表、主题启用、模板解析、
 * 主题完整性检查和应用中心兼容检查
 * 可以共享同一套主题描述模型。
 * </p>
 *
 * <p>
 * 本对象会在创建阶段完成基础标准化和校验，
 * 例如主题名称格式、模板引擎标识和空字段默认值。
 * 无效主题应尽早在扫描或安装阶段暴露，
 * 而不是等到访客打开页面时才出现难以理解的渲染错误。
 * </p>
 *
 * @param name 主题唯一标识
 * @param title 主题显示标题
 * @param version 主题版本号
 * @param engine 主题使用的服务端模板引擎标识
 * @param authorName 主题作者名称
 * @param parent 父主题唯一标识，没有父主题时允许为空
 * @param description 主题说明
 * @param themeDir 主题根目录绝对路径
 * @param themeYamlFile theme.yaml 文件绝对路径
 * @param settingsYamlFile settings.yaml 文件绝对路径
 * @param templatesDir templates 目录绝对路径
 * @param assetsDir assets 目录绝对路径
 * @param settingsYamlExists settings.yaml 是否存在
 * @param templatesDirExists templates 目录是否存在
 * @param assetsDirExists assets 目录是否存在
 */
public record ThemeDescriptor(

    /**
     * 主题唯一标识。
     *
     * <p>示例：</p>
     *
     * <pre>
     * default
     * art-pro
     * discuz-classic
     * </pre>
     *
     * <p>
     * 当前优先从 theme.yaml 的 id 字段读取。
     * 如果 id 为空，则尝试读取 name；
     * 如果 name 仍然为空，则由主题扫描器使用目录名称。
     * </p>
     */
    String name,

    /**
     * 主题显示标题。
     *
     * <p>
     * 该字段用于后台主题列表、主题详情和应用中心展示，
     * 不作为主题目录名称或程序内部唯一标识。
     * </p>
     */
    String title,

    /**
     * 主题版本号。
     *
     * <p>示例：</p>
     *
     * <pre>
     * 0.1.0
     * 1.0.0
     * 2.3.1
     * </pre>
     *
     * <p>
     * 当前只进行空值默认处理，
     * 后续主题安装器会增加正式的语义化版本校验。
     * </p>
     */
    String version,

    /**
     * 当前主题使用的服务端模板引擎标识。
     *
     * <p>Aquafish 当前正式支持：</p>
     *
     * <pre>
     * thymeleaf
     * pebble
     * </pre>
     *
     * <p>
     * 该值来自 theme.yaml：
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
     * 为兼容早期没有声明 engine 的旧主题，
     * 字段为空时默认使用 thymeleaf。
     * </p>
     *
     * <p>
     * 模板引擎标识只说明模板语法类型，
     * 不代表管理员已经允许外部主题使用该引擎。
     * 后续后台中的引擎启用开关会由独立策略服务负责。
     * </p>
     */
    String engine,

    /**
     * 主题作者名称。
     *
     * <p>
     * 该字段主要用于后台展示和应用中心作者信息，
     * 不作为主题授权身份的唯一依据。
     * 商业主题授权后续由独立许可证模块负责。
     * </p>
     */
    String authorName,

    /**
     * 父主题唯一标识。
     *
     * <p>
     * 普通独立主题可以为空；
     * 子主题可以填写另一个已安装主题的名称。
     * </p>
     *
     * <p>
     * 后续启用父子主题继承时，
     * 子主题和父主题必须使用同一种模板引擎。
     * 该规则会在独立父子主题校验步骤中实现，
     * 本步骤暂时只保存父主题名称。
     * </p>
     */
    String parent,

    /**
     * 主题说明。
     *
     * <p>
     * 用于后台主题详情和应用中心展示，
     * 不参与模板渲染和主题唯一性判断。
     * </p>
     */
    String description,

    /**
     * 主题根目录的规范化绝对路径。
     *
     * <p>Windows 环境示例：</p>
     *
     * <pre>
     * %USERPROFILE%\\.aquafish\\dev\\themes\\default
     * </pre>
     */
    String themeDir,

    /**
     * 当前主题 theme.yaml 文件的规范化绝对路径。
     */
    String themeYamlFile,

    /**
     * 当前主题 settings.yaml 文件的规范化绝对路径。
     *
     * <p>
     * 文件允许不存在，
     * 是否存在由 settingsYamlExists 字段表示。
     * </p>
     */
    String settingsYamlFile,

    /**
     * 当前主题 templates 目录的规范化绝对路径。
     *
     * <p>
     * Thymeleaf 和 Pebble 都使用该目录，
     * 但由不同 ThemeEngine 实现执行模板渲染。
     * </p>
     */
    String templatesDir,

    /**
     * 当前主题 assets 目录的规范化绝对路径。
     *
     * <p>
     * 该目录用于存放主题编译后的 CSS、JavaScript、
     * 图片、字体和其他公开静态资源。
     * </p>
     */
    String assetsDir,

    /**
     * settings.yaml 文件是否真实存在。
     */
    boolean settingsYamlExists,

    /**
     * templates 目录是否真实存在。
     */
    boolean templatesDirExists,

    /**
     * assets 目录是否真实存在。
     */
    boolean assetsDirExists
) {

    /**
     * Aquafish 当前正式支持的主题模板引擎标识。
     *
     * <p>
     * 使用不可修改的 Set，
     * 防止运行期间被其他代码随意增加或删除。
     * </p>
     *
     * <p>
     * 新模板引擎不能只在这里增加名称。
     * 还必须实现 ThemeEngine、注册到 Spring，
     * 增加运行测试并完善后台引擎策略。
     * </p>
     */
    private static final Set<String> SUPPORTED_ENGINES =
        Set.of(
            "thymeleaf",
            "pebble"
        );

    /**
     * 主题名称校验规则。
     *
     * <p>当前规则：</p>
     *
     * <ol>
     *     <li>必须以小写英文字母开头；</li>
     *     <li>后续允许小写字母、数字和中横线；</li>
     *     <li>总长度不能超过 64 个字符；</li>
     *     <li>不允许下划线、点号、空格和路径分隔符。</li>
     * </ol>
     *
     * <p>合法示例：</p>
     *
     * <pre>
     * default
     * art-pro
     * discuz-classic
     * </pre>
     *
     * <p>非法示例：</p>
     *
     * <pre>
     * Default
     * art_pro
     * 1theme
     * theme.name
     * </pre>
     */
    private static final Pattern THEME_NAME_PATTERN =
        Pattern.compile(
            "^[a-z][a-z0-9-]{0,63}$"
        );

    /**
     * ThemeDescriptor 的紧凑构造方法。
     *
     * <p>
     * Java record 在创建实例时会自动执行本构造方法。
     * 这里负责完成主题基础字段的标准化和关键校验，
     * 保证进入后台、模板解析器和主题管理服务的数据
     * 不包含不稳定的 null、大小写差异或非法引擎标识。
     * </p>
     *
     * <p>当前处理流程：</p>
     *
     * <ol>
     *     <li>验证主题名称不能为空；</li>
     *     <li>将主题名称转换为小写并验证格式；</li>
     *     <li>为空的标题、版本和说明设置安全默认值；</li>
     *     <li>将模板引擎标识转换为小写；</li>
     *     <li>验证模板引擎是否为 thymeleaf 或 pebble；</li>
     *     <li>把允许为空的父主题名称标准化为 null；</li>
     *     <li>把路径字段标准化为非 null 字符串。</li>
     * </ol>
     *
     * @throws IllegalArgumentException 当主题名称为空、
     *                                  主题名称格式非法，
     *                                  或模板引擎不受支持时抛出
     */
    public ThemeDescriptor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                "主题名称不能为空。"
            );
        }

        /*
         * 主题名称统一使用小写，
         * 避免 Windows 与 Linux 文件系统大小写行为不同
         * 导致同一个主题出现多个名称表示。
         */
        name = name
            .trim()
            .toLowerCase(Locale.ROOT);

        if (
            !THEME_NAME_PATTERN
                .matcher(name)
                .matches()
        ) {
            throw new IllegalArgumentException(
                "非法主题名称：" + name
            );
        }

        title = normalizeText(
            title,
            name
        );

        version = normalizeText(
            version,
            "0.0.0"
        );

        /*
         * 早期主题可能没有 engine 字段，
         * 此时继续默认使用 Thymeleaf，
         * 保证旧主题升级后不会直接失效。
         */
        engine = normalizeText(
                engine,
                "thymeleaf"
            )
            .toLowerCase(Locale.ROOT);

        /*
         * 主题扫描阶段就拒绝未知模板引擎。
         *
         * 不允许把未知引擎继续传到页面渲染阶段，
         * 否则访客访问页面时才会发现主题无法运行。
         */
        if (!SUPPORTED_ENGINES.contains(engine)) {
            throw new IllegalArgumentException(
                "主题声明了不受支持的模板引擎："
                    + engine
                    + "。当前支持：thymeleaf、pebble。"
            );
        }

        authorName = normalizeText(
            authorName,
            ""
        );

        parent = normalizeNullableText(
            parent
        );

        description = normalizeText(
            description,
            ""
        );

        themeDir = normalizeText(
            themeDir,
            ""
        );

        themeYamlFile = normalizeText(
            themeYamlFile,
            ""
        );

        settingsYamlFile = normalizeText(
            settingsYamlFile,
            ""
        );

        templatesDir = normalizeText(
            templatesDir,
            ""
        );

        assetsDir = normalizeText(
            assetsDir,
            ""
        );
    }

    /**
     * 判断当前主题是否声明了父主题。
     *
     * @return parent 存在有效主题名称时返回 true
     */
    public boolean hasParent() {
        return parent != null
            && !parent.isBlank();
    }

    /**
     * 判断当前主题是否使用 Thymeleaf。
     *
     * @return engine 为 thymeleaf 时返回 true
     */
    public boolean isThymeleaf() {
        return usesEngine("thymeleaf");
    }

    /**
     * 判断当前主题是否使用 Pebble。
     *
     * @return engine 为 pebble 时返回 true
     */
    public boolean isPebble() {
        return usesEngine("pebble");
    }

    /**
     * 判断当前主题是否使用指定模板引擎。
     *
     * <p>
     * 调用方传入的值会去除首尾空格并转换为小写。
     * 因此 THYMELEAF、Thymeleaf 和 thymeleaf
     * 会被视为同一个引擎标识。
     * </p>
     *
     * @param engineId 需要比较的模板引擎标识
     * @return 与当前主题引擎一致时返回 true
     */
    public boolean usesEngine(String engineId) {
        if (
            engineId == null
                || engineId.isBlank()
        ) {
            return false;
        }

        return engine.equals(
            engineId
                .trim()
                .toLowerCase(Locale.ROOT)
        );
    }

    /**
     * 判断某个模板引擎标识是否受 Aquafish 支持。
     *
     * <p>
     * 该方法可供后续主题安装器、应用中心兼容检查
     * 和后台表单校验复用。
     * </p>
     *
     * @param engineId 待检查的模板引擎标识
     * @return thymeleaf 或 pebble 返回 true，否则返回 false
     */
    public static boolean supportsEngine(
        String engineId
    ) {
        if (
            engineId == null
                || engineId.isBlank()
        ) {
            return false;
        }

        String normalizedEngineId =
            engineId
                .trim()
                .toLowerCase(Locale.ROOT);

        return SUPPORTED_ENGINES.contains(
            normalizedEngineId
        );
    }

    /**
     * 标准化普通文本字段。
     *
     * <p>
     * 当原始值为 null 或空白字符串时使用默认值，
     * 否则去除首尾空格后返回。
     * </p>
     *
     * @param value 原始字段值
     * @param defaultValue 字段为空时使用的默认值
     * @return 非 null 的标准化字符串
     */
    private static String normalizeText(
        String value,
        String defaultValue
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            return defaultValue;
        }

        return value.trim();
    }

    /**
     * 标准化允许为空的文本字段。
     *
     * <p>
     * 当前主要用于父主题名称。
     * 空字符串统一转换为 null，
     * 有效字符串则去除首尾空格。
     * </p>
     *
     * @param value 原始字段值
     * @return 空值返回 null，否则返回去除首尾空格后的文本
     */
    private static String normalizeNullableText(
        String value
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            return null;
        }

        return value.trim();
    }
}
