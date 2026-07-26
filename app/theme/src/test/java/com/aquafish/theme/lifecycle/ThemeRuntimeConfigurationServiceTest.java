package com.aquafish.theme.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 活动主题配置写入测试。
 *
 * <p>验证切换只修改 theme.active，且保留数据库和授权配置并创建备份。</p>
 */
class ThemeRuntimeConfigurationServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldPersistActiveThemeWithoutOverwritingOtherConfiguration()
        throws Exception {
        AquafishProperties properties = properties("default");
        WorkDirResolver workDirResolver = new WorkDirResolver(properties);
        Path config = workDirResolver.applicationYamlFile();
        Files.createDirectories(config.getParent());
        Files.writeString(
            config,
            """
            server:
              port: 8080
            aquafish:
              database:
                password: 'secret-value'
              theme:
                active: 'default'
              license:
                enforcement-enabled: true
            """,
            StandardCharsets.UTF_8
        );

        ThemeRuntimeConfigurationService service =
            new ThemeRuntimeConfigurationService(workDirResolver, properties);
        service.activate("paper-garden");

        String updated = Files.readString(config, StandardCharsets.UTF_8);
        assertTrue(updated.contains("active: 'paper-garden'"));
        assertTrue(updated.contains("password: 'secret-value'"));
        assertTrue(updated.contains("enforcement-enabled: true"));
        assertEquals("paper-garden", properties.activeTheme());
        try (var backups = Files.list(
            workDirResolver.backupsDir().resolve("theme-config")
        )) {
            assertEquals(1L, backups.count());
        }
    }

    private AquafishProperties properties(String activeTheme) {
        return new AquafishProperties(
            temporaryDirectory.toString(),
            "http://127.0.0.1:8080",
            "aq_",
            activeTheme
        );
    }
}
