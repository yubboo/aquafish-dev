package com.aquafish.theme.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * ThemeScanner 双模板引擎自动化测试。
 *
 * <p>
 * 本测试会在 JUnit 临时目录中创建真实的主题目录、
 * theme.yaml、templates、assets 和 settings.yaml，
 * 然后调用生产代码中的 {@link ThemeScanner}
 * 执行完整主题扫描。
 * </p>
 *
 * <p>与直接创建 ThemeDescriptor 的测试不同，本测试会真实验证：</p>
 *
 * <ol>
 *     <li>WorkDirResolver 能够定位临时 themes 目录；</li>
 *     <li>ThemeScanner 能够发现一级主题目录；</li>
 *     <li>ThemeScanner 能够读取 UTF-8 theme.yaml；</li>
 *     <li>engine: thymeleaf 能够正确进入 ThemeDescriptor；</li>
 *     <li>engine: pebble 能够正确进入 ThemeDescriptor；</li>
 *     <li>没有 engine 的旧主题能够默认使用 Thymeleaf；</li>
 *     <li>未知引擎会在扫描阶段被 ThemeDescriptor 拒绝；</li>
 *     <li>简单二级字段 author.name 能够正确解析；</li>
 *     <li>没有 theme.yaml 的普通目录不会被识别成主题；</li>
 *     <li>扫描结果会按照主题唯一名称稳定排序。</li>
 * </ol>
 *
 * <p>
 * 测试结束后，JUnit 会自动清理临时目录，
 * 不会读取、修改或删除用户真实 workdir/themes 中的主题。
 * </p>
 */
class ThemeScannerTest {

    /**
     * JUnit 为每个测试方法创建的独立临时工作目录。
     *
     * <p>
     * 该目录在测试中模拟 Aquafish 的 workdir，
     * 其中会自动创建 themes、plugins、storage 等基础目录。
     * </p>
     */
    @TempDir
    Path temporaryWorkDir;

    /**
     * 验证扫描器能够同时读取 Thymeleaf 和 Pebble 主题。
     *
     * @throws Exception 当测试目录或主题文件创建失败时抛出
     */
    @Test
    void shouldScanThymeleafAndPebbleThemes()
        throws Exception {

        /*
         * 创建一个 Thymeleaf 主题。
         *
         * 主题目录名称故意与 theme.yaml 中的 id 不同，
         * 用于确认系统优先使用清单中的 id。
         */
        createTheme(
            "folder-thymeleaf",
            """
            id: alpha-theme
            title: "Aquafish Thymeleaf 测试主题"
            version: '1.0.0'
            engine: THYMELEAF
            author:
              name: Aquafish Team
            parent: ""
            description: "用于测试 Thymeleaf 主题扫描。"
            """,
            true,
            true,
            true
        );

        /*
         * 创建一个 Pebble 主题。
         *
         * engine 使用大写形式，
         * ThemeDescriptor 应将其标准化为 pebble。
         */
        createTheme(
            "folder-pebble",
            """
            id: pebble-theme
            title: "Aquafish Pebble 测试主题"
            version: "2.1.0"
            engine: PEBBLE
            author:
              name: Pebble Theme Author
            parent: ""
            description: "用于测试 Pebble 主题扫描。"
            """,
            true,
            true,
            false
        );

        /*
         * 创建一个没有 theme.yaml 的普通目录。
         *
         * 即使其中存在 templates 目录，
         * 也不能被识别为正式安装主题。
         */
        Path invalidDirectory = temporaryWorkDir
            .resolve("themes")
            .resolve("not-a-theme");

        Files.createDirectories(
            invalidDirectory.resolve("templates")
        );

        ThemeScanner scanner = createScanner();

        List<ThemeDescriptor> themes =
            scanner.scanInstalledThemes();

        /*
         * 没有 theme.yaml 的目录应该被忽略，
         * 所以最终只扫描到两个正式主题。
         */
        assertEquals(
            2,
            themes.size()
        );

        /*
         * 扫描结果按照 ThemeDescriptor.name 排序。
         *
         * alpha-theme 应排在 pebble-theme 前面。
         */
        ThemeDescriptor thymeleafTheme =
            themes.get(0);

        ThemeDescriptor pebbleTheme =
            themes.get(1);

        assertEquals(
            "alpha-theme",
            thymeleafTheme.name()
        );

        assertEquals(
            "thymeleaf",
            thymeleafTheme.engine()
        );

        assertTrue(
            thymeleafTheme.isThymeleaf()
        );

        assertFalse(
            thymeleafTheme.isPebble()
        );

        assertEquals(
            "Aquafish Team",
            thymeleafTheme.authorName()
        );

        assertNull(
            thymeleafTheme.parent()
        );

        assertTrue(
            thymeleafTheme.settingsYamlExists()
        );

        assertTrue(
            thymeleafTheme.templatesDirExists()
        );

        assertTrue(
            thymeleafTheme.assetsDirExists()
        );

        assertEquals(
            "pebble-theme",
            pebbleTheme.name()
        );

        assertEquals(
            "pebble",
            pebbleTheme.engine()
        );

        assertTrue(
            pebbleTheme.isPebble()
        );

        assertFalse(
            pebbleTheme.isThymeleaf()
        );

        assertEquals(
            "Pebble Theme Author",
            pebbleTheme.authorName()
        );

        assertNull(
            pebbleTheme.parent()
        );

        assertTrue(
            pebbleTheme.settingsYamlExists()
        );

        assertTrue(
            pebbleTheme.templatesDirExists()
        );

        /*
         * 创建 Pebble 测试主题时没有创建 assets，
         * 因此扫描结果必须准确显示不存在。
         */
        assertFalse(
            pebbleTheme.assetsDirExists()
        );
    }

