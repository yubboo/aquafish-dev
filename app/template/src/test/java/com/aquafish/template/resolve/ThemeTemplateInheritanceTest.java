package com.aquafish.template.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.template.core.TemplateType;
import com.aquafish.theme.core.ActiveThemeResolver;
import com.aquafish.theme.core.ThemeInheritanceResolver;
import com.aquafish.theme.core.ThemeParentResolver;
import com.aquafish.theme.core.ThemeScanner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ThemeTemplateResolver 父主题模板继承自动化测试。
 *
 * <p>
 * 本测试会在 JUnit 临时工作目录中创建真实的主题目录、
 * theme.yaml 和模板文件，然后通过完整生产调用链：
 * </p>
 *
 * <pre>
 * ActiveThemeResolver
 * -> ThemeInheritanceResolver
 * -> ThemeTemplateResolver
 * </pre>
 *
 * <p>
 * 执行真实模板文件查找。
 * </p>
 *
 * <p>主要验证以下规则：</p>
 *
 * <ol>
 *     <li>
 *         子主题存在模板时，优先使用子主题模板；
 *     </li>
 *     <li>
 *         子主题缺少模板时，自动继承直接父主题模板；
 *     </li>
 *     <li>
 *         子主题和直接父主题都缺少时，
 *         继续使用更上层根主题模板；
 *     </li>
 *     <li>
 *         整个主题继承链都缺少模板时，
 *         返回 exists=false；
 *     </li>
 *     <li>
 *         Thymeleaf 和 Pebble 主题都使用相同继承查找规则；
 *     </li>
 *     <li>
 *         ResolvedTemplate 会记录真正提供模板的主题名称、
 *         模板引擎和绝对文件路径；
 *     </li>
 *     <li>
 *         子主题覆盖父主题时不会错误返回父主题文件。
 *     </li>
 * </ol>
 *
 * <p>
 * 所有主题和模板都位于 JUnit 临时目录。
 * 本测试不会读取、修改或删除用户真实主题。
 * </p>
 */
class ThemeTemplateInheritanceTest {

    /**
     * JUnit 为每个测试方法创建的临时 Aquafish 工作目录。
     *
     * <p>
     * 测试结束后，目录会自动删除。
     * </p>
     */
    @TempDir
    Path temporaryWorkDir;

    /**
     * 测试使用的统一模板类型。
     *
     * <p>
     * 所有测试均查找 content/view.html，
     * 便于同时验证多级目录模板路径。
     * </p>
     */
    private static final TemplateType TEST_TEMPLATE_TYPE =
        new TemplateType(
            "template-inheritance-test",
            "content/view.html",
            "父主题模板继承测试",
            "验证子主题、父主题和根主题模板查找顺序。"
        );

