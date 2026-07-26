package com.aquafish.template.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.template.core.TemplateType;
import com.aquafish.theme.core.ActiveThemeResolver;
import com.aquafish.theme.core.DefaultThemeResolver;
import com.aquafish.theme.core.ThemeInheritanceResolver;
import com.aquafish.theme.core.ThemeParentResolver;
import com.aquafish.theme.core.ThemeScanner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 外置 default 主题模板回退自动化测试。
 *
 * <p>
 * 本测试会在 JUnit 临时工作目录中创建真实主题目录、
 * theme.yaml 和模板文件，然后通过完整生产调用链：
 * </p>
 *
 * <pre>
 * ThemeScanner
 * -> ActiveThemeResolver
 * -> ThemeInheritanceResolver
 * -> DefaultThemeResolver
 * -> ThemeTemplateResolver
 * </pre>
 *
 * <p>
 * 执行真实的外置 default 模板回退。
 * </p>
 *
 * <p>主要验证：</p>
 *
 * <ol>
 *     <li>
 *         当前主题继承链缺少模板时，
 *         能够使用外置 default 模板；
 *     </li>
 *     <li>
 *         Pebble 活动主题可以回退到 Thymeleaf default；
 *     </li>
 *     <li>
 *         返回结果记录 default 自己的主题名称和模板引擎；
 *     </li>
 *     <li>
 *         当前主题已有模板时不会错误进入 default；
 *     </li>
 *     <li>
 *         default 已安装但目标模板缺失时返回 exists=false；
 *     </li>
 *     <li>
 *         default 未安装时返回 exists=false；
 *     </li>
 *     <li>
 *         当前活动主题本身就是 default 时不会重复查找。
 *     </li>
 * </ol>
 *
 * <p>
 * 测试不会读取或修改用户真实的 workdir/themes。
 * 所有文件均由 JUnit 在临时目录中创建和清理。
 * </p>
 */
class DefaultTemplateFallbackTest {

    /**
     * JUnit 为每个测试方法创建的临时 Aquafish 工作目录。
     */
    @TempDir
    Path temporaryWorkDir;

    /**
     * 测试统一查找的模板类型。
     */
    private static final TemplateType TEST_TEMPLATE_TYPE =
        new TemplateType(
            "default-fallback-test",
            "content/view.html",
            "外置 default 回退测试",
            "验证主题继承链缺失后使用外置 default。"
        );

    /**
     * 验证活动主题缺少模板时，
     * 能够回退到外置 default 主题。
     *
     * @throws Exception 创建测试文件失败时抛出
     */
    @Test
    void shouldUseExternalDefaultWhenActiveThemeTemplateIsMissing()
        throws Exception {

        Path defaultTemplate = createTheme(
            "official-default-directory",
            "default",
            "thymeleaf",
            null,
            true,
            "default 提供的模板"
        );

        createTheme(
            "custom-theme",
            "custom-theme",
            "thymeleaf",
            null,
            false,
            null
        );

        ResolvedTemplate result =
            createTemplateResolver(
                "custom-theme"
            ).resolve(
                TEST_TEMPLATE_TYPE
            );

        assertTrue(
            result.exists()
        );

        /*
         * 必须记录真正提供文件的 default，
         * 不能继续记录 custom-theme。
         */
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

        assertTrue(
            result.message().contains(
                "custom-theme -> default"
            )
        );
    }

    /**
     * 验证 Pebble 活动主题可以回退到
     * 使用 Thymeleaf 的外置 default。
     *
     * <p>
     * 这不是跨引擎父主题继承，
     * 而是独立完整页面回退，因此允许切换引擎。
     * </p>
     *
     * @throws Exception 创建测试文件失败时抛出
     */
    @Test
    void shouldSwitchFromPebbleActiveThemeToThymeleafDefault()
        throws Exception {

        Path defaultTemplate = createTheme(
            "default",
            "default",
            "thymeleaf",
            null,
            true,
            "Thymeleaf default 模板"
        );

        createTheme(
            "pebble-community",
            "pebble-community",
            "pebble",
            null,
            false,
            null
        );

        ResolvedTemplate result =
            createTemplateResolver(
                "pebble-community"
            ).resolve(
                TEST_TEMPLATE_TYPE
            );

        assertTrue(
            result.exists()
        );

        assertEquals(
            "default",
            result.themeName()
        );

        /*
         * 最终模板来自 Thymeleaf default，
         * 所以 engineId 必须变为 thymeleaf。
         *
         * 统一模板调度器会据此选择 Thymeleaf，
         * 不能继续使用活动主题的 pebble。
         */
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
    }

    /**
     * 验证当前活动主题已经有模板时，
     * 不会错误使用 default 模板。
     *
     * @throws Exception 创建测试文件失败时抛出
     */
    @Test
    void shouldPreferActiveThemeTemplateOverDefault()
        throws Exception {

        Path defaultTemplate = createTheme(
            "default",
            "default",
            "thymeleaf",
            null,
            true,
            "default 模板"
        );

        Path activeTemplate = createTheme(
            "custom-theme",
            "custom-theme",
            "pebble",
            null,
            true,
            "活动 Pebble 主题模板"
        );

        ResolvedTemplate result =
            createTemplateResolver(
                "custom-theme"
            ).resolve(
                TEST_TEMPLATE_TYPE
            );

        assertTrue(
            result.exists()
        );

        assertEquals(
            "custom-theme",
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
            result.absoluteTemplatePath().equals(
                defaultTemplate
                    .toAbsolutePath()
                    .normalize()
                    .toString()
            )
        );

        assertTrue(
            result.message().contains(
                "已找到当前主题模板"
            )
        );
    }

