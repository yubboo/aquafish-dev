package com.aquafish.theme.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ThemeInheritanceResolver 自动化测试。
 *
 * <p>
 * 本测试会在 JUnit 临时工作目录中创建真实主题目录和
 * theme.yaml 文件，然后通过生产代码中的：
 * </p>
 *
 * <pre>
 * ThemeScanner
 * -> ThemeParentResolver
 * -> ThemeInheritanceResolver
 * </pre>
 *
 * <p>
 * 执行完整主题继承链解析。
 * </p>
 *
 * <p>主要验证以下规则：</p>
 *
 * <ol>
 *     <li>独立主题的继承链只包含自身；</li>
 *     <li>多层继承按照子主题到根主题的顺序返回；</li>
 *     <li>resolveDepth 返回正确继承层数；</li>
 *     <li>resolveRootTheme 返回最顶层根主题；</li>
 *     <li>直接继承自己会被识别为循环继承；</li>
 *     <li>多个主题组成的间接循环会被识别；</li>
 *     <li>超过 32 层的异常继承结构会被拒绝；</li>
 *     <li>返回的继承链不能被外部代码修改；</li>
 *     <li>null 起始主题会被明确拒绝。</li>
 * </ol>
 *
 * <p>
 * 所有测试主题均位于 JUnit 临时目录。
 * 不会读取、修改或删除用户真实的 workdir/themes。
 * </p>
 */
class ThemeInheritanceResolverTest {

    /**
     * JUnit 为每个测试方法创建的独立临时工作目录。
     *
     * <p>
     * 当前目录模拟 Aquafish 的 workdir，
     * 测试完成后由 JUnit 自动清理。
     * </p>
     */
    @TempDir
    Path temporaryWorkDir;

    /**
     * 验证独立主题的继承链只包含主题自身。
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldReturnOnlyCurrentThemeForIndependentTheme()
        throws Exception {

        createTheme(
            "independent-theme",
            "independent-theme",
            "thymeleaf",
            null
        );

        ThemeDescriptor activeTheme =
            findInstalledTheme(
                "independent-theme"
            );

        ThemeInheritanceResolver resolver =
            createInheritanceResolver();

        List<ThemeDescriptor> chain =
            resolver.resolveChain(
                activeTheme
            );

        assertEquals(
            1,
            chain.size()
        );

        assertEquals(
            "independent-theme",
            chain.get(0).name()
        );

        assertEquals(
            1,
            resolver.resolveDepth(
                activeTheme
            )
        );

        assertEquals(
            "independent-theme",
            resolver.resolveRootTheme(
                activeTheme
            ).name()
        );
    }

    /**
     * 验证三级主题继承链的顺序。
     *
     * <p>主题结构：</p>
     *
     * <pre>
     * child-theme
     * -> parent-theme
     * -> root-theme
     * </pre>
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldResolveMultiLevelInheritanceInCorrectOrder()
        throws Exception {

        createTheme(
            "root-theme",
            "root-theme",
            "pebble",
            null
        );

        createTheme(
            "parent-theme",
            "parent-theme",
            "pebble",
            "root-theme"
        );

        createTheme(
            "child-theme",
            "child-theme",
            "pebble",
            "parent-theme"
        );

        ThemeDescriptor childTheme =
            findInstalledTheme(
                "child-theme"
            );

        ThemeInheritanceResolver resolver =
            createInheritanceResolver();

        List<ThemeDescriptor> chain =
            resolver.resolveChain(
                childTheme
            );

        assertEquals(
            3,
            chain.size()
        );

        assertEquals(
            "child-theme",
            chain.get(0).name()
        );

        assertEquals(
            "parent-theme",
            chain.get(1).name()
        );

        assertEquals(
            "root-theme",
            chain.get(2).name()
        );

        /*
         * 三个主题全部使用 Pebble。
         *
         * ThemeParentResolver 会在每一级继承过程中
         * 检查父子主题引擎一致性。
         */
        assertTrue(
            chain.stream().allMatch(
                ThemeDescriptor::isPebble
            )
        );

        assertEquals(
            3,
            resolver.resolveDepth(
                childTheme
            )
        );

        ThemeDescriptor rootTheme =
            resolver.resolveRootTheme(
                childTheme
            );

        assertEquals(
            "root-theme",
            rootTheme.name()
        );