    /**
     * 验证子主题和父主题都有模板时，
     * 必须优先使用子主题模板。
     *
     * @throws Exception 当测试目录或模板文件创建失败时抛出
     */
    @Test
    void shouldPreferChildTemplateOverParentTemplate()
        throws Exception {

        Path parentTemplate = createTheme(
            "parent-theme",
            "parent-theme",
            "thymeleaf",
            null,
            true,
            "父主题模板"
        );

        Path childTemplate = createTheme(
            "child-theme",
            "child-theme",
            "thymeleaf",
            "parent-theme",
            true,
            "子主题模板"
        );

        ThemeTemplateResolver resolver =
            createTemplateResolver(
                "child-theme"
            );

        ResolvedTemplate result =
            resolver.resolve(
                TEST_TEMPLATE_TYPE
            );

        assertTrue(
            result.exists()
        );

        /*
         * 最终来源必须是子主题，
         * 不能因为存在父主题就跳过子主题。
         */
        assertEquals(
            "child-theme",
            result.themeName()
        );

        assertEquals(
            "thymeleaf",
            result.engineId()
        );

        assertEquals(
            childTemplate
                .toAbsolutePath()
                .normalize()
                .toString(),
            result.absoluteTemplatePath()
        );

        assertFalse(
            result.absoluteTemplatePath().equals(
                parentTemplate
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

        assertTrue(
            result.message().contains(
                "child-theme -> parent-theme"
            )
        );
    }

    /**
     * 验证子主题缺少模板时，
     * 自动继承直接父主题模板。
     *
     * @throws Exception 当测试目录或模板文件创建失败时抛出
     */
    @Test
    void shouldUseDirectParentTemplateWhenChildTemplateIsMissing()
        throws Exception {

        Path parentTemplate = createTheme(
            "parent-theme",
            "parent-theme",
            "thymeleaf",
            null,
            true,
            "父主题提供的文章模板"
        );

        createTheme(
            "child-theme",
            "child-theme",
            "thymeleaf",
            "parent-theme",
            false,
            null
        );

        ThemeTemplateResolver resolver =
            createTemplateResolver(
                "child-theme"
            );

        ResolvedTemplate result =
            resolver.resolve(
                TEST_TEMPLATE_TYPE
            );

        assertTrue(
            result.exists()
        );

        /*
         * 结果必须记录真正提供文件的父主题，
         * 而不是仍然填写当前子主题。
         */
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

        assertEquals(
            "content/view.html",
            result.relativeTemplatePath()
        );

        assertTrue(
            result.message().contains(
                "已从父主题 parent-theme 继承模板"
            )
        );

        assertTrue(
            result.message().contains(
                "child-theme -> parent-theme"
            )
        );
    }

    /**
     * 验证子主题和直接父主题都缺少模板时，
     * 继续查找最上层根主题。
     *
     * <p>继承结构：</p>
     *
     * <pre>
     * child-theme
     * -> parent-theme
     * -> root-theme
     * </pre>
     *
     * @throws Exception 当测试目录或模板文件创建失败时抛出
     */
    @Test
    void shouldUseRootTemplateWhenChildAndParentTemplatesAreMissing()
        throws Exception {

        Path rootTemplate = createTheme(
            "root-theme",
            "root-theme",
            "thymeleaf",
            null,
            true,
            "根主题模板"
        );

        createTheme(
            "parent-theme",
            "parent-theme",
            "thymeleaf",
            "root-theme",
            false,
            null
        );

        createTheme(
            "child-theme",
            "child-theme",
            "thymeleaf",
            "parent-theme",
            false,
            null
        );

        ResolvedTemplate result =
            createTemplateResolver(
                "child-theme"
            ).resolve(
                TEST_TEMPLATE_TYPE
            );

        assertTrue(
            result.exists()
        );

        assertEquals(
            "root-theme",
            result.themeName()
        );

        assertEquals(
            "thymeleaf",
            result.engineId()
        );

        assertEquals(
            rootTemplate
                .toAbsolutePath()
                .normalize()
                .toString(),
            result.absoluteTemplatePath()
        );

        assertTrue(
            result.message().contains(
                "已从父主题 root-theme 继承模板"
            )
        );

        assertTrue(
            result.message().contains(
                "child-theme -> parent-theme -> root-theme"
            )
        );
    }

    /**
     * 验证整个主题继承链都没有模板时，
     * 返回 exists=false，而不是返回错误父主题路径。
     *
     * @throws Exception 当测试主题目录创建失败时抛出
     */
    @Test
    void shouldReturnMissingResultWhenEntireInheritanceChainIsMissing()
        throws Exception {

        createTheme(
            "root-theme",
            "root-theme",
            "thymeleaf",
            null,
            false,
            null
        );

        createTheme(
            "parent-theme",
            "parent-theme",
            "thymeleaf",
            "root-theme",
            false,
            null
        );

        Path childExpectedTemplate = createTheme(
            "child-theme",
            "child-theme",
            "thymeleaf",
            "parent-theme",
            false,
            null
        );

        ResolvedTemplate result =
            createTemplateResolver(
                "child-theme"
            ).resolve(
                TEST_TEMPLATE_TYPE
            );

        assertFalse(
            result.exists()
        );

        /*
         * 全部缺失时仍然以当前启用主题为基础返回不存在路径，
         * 等待后续 default 和核心 fallback 继续处理。
         */
        assertEquals(
            "child-theme",
            result.themeName()
        );

        assertEquals(
            "thymeleaf",
            result.engineId()
        );

        assertEquals(
            childExpectedTemplate
                .toAbsolutePath()
                .normalize()
                .toString(),
            result.absoluteTemplatePath()
        );

        assertTrue(
            result.message().contains(
                "当前主题及全部父主题均未找到模板"
            )
        );

        assertTrue(
            result.message().contains(
                "child-theme -> parent-theme -> root-theme"
            )
        );

        assertTrue(
            result.message().contains(
                "default/fallback"
            )
        );
    }

    /**
     * 验证 Pebble 父子主题同样支持模板继承。
     *
     * <p>
     * 模板解析层只负责查找文件。
     * 最终 ResolvedTemplate.engineId 为 pebble，
     * 统一调度器会把模板交给 PebbleThemeEngine。
     * </p>
     *
     * @throws Exception 当测试目录或模板文件创建失败时抛出
     */
    @Test
    void shouldInheritPebbleTemplateFromPebbleParent()
        throws Exception {

        Path parentTemplate = createTheme(
            "pebble-parent",
            "pebble-parent",
            "pebble",
            null,
            true,
            "Pebble 父主题模板"
        );

        createTheme(
            "pebble-child",
            "pebble-child",
            "pebble",
            "pebble-parent",
            false,
            null
        );

        ResolvedTemplate result =
            createTemplateResolver(
                "pebble-child"
            ).resolve(
                TEST_TEMPLATE_TYPE
            );

        assertTrue(
            result.exists()
        );

        assertEquals(
            "pebble-parent",
            result.themeName()
        );

        assertEquals(
            "pebble",
            result.engineId()
        );

        assertEquals(
            parentTemplate
                .toAbsolutePath()
                .normalize()
                .toString(),
            result.absoluteTemplatePath()
        );
    }

    /**
     * 创建完整生产模板解析调用链。
     *
     * <p>组装顺序：</p>
     *
     * <pre>
     * ThemeScanner
     * -> ActiveThemeResolver
     * -> ThemeParentResolver
     * -> ThemeInheritanceResolver
     * -> ThemeTemplateResolver
     * </pre>
     *
     * @param activeThemeName 当前测试需要启用的主题名称
     * @return 使用临时主题目录的模板解析器
     */
    private ThemeTemplateResolver createTemplateResolver(
        String activeThemeName
    ) {
        AquafishProperties properties =
            new AquafishProperties(
                temporaryWorkDir.toString(),
                "http://127.0.0.1:8080",
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

        ThemeInheritanceResolver
            inheritanceResolver =
                new ThemeInheritanceResolver(
                    parentResolver
                );

        return new ThemeTemplateResolver(
            activeThemeResolver,
            inheritanceResolver
        );
    }

    /**
     * 在临时 workdir/themes 中创建一个真实测试主题。
     *
     * <p>创建结构：</p>
     *
     * <pre>
     * themes/{directoryName}
     * ├─ theme.yaml
     * ├─ settings.yaml
     * ├─ templates
     * │  └─ content
     * │     └─ view.html（可选）
     * └─ assets
     * </pre>
     *
     * <p>
     * 即使 createTemplate 为 false，
     * 方法仍会返回该主题理论上的模板文件路径，
     * 便于测试 exists=false 时的路径结果。
     * </p>
     *
     * @param directoryName 主题目录名称
     * @param themeId theme.yaml 中的主题唯一标识
     * @param engine thymeleaf 或 pebble
     * @param parent 父主题名称；独立主题传入 null
     * @param createTemplate 是否真正创建 content/view.html
     * @param templateContent 模板文件内容
     * @return 当前主题 content/view.html 的完整路径
     * @throws Exception 当主题目录或文件创建失败时抛出
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

        Path templatesDirectory =
            themeDirectory.resolve("templates");

        Path contentDirectory =
            templatesDirectory.resolve("content");

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
                + " 模板继承测试主题\"\n"
                + "version: 1.0.0\n"
                + "engine: "
                + engine
                + "\n"
                + parentLine
                + "author:\n"
                + "  name: Aquafish Test\n"
                + "description: \"父主题模板继承自动化测试。\"\n";

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
