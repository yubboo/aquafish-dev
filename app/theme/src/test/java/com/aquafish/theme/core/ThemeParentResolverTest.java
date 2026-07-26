package com.aquafish.theme.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ThemeParentResolver 自动化测试。
 *
 * <p>
 * 本测试会在 JUnit 临时目录中创建真实主题目录和
 * theme.yaml 文件，然后使用生产代码中的
 * {@link ThemeScanner} 与 {@link ThemeParentResolver}
 * 执行父主题解析。
 * </p>
 *
 * <p>主要验证以下规则：</p>
 *
 * <ol>
 *     <li>
 *         Thymeleaf 子主题可以继承 Thymeleaf 父主题；
 *     </li>
 *     <li>
 *         Pebble 子主题可以继承 Pebble 父主题；
 *     </li>
 *     <li>
 *         Thymeleaf 与 Pebble 之间禁止跨引擎继承；
 *     </li>
 *     <li>
 *         子主题声明的父主题必须已经安装；
 *     </li>
 *     <li>
 *         没有声明 parent 的独立主题正常返回空结果；
 *     </li>
 *     <li>
 *         requireParent 遇到独立主题时抛出明确异常；
 *     </li>
 *     <li>
 *         父主题名称查找会忽略大小写和首尾空格。
 *     </li>
 * </ol>
 *
 * <p>
 * 所有测试主题都创建在 JUnit 临时目录中。
 * 测试不会读取、修改或删除用户真实的
 * workdir/themes 目录。
 * </p>
 */
class ThemeParentResolverTest {

    /**
     * JUnit 为每个测试方法创建的独立临时工作目录。
     *
     * <p>
     * 当前目录会模拟 Aquafish 的 workdir，
     * 测试结束后由 JUnit 自动删除。
     * </p>
     */
    @TempDir
    Path temporaryWorkDir;

    /**
     * 验证 Thymeleaf 子主题能够继承
     * 同样使用 Thymeleaf 的父主题。
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldResolveThymeleafParentForThymeleafChild()
        throws Exception {

        createTheme(
            "classic-parent",
            "classic-parent",
            "thymeleaf",
            null
        );

        /*
         * parent 故意使用大写字母和首尾空格。
         *
         * ThemeParentResolver 应将其标准化为小写，
         * 并正确找到 classic-parent。
         */
        createTheme(
            "classic-child",
            "classic-child",
            "thymeleaf",
            " CLASSIC-PARENT "
        );

        ThemeParentResolver resolver =
            createParentResolver();

        ThemeDescriptor childTheme =
            findInstalledTheme("classic-child");

        Optional<ThemeDescriptor> parentResult =
            resolver.resolveParent(childTheme);

        assertTrue(
            parentResult.isPresent()
        );

        ThemeDescriptor parentTheme =
            parentResult.orElseThrow();

        assertEquals(
            "classic-parent",
            parentTheme.name()
        );

        assertEquals(
            "thymeleaf",
            parentTheme.engine()
        );

