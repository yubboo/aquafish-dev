package com.aquafish.template.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.template.core.TemplateTypes;
import com.aquafish.theme.core.ActiveThemeResolver;
import com.aquafish.theme.core.DefaultThemeResolver;
import com.aquafish.theme.core.ThemeInheritanceResolver;
import com.aquafish.theme.core.ThemeParentResolver;
import com.aquafish.theme.core.ThemeScanner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ThemeTemplateResolver 核心 fallback 完整集成测试。
 *
 * <p>
 * 本测试验证第 34 步完成后的正式模板查找顺序：
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
 * 测试使用真实主题目录、theme.yaml、模板文件、
 * ThemeScanner 和全部生产解析器，不使用 Mock。
 * </p>
 *
 * <p>主要验证：</p>
 *
 * <ol>
 *     <li>
 *         活动主题模板优先级最高；
 *     </li>
 *     <li>
 *         活动主题缺失时优先继承父主题；
 *     </li>
 *     <li>
 *         父主题链缺失时优先使用外置 default；
 *     </li>
 *     <li>
 *         前面所有主题层都缺失时使用核心 fallback；
 *     </li>
 *     <li>
 *         Pebble 活动主题可以切换到 Thymeleaf 核心 fallback；
 *     </li>
 *     <li>
 *         16 个平台注册模板类型都能获得核心兜底。
 *     </li>
 * </ol>
 *
 * <p>
 * 所有外置主题都创建在 JUnit 临时目录，
 * 不会读取或修改用户真实主题。
 * 核心模板从 template 模块真实 classpath 中读取。
 * </p>
 */
class CoreFallbackIntegrationTest {

    /**
     * JUnit 为每个测试方法创建的临时 Aquafish 工作目录。
     */
    @TempDir
    Path temporaryWorkDir;

    /**
     * 当前平台注册的内置模板类型数量。
     *
     * <p>
     * 如果未来新增 TemplateType，
     * 需要同时新增对应的核心 fallback 模板，
     * 并明确更新该数量。
     * </p>
     */
    private static final int EXPECTED_TEMPLATE_COUNT =
        16;

    /**
     * 验证活动主题、父主题和外置 default 都缺少模板时，
     * ThemeTemplateResolver 会返回核心内置 fallback。
     *
     * @throws Exception 创建临时主题失败时抛出
     */
    @Test
    void shouldUseCoreFallbackWhenAllExternalThemeLayersAreMissing()
        throws Exception {

        /*
         * 当前活动主题真实存在，
         * 但是不创建 index.html。
         */
        createTheme(
            "custom-theme",
            "custom-theme",
            "thymeleaf",
            null,
            null,
            null
        );

        ResolvedTemplate result =
            createTemplateResolver(
                "custom-theme"
            ).resolve(
                TemplateTypes.require("index")
            );

        assertTrue(
            result.exists()
        );

        assertEquals(
            CoreFallbackTemplateResolver
                .CORE_FALLBACK_THEME_NAME,
            result.themeName()
        );

        assertEquals(
            "aquafish-core-fallback",
            result.themeName()
        );

        assertEquals(
            CoreFallbackTemplateResolver
                .CORE_FALLBACK_ENGINE_ID,
            result.engineId()
        );

        assertEquals(
            "thymeleaf",
            result.engineId()
        );

        assertEquals(
            "index.html",
            result.relativeTemplatePath()
        );

        assertEquals(
            "classpath:/aquafish/core-fallback/templates/index.html",
            result.absoluteTemplatePath()
        );

        assertTrue(
            result.message().contains(
                "核心内置只读 fallback"
            )
        );
    }

    /**
     * 验证 Pebble 活动主题缺少模板时，
     * 最终可以切换到固定使用 Thymeleaf 的核心 fallback。
     *
     * <p>
     * 这不是父主题跨引擎继承，
     * 而是独立安全回退层，因此允许切换引擎。
     * </p>
     *
     * @throws Exception 创建临时主题失败时抛出
     */
    @Test
    void shouldSwitchFromPebbleThemeToThymeleafCoreFallback()
        throws Exception {

        createTheme(
            "pebble-theme",
            "pebble-theme",
            "pebble",
            null,
            null,
            null
        );

        ResolvedTemplate result =
            createTemplateResolver(
                "pebble-theme"
            ).resolve(
                TemplateTypes.require("thread")
            );

        assertTrue(
            result.exists()
        );

        assertEquals(
            "aquafish-core-fallback",
            result.themeName()
        );

        /*
         * 当前活动主题是 Pebble，
         * 但最终模板来自核心 fallback，
         * 所以 engineId 必须变成 thymeleaf。
         */
        assertEquals(
            "thymeleaf",
            result.engineId()
        );

        assertEquals(
            "forum/viewthread.html",
            result.relativeTemplatePath()
        );

        assertEquals(
            "classpath:/aquafish/core-fallback/templates/forum/viewthread.html",
            result.absoluteTemplatePath()
        );
    }

