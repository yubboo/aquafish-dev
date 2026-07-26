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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DefaultThemeResolver 自动化测试。
 *
 * <p>
 * 本测试会在 JUnit 临时工作目录中创建真实主题目录和
 * theme.yaml 文件，然后通过生产代码中的
 * {@link ThemeScanner} 与 {@link DefaultThemeResolver}
 * 执行外置 default 主题解析。
 * </p>
 *
 * <p>主要验证以下规则：</p>
 *
 * <ol>
 *     <li>已安装的 default 主题能够被正确找到；</li>
 *     <li>default 主题自己的模板引擎信息会被完整保留；</li>
 *     <li>default 可以使用 Thymeleaf；</li>
 *     <li>default 也可以使用 Pebble；</li>
 *     <li>只有其他普通主题时不会被误认为 default；</li>
 *     <li>default 缺失时返回 Optional.empty()；</li>
 *     <li>强制获取缺失 default 时抛出明确异常；</li>
 *     <li>isDefaultTheme 能够安全处理普通主题和 null；</li>
 *     <li>平台固定 default 主题名称保持稳定。</li>
 * </ol>
 *
 * <p>
 * 所有主题文件均创建在 JUnit 临时目录。
 * 测试不会读取、修改或删除用户真实的
 * workdir/themes 目录。
 * </p>
 */
class DefaultThemeResolverTest {

    /**
     * JUnit 为每一个测试方法创建的临时 Aquafish 工作目录。
     *
     * <p>
     * 测试完成后，JUnit 会自动删除该目录。
     * </p>
     */
    @TempDir
    Path temporaryWorkDir;

    /**
     * 验证已安装的 Thymeleaf default 主题能够被正确找到。
     *
     * <p>
     * 主题目录名称故意写成 official-default-package，
     * 用于确认解析依据是 theme.yaml 中的主题唯一标识，
     * 而不是简单依赖目录显示名称。
     * </p>
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldResolveInstalledThymeleafDefaultTheme()
        throws Exception {

        createTheme(
            "official-default-package",
            "default",
            "thymeleaf"
        );

        /*
         * 同时创建一个普通主题，
         * 确认解析器不会错误返回第一个扫描到的主题。
         */
        createTheme(
            "custom-theme",
            "custom-theme",
            "pebble"
        );

        DefaultThemeResolver resolver =
            createDefaultThemeResolver();

        Optional<ThemeDescriptor> result =
            resolver.defaultTheme();

        assertTrue(
            result.isPresent()
        );

        ThemeDescriptor defaultTheme =
            result.orElseThrow();

        assertEquals(
            "default",
            defaultTheme.name()
        );

        assertEquals(
            "thymeleaf",
            defaultTheme.engine()
        );

        assertTrue(
            defaultTheme.isThymeleaf()
        );

        assertFalse(
            defaultTheme.isPebble()
        );

        assertTrue(
            resolver.isDefaultTheme(
                defaultTheme
            )
        );

        assertTrue(
            resolver.isDefaultThemeInstalled()
        );

        assertEquals(
            "default",
            resolver.defaultThemeName()
        );

        ThemeDescriptor requiredTheme =
            resolver.requireDefaultTheme();

        assertEquals(
            "default",
            requiredTheme.name()
        );