    /**
     * 验证没有声明 engine 的旧主题仍然默认使用 Thymeleaf。
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldDefaultLegacyThemeToThymeleaf()
        throws Exception {

        createTheme(
            "legacy-theme",
            """
            id: legacy-theme
            title: "早期兼容主题"
            version: 0.8.0
            author:
              name: Legacy Author
            description: "没有声明 engine 的旧主题。"
            """,
            true,
            true,
            true
        );

        List<ThemeDescriptor> themes =
            createScanner().scanInstalledThemes();

        assertEquals(
            1,
            themes.size()
        );

        ThemeDescriptor theme =
            themes.get(0);

        assertEquals(
            "legacy-theme",
            theme.name()
        );

        assertEquals(
            "thymeleaf",
            theme.engine()
        );

        assertTrue(
            theme.isThymeleaf()
        );
    }

    /**
     * 验证未知模板引擎会在扫描主题时立即被拒绝。
     *
     * <p>
     * 不允许无效主题继续进入主题启用和访客渲染阶段。
     * </p>
     *
     * @throws Exception 当测试主题文件创建失败时抛出
     */
    @Test
    void shouldRejectUnsupportedEngineDuringScan()
        throws Exception {

        createTheme(
            "unsupported-theme",
            """
            id: unsupported-theme
            title: "不支持的模板引擎主题"
            version: 1.0.0
            engine: freemarker
            author:
              name: Invalid Author
            description: "该主题应在扫描阶段被拒绝。"
            """,
            true,
            true,
            true
        );

        IllegalArgumentException error =
            assertThrows(
                IllegalArgumentException.class,
                () -> createScanner()
                    .scanInstalledThemes()
            );

        assertTrue(
            error.getMessage().contains(
                "不受支持的模板引擎"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "freemarker"
            )
        );
    }

    /**
     * 创建连接到临时 workdir 的真实 ThemeScanner。
     *
     * <p>
     * 不启动完整 Spring 容器，
     * 直接使用生产类的公开构造方法组装依赖。
     * </p>
     *
     * @return 使用当前测试临时目录的主题扫描器
     */
    private ThemeScanner createScanner() {
        AquafishProperties properties =
            new AquafishProperties(
                temporaryWorkDir.toString(),
                "http://127.0.0.1:8520",
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
     * 在临时 workdir/themes 中创建一个真实测试主题。
     *
     * @param directoryName 主题目录名称
     * @param themeYamlContent theme.yaml UTF-8 内容
     * @param createSettings 是否创建 settings.yaml
     * @param createTemplates 是否创建 templates 目录
     * @param createAssets 是否创建 assets 目录
     * @throws Exception 当目录或文件创建失败时抛出
     */
    private void createTheme(
        String directoryName,
        String themeYamlContent,
        boolean createSettings,
        boolean createTemplates,
        boolean createAssets
    ) throws Exception {
        Path themeDirectory = temporaryWorkDir
            .resolve("themes")
            .resolve(directoryName);

        Files.createDirectories(
            themeDirectory
        );

        Files.writeString(
            themeDirectory.resolve("theme.yaml"),
            themeYamlContent,
            StandardCharsets.UTF_8
        );

        if (createSettings) {
            Files.writeString(
                themeDirectory.resolve("settings.yaml"),
                "# 测试主题设置文件\n",
                StandardCharsets.UTF_8
            );
        }

        if (createTemplates) {
            Files.createDirectories(
                themeDirectory.resolve("templates")
            );
        }

        if (createAssets) {
            Files.createDirectories(
                themeDirectory.resolve("assets")
            );
        }
    }
}