    /**
     * 验证活动主题拥有模板时，
     * 不会继续进入父主题、default 或核心 fallback。
     *
     * @throws Exception 创建临时主题失败时抛出
     */
    @Test
    void shouldPreferActiveThemeTemplateOverEveryFallbackLayer()
        throws Exception {

        Path activeTemplate = createTheme(
            "active-theme",
            "active-theme",
            "pebble",
            null,
            "index.html",
            "活动主题首页"
        );

        /*
         * 同时创建外置 default，
         * 用于确认 default 不会覆盖活动主题。
         */
        createTheme(
            "default",
            "default",
            "thymeleaf",
            null,
            "index.html",
            "default 首页"
        );

        ResolvedTemplate result =
            createTemplateResolver(
                "active-theme"
            ).resolve(
                TemplateTypes.require("index")
            );

        assertTrue(
            result.exists()
        );

        assertEquals(
            "active-theme",
            result.themeName()
        );

        assertEquals(
            "pebble",
            result.engineId()
        );

        assertEquals(
            activeTemplate
                .toAbsolutePath()
                .normalize()
                .toString(),
            result.absoluteTemplatePath()
        );

        assertFalse(
            result.absoluteTemplatePath()
                .startsWith("classpath:/")
        );

        assertFalse(
            result.themeName().equals(
                "aquafish-core-fallback"
            )
        );
    }

    /**
     * 验证子主题缺少模板时，
     * 父主题模板优先于外置 default 和核心 fallback。
     *
     * @throws Exception 创建临时主题失败时抛出
     */
    @Test
    void shouldPreferParentTemplateOverDefaultAndCoreFallback()
        throws Exception {

        Path parentTemplate = createTheme(
            "parent-theme",
            "parent-theme",
            "thymeleaf",
            null,
            "index.html",
            "父主题首页"
        );

        createTheme(
            "child-theme",
            "child-theme",
            "thymeleaf",
            "parent-theme",
            null,
            null
        );

        createTheme(
            "default",
            "default",
            "thymeleaf",
            null,
            "index.html",
            "default 首页"
        );

        ResolvedTemplate result =
            createTemplateResolver(
                "child-theme"
            ).resolve(
                TemplateTypes.require("index")
            );

        assertTrue(
            result.exists()
        );

        assertEquals(
            "parent-theme",
            result.themeName()
        );

        assertEquals(
            "thymeleaf",
            result.engineId()
        );

        assertEquals(
            parentTemplate
                .toAbsolutePath()
                .normalize()
                .toString(),
            result.absoluteTemplatePath()
        );

        assertTrue(
            result.message().contains(
                "已从父主题 parent-theme 继承模板"
            )
        );

        assertFalse(
            result.themeName().equals(
                "default"
            )
        );

        assertFalse(
            result.themeName().equals(
                "aquafish-core-fallback"
            )
        );
    }

    /**
     * 验证活动主题及父主题链缺失时，
     * 外置 default 模板优先于核心 fallback。
     *
     * <p>
     * 当前活动主题使用 Pebble，
     * 外置 default 使用 Thymeleaf，
     * 用来同时验证 default 的独立跨引擎回退。
     * </p>
     *
     * @throws Exception 创建临时主题失败时抛出
     */
    @Test
    void shouldPreferExternalDefaultOverCoreFallback()
        throws Exception {

        createTheme(
            "pebble-theme",
            "pebble-theme",
            "pebble",
            null,
            null,
            null
        );

        Path defaultTemplate = createTheme(
            "official-default-directory",
            "default",
            "thymeleaf",
            null,
            "index.html",
            "外置 default 首页"
        );

        ResolvedTemplate result =
            createTemplateResolver(
                "pebble-theme"
            ).resolve(
                TemplateTypes.require("index")
            );

        assertTrue(
            result.exists()
        );

        assertEquals(
            "default",
            result.themeName()
        );

        assertEquals(
            "thymeleaf",
            result.engineId()
        );

        assertEquals(
            defaultTemplate
                .toAbsolutePath()
                .normalize()
                .toString(),
            result.absoluteTemplatePath()
        );

        assertTrue(
            result.message().contains(
                "外置官方 default"
            )
        );

        assertFalse(
            result.themeName().equals(
                "aquafish-core-fallback"
            )
        );
    }

    /**
     * 验证活动主题、父主题和 default 都没有任何模板时，
     * 16 个平台注册模板类型全部能回退到核心资源。
     *
     * @throws Exception 创建临时主题失败时抛出
     */
    @Test
    void shouldResolveEveryBuiltInTypeToCoreFallback()
        throws Exception {

        createTheme(
            "empty-theme",
            "empty-theme",
            "pebble",
            null,
            null,
            null
        );

        ThemeTemplateResolver resolver =
            createTemplateResolver(
                "empty-theme"
            );

        List<ResolvedTemplate> results =
            resolver.resolveAllBuiltInTypes();

        assertEquals(
            EXPECTED_TEMPLATE_COUNT,
            TemplateTypes.all().size()
        );

        assertEquals(
            EXPECTED_TEMPLATE_COUNT,
            results.size()
        );

        for (ResolvedTemplate result : results) {
            assertTrue(
                result.exists(),
                "核心模板应存在："
                    + result.relativeTemplatePath()
            );

            assertEquals(
                "aquafish-core-fallback",
                result.themeName()
            );

            assertEquals(
                "thymeleaf",
                result.engineId()
            );

            assertTrue(
                result.absoluteTemplatePath()
                    .startsWith(
                        "classpath:/aquafish/core-fallback/templates/"
                    )
            );

            assertTrue(
                result.message().contains(
                    "核心内置只读 fallback"
                )
            );
        }
    }

