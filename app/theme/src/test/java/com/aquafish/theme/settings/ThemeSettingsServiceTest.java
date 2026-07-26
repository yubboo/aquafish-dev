package com.aquafish.theme.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.theme.core.ActiveThemeResolver;
import com.aquafish.theme.core.ThemeScanner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 主题设置清单与实例值读写回归测试。
 */
class ThemeSettingsServiceTest {

    @TempDir
    Path temporaryDirectory;

    private ThemeSettingsService service;
    private Path persistedFile;

    @BeforeEach
    void setUp() throws Exception {
        Path workDir = temporaryDirectory.resolve("workdir");
        AquafishProperties properties = new AquafishProperties(
            workDir.toString(),
            "http://127.0.0.1:8080",
            "aq_",
            "default"
        );
        WorkDirResolver workDirResolver = new WorkDirResolver(properties);
        workDirResolver.ensureBaseDirectories();
        Path themeDirectory = workDirResolver.themesDir().resolve("default");
        Files.createDirectories(themeDirectory.resolve("templates"));
        Files.createDirectories(themeDirectory.resolve("assets"));
        Files.writeString(
            themeDirectory.resolve("theme.yaml"),
            """
            id: default
            title: 默认主题
            version: 1.0.0
            engine: thymeleaf
            """,
            StandardCharsets.UTF_8
        );
        Files.writeString(
            themeDirectory.resolve("settings.yaml"),
            """
            settings:
              primaryColor:
                label: 主色
                type: color
                default: "#3f5039"
              layout:
                label: 布局
                type: select
                default: default
                options:
                  - label: 默认
                    value: default
                  - label: 紧凑
                    value: compact
              heroImage:
                label: 首页图片
                type: image
                default: /theme-assets/images/backgrounds/home/hero.jpg
              showSearch:
                label: 显示搜索
                type: boolean
                default: true
            """,
            StandardCharsets.UTF_8
        );

        ThemeScanner scanner = new ThemeScanner(workDirResolver);
        ActiveThemeResolver activeThemeResolver = new ActiveThemeResolver(
            properties,
            scanner
        );
        service = new ThemeSettingsService(
            scanner,
            activeThemeResolver,
            workDirResolver
        );
        persistedFile = workDir.resolve("settings/themes/default.json");
    }

    @Test
    void shouldLoadDefaultsSaveInstanceValuesAndReset() {
        ThemeSettingsService.ThemeSettingsSnapshot defaults =
            service.load("default");
        assertTrue(defaults.available());
        assertFalse(defaults.customized());
        assertEquals("#3f5039", defaults.values().get("primaryColor"));
        assertEquals("default", defaults.values().get("layout"));

        ThemeSettingsService.ThemeSettingsSnapshot saved = service.save(
            "default",
            Map.of(
                "primaryColor", "#445566",
                "layout", "compact",
                "heroImage", "/uploads/themes/hero.jpg",
                "showSearch", false
            )
        );
        assertTrue(saved.customized());
        assertEquals("#445566", saved.values().get("primaryColor"));
        assertEquals("compact", saved.values().get("layout"));
        assertTrue(Files.isRegularFile(persistedFile));

        ThemeSettingsService.ThemeSettingsSnapshot reset =
            service.reset("default");
        assertFalse(reset.customized());
        assertEquals("#3f5039", reset.values().get("primaryColor"));
        assertFalse(Files.exists(persistedFile));
    }

    @Test
    void shouldRejectUnknownAndUnsafeImageValues() {
        ThemeSettingsException unknown = assertThrows(
            ThemeSettingsException.class,
            () -> service.save("default", Map.of("notDeclared", "value"))
        );
        assertEquals("THEME_SETTING_UNKNOWN", unknown.code());

        ThemeSettingsException unsafeImage = assertThrows(
            ThemeSettingsException.class,
            () -> service.save(
                "default",
                Map.of("heroImage", "/theme-assets/../secrets.txt")
            )
        );
        assertEquals("THEME_SETTING_VALUE_INVALID", unsafeImage.code());
    }
}
