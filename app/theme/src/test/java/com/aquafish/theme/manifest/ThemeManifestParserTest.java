package com.aquafish.theme.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Aquafish 正式 theme.yaml 解析器测试。
 */
class ThemeManifestParserTest {

    /**
     * 测试临时目录。
     */
    @TempDir
    Path temporaryDirectory;

    /**
     * 验证当前官方 default 主题格式能够正常解析。
     */
    @Test
    void shouldParseCurrentThemeYamlFormat() {
        ThemeManifest manifest =
            new ThemeManifestParser().parse(
                """
                id: default
                name: default
                title: Aquafish 默认主题
                version: 0.1.0
                engine: thymeleaf
                author:
                  name: Aquafish Team
                parent: ""
                description: Aquafish 官方默认主题。
                """
            );

        assertEquals(
            "default",
            manifest.id()
        );

        assertEquals(
            "Aquafish 默认主题",
            manifest.title()
        );

        assertEquals(
            "0.1.0",
            manifest.version()
        );

        assertEquals(
            "thymeleaf",
            manifest.engine()
        );

        assertEquals(
            "Aquafish Team",
            manifest.author().name()
        );

        assertNull(
            manifest.parent()
        );

        assertEquals(
            1,
            manifest.apiVersion()
        );

        assertTrue(
            manifest.isThymeleaf()
        );

        assertFalse(
            manifest.isPebble()
        );
    }

    /**
     * 验证旧主题可以使用 name 作为唯一标识，
     * 并在缺少 engine 时默认使用 Thymeleaf。
     */
    @Test
    void shouldSupportLegacyNameAndDefaultEngine() {
        ThemeManifest manifest =
            new ThemeManifestParser().parse(
                """
                name: legacy-theme
                title: 旧主题
                author: Legacy Author
                """
            );

        assertEquals(
            "legacy-theme",
            manifest.id()
        );

        assertEquals(
            "0.0.0",
            manifest.version()
        );

        assertEquals(
            "thymeleaf",
            manifest.engine()
        );

        assertEquals(
            "Legacy Author",
            manifest.author().name()
        );
    }

    /**
     * 验证 Pebble、父主题、API 版本和运行要求。
     */
    @Test
    void shouldParseExtendedManifestFields() {
        ThemeManifest manifest =
            new ThemeManifestParser().parse(
                """
                id: forum-child
                title: 论坛子主题
                version: 2.1.0
                engine: PEBBLE
                parent: forum-parent
                apiVersion: 2
                author:
                  name: Theme Author
                  url: https://example.com
                requires:
                  aquafish: ">=1.0.0"
                  java: ">=21"
                description: 扩展字段测试。
                """
            );

        assertEquals(
            "pebble",
            manifest.engine()
        );

        assertTrue(
            manifest.isPebble()
        );

        assertTrue(
            manifest.hasParent()
        );

        assertEquals(
            "forum-parent",
            manifest.parent()
        );

        assertEquals(
            2,
            manifest.apiVersion()
        );

        assertEquals(
            "https://example.com",
            manifest.author().url()
        );

        assertEquals(
            ">=1.0.0",
            manifest
                .requirements()
                .aquafish()
        );

        assertEquals(
            ">=21",
            manifest
                .requirements()
                .java()
        );
    }

    /**
     * 验证未知模板引擎会在清单阶段被拒绝。
     */
    @Test
    void shouldRejectUnsupportedEngine() {
        ThemeManifestException error =
            assertThrows(
                ThemeManifestException.class,
                () -> new ThemeManifestParser()
                    .parse(
                        """
                        id: invalid-theme
                        engine: freemarker
                        """
                    )
            );

        assertTrue(
            error.getMessage().contains(
                "不受支持的模板引擎"
            )
        );
    }

    /**
     * 验证缺少 id 和旧 name 时会失败。
     */
    @Test
    void shouldRejectMissingThemeId() {
        ThemeManifestException error =
            assertThrows(
                ThemeManifestException.class,
                () -> new ThemeManifestParser()
                    .parse(
                        """
                        title: 没有唯一标识的主题
                        engine: thymeleaf
                        """
                    )
            );

        assertTrue(
            error.getMessage().contains(
                "缺少主题唯一标识"
            )
        );
    }

    /**
     * 验证非法 YAML 会转成统一主题清单异常。
     */
    @Test
    void shouldRejectMalformedYaml() {
        assertThrows(
            ThemeManifestException.class,
            () -> new ThemeManifestParser()
                .parse(
                    """
                    id: broken-theme
                    author:
                      name: [invalid
                    """
                )
        );
    }

    /**
     * 验证能够从真实 UTF-8 文件读取主题清单。
     *
     * @throws Exception 文件创建失败
     */
    @Test
    void shouldParseUtf8ThemeYamlFile()
        throws Exception {

        Path themeYamlFile =
            temporaryDirectory.resolve(
                "theme.yaml"
            );

        Files.writeString(
            themeYamlFile,
            """
            id: file-theme
            title: 文件主题
            version: 1.0.0
            engine: thymeleaf
            author:
              name: 文件作者
            description: UTF-8 文件测试。
            """,
            StandardCharsets.UTF_8
        );

        ThemeManifest manifest =
            new ThemeManifestParser().parse(
                themeYamlFile
            );

        assertEquals(
            "file-theme",
            manifest.id()
        );

        assertEquals(
            "文件主题",
            manifest.title()
        );

        assertEquals(
            "文件作者",
            manifest.author().name()
        );
    }

    /**
     * 验证不存在的 theme.yaml 会返回明确错误。
     */
    @Test
    void shouldRejectMissingThemeYamlFile() {
        Path missingFile =
            temporaryDirectory.resolve(
                "missing-theme.yaml"
            );

        ThemeManifestException error =
            assertThrows(
                ThemeManifestException.class,
                () -> new ThemeManifestParser()
                    .parse(missingFile)
            );

        assertTrue(
            error.getMessage().contains(
                "文件不存在"
            )
        );
    }

    /**
     * 验证主题清单阶段允许暂存直接自继承声明。
     *
     * <p>
     * ThemeManifest 只负责单个清单字段校验。
     * 自继承和间接循环由 ThemeInheritanceResolver
     * 统一检测并输出完整循环链。
     * </p>
     */
    @Test
    void shouldAllowSelfParentForInheritanceValidation() {
        ThemeManifest manifest =
            new ThemeManifestParser().parse(
                """
                id: self-cycle
                title: 自继承测试主题
                engine: thymeleaf
                parent: self-cycle
                """
            );

        assertEquals(
            "self-cycle",
            manifest.id()
        );

        assertEquals(
            "self-cycle",
            manifest.parent()
        );

        assertTrue(
            manifest.hasParent()
        );
    }

}