    /**
     * 创建完整生产模板查找调用链。
     *
     * <p>装配顺序：</p>
     *
     * <pre>
     * ThemeScanner
     * -> ActiveThemeResolver
     * -> ThemeParentResolver
     * -> ThemeInheritanceResolver
     * -> DefaultThemeResolver
     * -> CoreFallbackTemplateResolver
     * -> ThemeTemplateResolver
     * </pre>
     *
     * @param activeThemeName 当前活动主题唯一标识
     * @return 使用真实生产依赖的模板解析器
     */
    private ThemeTemplateResolver createTemplateResolver(
        String activeThemeName
    ) {
        AquafishProperties properties =
            new AquafishProperties(
                temporaryWorkDir.toString(),
                "http://127.0.0.1:8520",
                "aq_",
                activeThemeName
            );

        WorkDirResolver workDirResolver =
            new WorkDirResolver(
                properties
            );

        ThemeScanner themeScanner =
            new ThemeScanner(
                workDirResolver
            );

        ActiveThemeResolver activeThemeResolver =
            new ActiveThemeResolver(
                properties,
                themeScanner
            );

        ThemeParentResolver parentResolver =
            new ThemeParentResolver(
                themeScanner
            );

        ThemeInheritanceResolver inheritanceResolver =
            new ThemeInheritanceResolver(
                parentResolver
            );

        DefaultThemeResolver defaultThemeResolver =
            new DefaultThemeResolver(
                themeScanner
            );

        CoreFallbackTemplateResolver coreResolver =
            new CoreFallbackTemplateResolver();

        return new ThemeTemplateResolver(
            activeThemeResolver,
            inheritanceResolver,
            defaultThemeResolver,
            coreResolver
        );
    }

    /**
     * 在临时 workdir/themes 中创建真实测试主题。
     *
     * <p>
     * templateRelativePath 传入 null 时，
     * 只创建主题结构，不创建目标模板。
     * </p>
     *
     * @param directoryName 主题目录名称
     * @param themeId theme.yaml 中的主题唯一标识
     * @param engine thymeleaf 或 pebble
     * @param parent 父主题名称；独立主题传入 null
     * @param templateRelativePath 要创建的模板相对路径；
     *                             不创建模板时传入 null
     * @param templateContent 模板文件内容
     * @return 目标模板理论路径；
     *         未指定模板路径时返回 templates 目录
     * @throws Exception 创建目录或文件失败时抛出
     */
    private Path createTheme(
        String directoryName,
        String themeId,
        String engine,
        String parent,
        String templateRelativePath,
        String templateContent
    ) throws Exception {
        Path themeDirectory = temporaryWorkDir
            .resolve("themes")
            .resolve(directoryName);

        Path templatesDirectory =
            themeDirectory.resolve("templates");

        Path assetsDirectory =
            themeDirectory.resolve("assets");

        Files.createDirectories(
            templatesDirectory
        );

        Files.createDirectories(
            assetsDirectory
        );

        String parentLine =
            parent == null
                ? ""
                : "parent: \"" + parent + "\"\n";

        String themeYaml =
            "id: " + themeId + "\n"
                + "title: \""
                + themeId
                + " 核心回退集成测试主题\"\n"
                + "version: 1.0.0\n"
                + "engine: "
                + engine
                + "\n"
                + parentLine
                + "author:\n"
                + "  name: Aquafish Test\n"
                + "description: \"核心 fallback 完整优先级测试。\"\n";

        Files.writeString(
            themeDirectory.resolve(
                "theme.yaml"
            ),
            themeYaml,
            StandardCharsets.UTF_8
        );

        Files.writeString(
            themeDirectory.resolve(
                "settings.yaml"
            ),
            "# 测试主题设置\n",
            StandardCharsets.UTF_8
        );

        if (
            templateRelativePath == null
                || templateRelativePath.isBlank()
        ) {
            return templatesDirectory;
        }

        Path templateFile =
            templatesDirectory.resolve(
                templateRelativePath
                    .replace(
                        "/",
                        java.io.File.separator
                    )
            );

        Files.createDirectories(
            templateFile.getParent()
        );

        Files.writeString(
            templateFile,
            templateContent == null
                ? "<html><body>测试模板</body></html>"
                : templateContent,
            StandardCharsets.UTF_8
        );

        return templateFile;
    }
}