        assertEquals(
            "thymeleaf",
            requiredTheme.engine()
        );
    }

    /**
     * 验证外置 default 主题可以使用 Pebble。
     *
     * <p>
     * default 回退不是父子主题继承，
     * 所以它可以使用与当前活动主题不同的模板引擎。
     * </p>
     *
     * <p>
     * 当最终模板来自 Pebble default 时，
     * ResolvedTemplate 将记录 engineId=pebble，
     * 统一模板调度器会调用 PebbleThemeEngine。
     * </p>
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldPreservePebbleEngineOfDefaultTheme()
        throws Exception {

        createTheme(
            "default",
            "default",
            " PEBBLE "
        );

        DefaultThemeResolver resolver =
            createDefaultThemeResolver();

        ThemeDescriptor defaultTheme =
            resolver.requireDefaultTheme();

        assertEquals(
            "default",
            defaultTheme.name()
        );

        /*
         * ThemeDescriptor 会统一去除空格并转换为小写。
         */
        assertEquals(
            "pebble",
            defaultTheme.engine()
        );

        assertTrue(
            defaultTheme.isPebble()
        );

        assertFalse(
            defaultTheme.isThymeleaf()
        );

        assertTrue(
            resolver.isDefaultTheme(
                defaultTheme
            )
        );
    }

    /**
     * 验证只有普通主题时，
     * defaultTheme 返回 Optional.empty()。
     *
     * <p>
     * 不能把当前启用主题、扫描到的第一个主题
     * 或名称相似的主题误认为官方 default。
     * </p>
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldReturnEmptyWhenDefaultThemeIsMissing()
        throws Exception {

        createTheme(
            "default-like-theme",
            "default-pro",
            "thymeleaf"
        );

        createTheme(
            "normal-theme",
            "normal-theme",
            "pebble"
        );

        DefaultThemeResolver resolver =
            createDefaultThemeResolver();

        Optional<ThemeDescriptor> result =
            resolver.defaultTheme();

        assertTrue(
            result.isEmpty()
        );

        assertFalse(
            resolver.isDefaultThemeInstalled()
        );
    }

    /**
     * 验证 requireDefaultTheme 在 default 未安装时
     * 会抛出明确异常。
     *
     * <p>
     * 正式访客回退流程可以使用 defaultTheme()
     * 在缺失时继续进入核心 fallback。
     * 后台完整性诊断则可以使用 requireDefaultTheme()
     * 强制要求外置 default 存在。
     * </p>
     */
    @Test
    void shouldRejectRequiredDefaultThemeWhenMissing() {
        DefaultThemeResolver resolver =
            createDefaultThemeResolver();

        IllegalStateException error =
            assertThrows(
                IllegalStateException.class,
                resolver::requireDefaultTheme
            );

        assertTrue(
            error.getMessage().contains(
                "外置官方 default 主题不存在"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "default"
            )
        );
    }

    /**
     * 验证 isDefaultTheme 对普通主题返回 false。
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldReturnFalseForNonDefaultTheme()
        throws Exception {

        createTheme(
            "normal-theme",
            "normal-theme",
            "thymeleaf"
        );

        ThemeDescriptor normalTheme =
            createThemeScanner()
                .scanInstalledThemes()
                .stream()
                .filter(
                    theme -> theme.name()
                        .equals("normal-theme")
                )
                .findFirst()
                .orElseThrow();

        DefaultThemeResolver resolver =
            createDefaultThemeResolver();

        assertFalse(
            resolver.isDefaultTheme(
                normalTheme
            )
        );
    }

    /**
     * 验证 isDefaultTheme 对 null 安全返回 false。
     */
    @Test
    void shouldReturnFalseForNullTheme() {
        DefaultThemeResolver resolver =
            createDefaultThemeResolver();

        assertFalse(
            resolver.isDefaultTheme(null)
        );
    }

    /**
     * 验证构造方法拒绝 null ThemeScanner。
     */
    @Test
    void shouldRejectNullThemeScanner() {
        IllegalArgumentException error =
            assertThrows(
                IllegalArgumentException.class,
                () -> new DefaultThemeResolver(
                    null
                )
            );

        assertTrue(
            error.getMessage().contains(
                "主题扫描器不能为空"
            )
        );
    }

    /**
     * 验证平台固定 default 主题名称不会变化。
     */
    @Test
    void shouldExposeStableDefaultThemeName() {
        DefaultThemeResolver resolver =
            createDefaultThemeResolver();

        assertEquals(
            "default",
            DefaultThemeResolver.DEFAULT_THEME_NAME
        );

        assertEquals(
            DefaultThemeResolver.DEFAULT_THEME_NAME,
            resolver.defaultThemeName()
        );
    }

    /**
     * 创建连接到临时 workdir 的外置 default 主题解析器。
     *
     * @return 使用真实 ThemeScanner 的 DefaultThemeResolver
     */
    private DefaultThemeResolver
        createDefaultThemeResolver() {

        return new DefaultThemeResolver(
            createThemeScanner()
        );
    }

    /**
     * 创建连接到临时 workdir 的真实主题扫描器。
     *
     * <p>
     * 当前 activeThemeName 设为 normal-theme，
     * 但 DefaultThemeResolver 不依赖当前活动主题，
     * 它只按照固定主题唯一标识 default 查找。
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
                "normal-theme"
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
     * @param engine thymeleaf 或 pebble
     * @throws Exception 当目录或文件创建失败时抛出
     */
    private void createTheme(
        String directoryName,
        String themeId,
        String engine
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

        String themeYaml =
            "id: " + themeId + "\n"
                + "title: \""
                + themeId
                + " 默认主题解析测试\"\n"
                + "version: 1.0.0\n"
                + "engine: "
                + engine
                + "\n"
                + "author:\n"
                + "  name: Aquafish Test\n"
                + "description: \"外置 default 主题解析测试。\"\n";

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

        Files.writeString(
            templatesDirectory.resolve(
                "index.html"
            ),
            "<html><body>default 主题测试</body></html>",
            StandardCharsets.UTF_8
        );
    }
}