        assertEquals(
            childTheme.engine(),
            parentTheme.engine()
        );
    }

    /**
     * 验证 Pebble 子主题能够继承
     * 同样使用 Pebble 的父主题。
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldResolvePebbleParentForPebbleChild()
        throws Exception {

        createTheme(
            "pebble-parent",
            "pebble-parent",
            "pebble",
            null
        );

        createTheme(
            "pebble-child",
            "pebble-child",
            "pebble",
            "pebble-parent"
        );

        ThemeParentResolver resolver =
            createParentResolver();

        ThemeDescriptor childTheme =
            findInstalledTheme("pebble-child");

        ThemeDescriptor parentTheme =
            resolver.requireParent(childTheme);

        assertEquals(
            "pebble-parent",
            parentTheme.name()
        );

        assertTrue(
            parentTheme.isPebble()
        );

        assertEquals(
            childTheme.engine(),
            parentTheme.engine()
        );
    }

    /**
     * 验证 Thymeleaf 子主题不能继承 Pebble 父主题。
     *
     * <p>
     * 两种模板引擎的布局、表达式和模板片段语法不同，
     * 因此跨引擎继承必须在父主题解析阶段被拒绝。
     * </p>
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldRejectThymeleafChildWithPebbleParent()
        throws Exception {

        createTheme(
            "pebble-parent",
            "pebble-parent",
            "pebble",
            null
        );

        createTheme(
            "thymeleaf-child",
            "thymeleaf-child",
            "thymeleaf",
            "pebble-parent"
        );

        ThemeParentResolver resolver =
            createParentResolver();

        ThemeDescriptor childTheme =
            findInstalledTheme("thymeleaf-child");

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () -> resolver.resolveParent(
                    childTheme
                )
            );

        assertTrue(
            error.getMessage().contains(
                "禁止跨模板引擎继承"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "thymeleaf-child"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "thymeleaf"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "pebble"
            )
        );
    }

    /**
     * 验证 Pebble 子主题不能继承 Thymeleaf 父主题。
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldRejectPebbleChildWithThymeleafParent()
        throws Exception {

        createTheme(
            "thymeleaf-parent",
            "thymeleaf-parent",
            "thymeleaf",
            null
        );

        createTheme(
            "pebble-child",
            "pebble-child",
            "pebble",
            "thymeleaf-parent"
        );

        ThemeParentResolver resolver =
            createParentResolver();

        ThemeDescriptor childTheme =
            findInstalledTheme("pebble-child");

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () -> resolver.requireParent(
                    childTheme
                )
            );

        assertTrue(
            error.getMessage().contains(
                "禁止跨模板引擎继承"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "pebble-child"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "thymeleaf-parent"
            )
        );
    }

    /**
     * 验证子主题声明的父主题必须真实安装。
     *
     * <p>
     * 不允许父主题缺失时把子主题静默当成独立主题，
     * 否则子主题可能缺少布局、页面模板和静态资源。
     * </p>
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldRejectMissingParentTheme()
        throws Exception {

        createTheme(
            "orphan-child",
            "orphan-child",
            "thymeleaf",
            "missing-parent"
        );

        ThemeParentResolver resolver =
            createParentResolver();

        ThemeDescriptor childTheme =
            findInstalledTheme("orphan-child");

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () -> resolver.resolveParent(
                    childTheme
                )
            );

        assertTrue(
            error.getMessage().contains(
                "父主题不存在"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "orphan-child"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "missing-parent"
            )
        );
    }

    /**
     * 验证没有声明 parent 的独立主题
     * 会正常返回 Optional.empty()。
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldReturnEmptyForIndependentTheme()
        throws Exception {

        createTheme(
            "independent-theme",
            "independent-theme",
            "thymeleaf",
            null
        );

        ThemeParentResolver resolver =
            createParentResolver();

        ThemeDescriptor theme =
            findInstalledTheme("independent-theme");

        Optional<ThemeDescriptor> parentResult =
            resolver.resolveParent(theme);

        assertTrue(
            parentResult.isEmpty()
        );

        assertFalse(
            theme.hasParent()
        );
    }

    /**
     * 验证 requireParent 不允许独立主题进入
     * “必须存在父主题”的调用流程。
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldRejectRequireParentForIndependentTheme()
        throws Exception {

        createTheme(
            "independent-theme",
            "independent-theme",
            "pebble",
            null
        );

        ThemeParentResolver resolver =
            createParentResolver();

        ThemeDescriptor theme =
            findInstalledTheme("independent-theme");

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () -> resolver.requireParent(theme)
            );

        assertTrue(
            error.getMessage().contains(
                "没有声明父主题"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "independent-theme"
            )
        );
    }

    /**
     * 验证父主题解析器拒绝 null 子主题。
     */
    @Test
    void shouldRejectNullChildTheme() {
        ThemeParentResolver resolver =
            createParentResolver();

        IllegalArgumentException error =
            assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveParent(null)
            );

        assertTrue(
            error.getMessage().contains(
                "子主题描述对象不能为空"
            )
        );
    }

    /**
     * 创建连接到当前临时 workdir 的 ThemeParentResolver。
     *
     * @return 使用真实 ThemeScanner 的父主题解析器
     */
    private ThemeParentResolver createParentResolver() {
        return new ThemeParentResolver(
            createThemeScanner()
        );
    }

    /**
     * 创建连接到当前临时 workdir 的 ThemeScanner。
     *
     * <p>
     * 测试使用真实生产类，
     * 不使用 Mock 或伪造主题列表。
     * </p>
     *
     * @return 临时主题目录扫描器
     */
    private ThemeScanner createThemeScanner() {
        AquafishProperties properties =
            new AquafishProperties(
                temporaryWorkDir.toString(),
                "http://127.0.0.1:8080",
                "aq_",
                "default"
            );

        WorkDirResolver workDirResolver =
            new WorkDirResolver(properties);

        return new ThemeScanner(
            workDirResolver
        );
    }

    /**
     * 从当前临时 workdir 中查找指定主题。
     *
     * @param themeName 主题唯一名称
     * @return 已扫描到的主题描述对象
     * @throws IllegalStateException 当测试主题没有被扫描到时抛出
     */
    private ThemeDescriptor findInstalledTheme(
        String themeName
    ) {
        List<ThemeDescriptor> themes =
            createThemeScanner()
                .scanInstalledThemes();

        return themes
            .stream()
            .filter(
                theme -> theme.name()
                    .equals(themeName)
            )
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException(
                    "测试主题没有被扫描到："
                        + themeName
                )
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
     * │  └─ index.html
     * └─ assets
     * </pre>
     *
     * @param directoryName 主题目录名称
     * @param themeId theme.yaml 中的主题唯一标识
     * @param engine 模板引擎标识
     * @param parent 父主题名称；独立主题传入 null
     * @throws Exception 当目录或文件创建失败时抛出
     */
    private void createTheme(
        String directoryName,
        String themeId,
        String engine,
        String parent
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

        /*
         * parent 为 null 时不写入 parent 字段，
         * 用于模拟真正的独立主题。
         */
        String parentLine =
            parent == null
                ? ""
                : "parent: \"" + parent + "\"\n";

        String themeYaml =
            "id: " + themeId + "\n"
                + "title: \""
                + themeId
                + " 测试主题\"\n"
                + "version: 1.0.0\n"
                + "engine: "
                + engine
                + "\n"
                + parentLine
                + "author:\n"
                + "  name: Aquafish Test\n"
                + "description: \"父主题解析自动化测试。\"\n";

        Files.writeString(
            themeDirectory.resolve("theme.yaml"),
            themeYaml,
            StandardCharsets.UTF_8
        );

        Files.writeString(
            themeDirectory.resolve("settings.yaml"),
            "# 测试主题配置\n",
            StandardCharsets.UTF_8
        );

        Files.writeString(
            templatesDirectory.resolve("index.html"),
            "<html><body>父主题解析测试</body></html>",
            StandardCharsets.UTF_8
        );
    }
}
