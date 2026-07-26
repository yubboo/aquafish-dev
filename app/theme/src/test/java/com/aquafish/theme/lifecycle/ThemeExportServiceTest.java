package com.aquafish.theme.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.core.operation.InMemoryExtensionOperationCoordinator;
import com.aquafish.theme.core.ThemeScanner;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 主题标准 ZIP 导出结构回归测试。
 */
class ThemeExportServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldExportInstallableZipWithoutInstanceSettings() throws Exception {
        Path workDir = temporaryDirectory.resolve("workdir");
        AquafishProperties properties = new AquafishProperties(
            workDir.toString(),
            "http://127.0.0.1:8080",
            "aq_",
            "default"
        );
        WorkDirResolver resolver = new WorkDirResolver(properties);
        resolver.ensureBaseDirectories();
        Path theme = resolver.themesDir().resolve("default");
        Files.createDirectories(theme.resolve("templates"));
        Files.createDirectories(theme.resolve("assets/css"));
        Files.writeString(
            theme.resolve("theme.yaml"),
            "id: default\ntitle: 默认主题\nversion: 1.0.0\nengine: thymeleaf\n",
            StandardCharsets.UTF_8
        );
        Files.writeString(
            theme.resolve("settings.yaml"),
            "settings: {}\n",
            StandardCharsets.UTF_8
        );
        Files.writeString(
            theme.resolve("templates/index.html"),
            "<h1>Aquafish</h1>",
            StandardCharsets.UTF_8
        );
        Files.writeString(
            theme.resolve("assets/css/style.css"),
            ":root {}",
            StandardCharsets.UTF_8
        );
        Files.createDirectories(workDir.resolve("settings/themes"));
        Files.writeString(
            workDir.resolve("settings/themes/default.json"),
            "{\"private\":\"value\"}",
            StandardCharsets.UTF_8
        );

        ThemeExportService service = new ThemeExportService(
            new ThemeScanner(resolver),
            resolver,
            new InMemoryExtensionOperationCoordinator()
        );
        Set<String> entries = zipEntries(service.export("default"));

        assertTrue(entries.contains("theme.yaml"));
        assertTrue(entries.contains("settings.yaml"));
        assertTrue(entries.contains("templates/index.html"));
        assertTrue(entries.contains("assets/css/style.css"));
        assertFalse(entries.stream().anyMatch(name ->
            name.contains("settings/themes") || name.startsWith("default/")
        ));
    }

    private Set<String> zipEntries(byte[] archive) throws Exception {
        Set<String> entries = new HashSet<>();
        try (
            ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(archive)
            )
        ) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        return entries;
    }
}