    /**
     * 验证 default 已经安装，
     * 但目标模板也不存在时返回 exists=false。
     *
     * @throws Exception 创建测试文件失败时抛出
     */
    @Test
    void shouldReturnMissingWhenDefaultTemplateIsAlsoMissing()
        throws Exception {

        createTheme(
            "default",
            "default",
            "thymeleaf",
            null,
            false,
            null
        );

        Path activeExpectedTemplate = createTheme(
            "custom-theme",
            "custom-theme",
            "pebble",
            null,
            false,
            null
        );

        ResolvedTemplate result =
            createTemplateResolver(
                "custom-theme"
            ).resolve(
                TEST_TEMPLATE_TYPE
            );

        assertFalse(
            result.exists()
        );

        /*
         * 最终未找到模板时，
         * 结果仍以当前活动主题为基础，
         * 等待下一阶段核心 fallback 继续处理。
         */
        assertEquals(
            "custom-theme",
            result.themeName()
        );

        assertEquals(
            "pebble",
            result.engineId()
        );

        assertEquals(
            activeExpectedTemplate
                .toAbsolutePath()
                .normalize()
                .toString(),
            result.absoluteTemplatePath()
        );

        assertTrue(
            result.message().contains(
                "外置 default 均未找到模板"
            )
        );

        assertTrue(
            result.message().contains(
                "核心 fallback"
            )
        );
    }

    /**
     * 验证外置 default 未安装时，
     * 解析器能够安全返回 exists=false。
     *
     * @throws Exception 创建测试文件失败时抛出
     */
    @Test
    void shouldReturnMissingWhenDefaultThemeIsNotInstalled()
        throws Exception {

        createTheme(
            "custom-theme",
            "custom-theme",
            "thymeleaf",
            null,
            false,
            null
        );

        ResolvedTemplate result =
            createTemplateResolver(
                "custom-theme"
            ).resolve(
                TEST_TEMPLATE_TYPE
            );

        assertFalse(
            result.exists()
        );

        assertEquals(
            "custom-theme",
            result.themeName()
        );

        assertEquals(
            "thymeleaf",
            result.engineId()
        );

        assertTrue(
            result.message().contains(
                "核心 fallback"
            )
        );
    }

    /**
     * 验证当前活动主题就是 default 时，
     * 不会在继承链查找后再次重复查找 default。
     *
     * @throws Exception 创建测试文件失败时抛出
     */
    @Test
    void shouldNotSearchDefaultTwiceWhenDefaultIsActiveTheme()
        throws Exception {

        Path expectedTemplate = createTheme(
            "default",
            "default",
            "thymeleaf",
            null,
            false,
            null
        );

        ResolvedTemplate result =
            createTemplateResolver(
                "default"
            ).resolve(
                TEST_TEMPLATE_TYPE
            );

        assertFalse(
            result.exists()
        );

        assertEquals(
            "default",
            result.themeName()
        );

        assertEquals(
            expectedTemplate
                .toAbsolutePath()
                .normalize()
                .toString(),
            result.absoluteTemplatePath()
        );

        /*
         * 没有找到模板后应直接等待核心 fallback，
         * 不能生成 default -> default 的重复查找路径。
         */
        assertFalse(
            result.message().contains(
                "default -> default"
            )
        );

        assertTrue(
            result.message().contains(
                "核心 fallback"
            )
        );
    }

    /**
     * 创建完整生产模板解析调用链。
     *
     * @param activeThemeName 当前测试活动主题
     * @return 使用临时主题目录的模板解析器
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

        return new ThemeTemplateResolver(
            activeThemeResolver,
            inheritanceResolver,
            defaultThemeResolver
        );
    }

    /**
     * 在临时 workdir/themes 中创建真实测试主题。
     *
     * @param directoryName 主题目录名称
     * @param themeId theme.yaml 中的主题唯一标识
     * @param engine thymeleaf 或 pebble
     * @param parent 父主题名称；独立主题传入 null
     * @param createTemplate 是否创建 content/view.html
     * @param templateContent 模板内容
     * @return 该主题理论上的 content/view.html 路径
     * @throws Exception 创建目录或文件失败时抛出
     */
    private Path createTheme(
        String directoryName,
        String themeId,
        String engine,
        String parent,
        boolean createTemplate,
        String templateContent
    ) throws Exception {
        Path themeDirectory = temporaryWorkDir
            .resolve("themes")
            .resolve(directoryName);

        Path contentDirectory = themeDirectory
            .resolve("templates")
            .resolve("content");

        Path assetsDirectory =
            themeDirectory.resolve("assets");

        Files.createDirectories(
            contentDirectory
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
                + " default 回退测试主题\"\n"
                + "version: 1.0.0\n"
                + "engine: "
                + engine
                + "\n"
                + parentLine
                + "author:\n"
                + "  name: Aquafish Test\n"
                + "description: \"外置 default 模板回退测试。\"\n";

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

        Path templateFile =
            contentDirectory.resolve(
                "view.html"
            );

        if (createTemplate) {
            Files.writeString(
                templateFile,
                templateContent == null
                    ? "<html><body>测试模板</body></html>"
                    : templateContent,
                StandardCharsets.UTF_8
            );
        }

        return templateFile;
    }
}