        /*
         * 根主题对象应直接来自继承链最后一项，
         * 不应该额外创建新的 ThemeDescriptor。
         */
        assertEquals(
            chain.get(2).name(),
            rootTheme.name()
        );
    }

    /**
     * 验证主题不能把自己声明为父主题。
     *
     * <p>非法结构：</p>
     *
     * <pre>
     * self-cycle -> self-cycle
     * </pre>
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldRejectDirectSelfInheritance()
        throws Exception {

        createTheme(
            "self-cycle",
            "self-cycle",
            "thymeleaf",
            "self-cycle"
        );

        ThemeDescriptor theme =
            findInstalledTheme(
                "self-cycle"
            );

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () -> createInheritanceResolver()
                    .resolveChain(theme)
            );

        assertTrue(
            error.getMessage().contains(
                "循环继承"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "self-cycle -> self-cycle"
            )
        );
    }

    /**
     * 验证三个主题组成的间接循环会被识别。
     *
     * <p>非法结构：</p>
     *
     * <pre>
     * theme-a
     * -> theme-b
     * -> theme-c
     * -> theme-a
     * </pre>
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldRejectIndirectCircularInheritance()
        throws Exception {

        createTheme(
            "theme-a",
            "theme-a",
            "pebble",
            "theme-b"
        );

        createTheme(
            "theme-b",
            "theme-b",
            "pebble",
            "theme-c"
        );

        createTheme(
            "theme-c",
            "theme-c",
            "pebble",
            "theme-a"
        );

        ThemeDescriptor themeA =
            findInstalledTheme("theme-a");

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () -> createInheritanceResolver()
                    .resolveChain(themeA)
            );

        assertTrue(
            error.getMessage().contains(
                "循环继承"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "theme-a -> theme-b -> theme-c -> theme-a"
            )
        );
    }

    /**
     * 验证超过 32 层的异常主题继承会被拒绝。
     *
     * <p>
     * 本测试创建 33 个主题：
     * theme-01 继承 theme-02，
     * 一直延续到 theme-33。
     * </p>
     *
     * <p>
     * 第 33 个主题加入继承链之前，
     * 解析器应触发最大层级保护。
     * </p>
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldRejectInheritanceDeeperThanMaximumLimit()
        throws Exception {

        int totalThemes = 33;

        for (
            int index = 1;
            index <= totalThemes;
            index++
        ) {
            String currentThemeName =
                formatThemeName(index);

            String parentThemeName =
                index < totalThemes
                    ? formatThemeName(index + 1)
                    : null;

            createTheme(
                currentThemeName,
                currentThemeName,
                "thymeleaf",
                parentThemeName
            );
        }

        ThemeDescriptor firstTheme =
            findInstalledTheme(
                formatThemeName(1)
            );

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                () -> createInheritanceResolver()
                    .resolveChain(firstTheme)
            );

        assertTrue(
            error.getMessage().contains(
                "超过安全上限 32 层"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "theme-01"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "theme-32"
            )
        );
    }

    /**
     * 验证继承链返回不可修改列表。
     *
     * <p>
     * 模板解析流程不能允许外部代码临时改变主题顺序，
     * 否则可能造成错误模板覆盖或绕过主题安全检查。
     * </p>
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldReturnImmutableInheritanceChain()
        throws Exception {

        createTheme(
            "root-theme",
            "root-theme",
            "thymeleaf",
            null
        );

        createTheme(
            "child-theme",
            "child-theme",
            "thymeleaf",
            "root-theme"
        );

        ThemeDescriptor childTheme =
            findInstalledTheme(
                "child-theme"
            );

        List<ThemeDescriptor> chain =
            createInheritanceResolver()
                .resolveChain(childTheme);

        assertEquals(
            2,
            chain.size()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> chain.add(childTheme)
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> chain.remove(0)
        );
    }

    /**
     * 验证解析器拒绝 null 起始主题。
     */
    @Test
    void shouldRejectNullActiveTheme() {
        ThemeInheritanceResolver resolver =
            createInheritanceResolver();

        IllegalArgumentException error =
            assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveChain(null)
            );

        assertTrue(
            error.getMessage().contains(
                "起始主题描述对象不能为空"
            )
        );
    }

    /**
     * 验证构造方法拒绝 null 父主题解析器。
     */
    @Test
    void shouldRejectNullParentResolver() {
        IllegalArgumentException error =
            assertThrows(
                IllegalArgumentException.class,
                () -> new ThemeInheritanceResolver(
                    null
                )
            );

        assertTrue(
            error.getMessage().contains(
                "父主题解析器不能为空"
            )
        );
    }

    /**
     * 创建连接到当前临时主题目录的完整继承链解析器。
     *
     * @return 使用真实生产依赖的主题继承链解析器
     */
    private ThemeInheritanceResolver
        createInheritanceResolver() {

        ThemeParentResolver parentResolver =
            new ThemeParentResolver(
                createThemeScanner()
            );

        return new ThemeInheritanceResolver(
            parentResolver
        );
    }

    /**
     * 创建连接到当前临时 workdir 的真实主题扫描器。
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
            new WorkDirResolver(
                properties
            );

        return new ThemeScanner(
            workDirResolver
        );
    }

    /**
     * 从临时主题目录中查找指定主题。
     *
     * @param themeName 主题唯一名称
     * @return 已安装主题描述对象
     * @throws IllegalStateException 当测试主题没有被扫描到时抛出
     */
    private ThemeDescriptor findInstalledTheme(
        String themeName
    ) {
        return createThemeScanner()
            .scanInstalledThemes()
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
     * 将数字转换为深层继承测试使用的主题名称。
     *
     * <p>示例：</p>
     *
     * <pre>
     * 1  -> theme-01
     * 9  -> theme-09
     * 10 -> theme-10
     * 33 -> theme-33
     * </pre>
     *
     * @param index 主题序号
     * @return 合法且稳定的主题名称
     */
    private String formatThemeName(
        int index
    ) {
        return String.format(
            "theme-%02d",
            index
        );
    }

    /**
     * 在临时 workdir/themes 中创建真实测试主题。
     *
     * <p>目录结构：</p>
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
     * @param engine 主题模板引擎
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
         * 独立主题不写入 parent 字段。
         *
         * 子主题则把父主题名称完整写入 theme.yaml。
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
                + "description: \"完整主题继承链自动化测试。\"\n";

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
            "# 测试主题配置\n",
            StandardCharsets.UTF_8
        );

        Files.writeString(
            templatesDirectory.resolve(
                "index.html"
            ),
            "<html><body>主题继承链测试</body></html>",
            StandardCharsets.UTF_8
        );
    }
}
