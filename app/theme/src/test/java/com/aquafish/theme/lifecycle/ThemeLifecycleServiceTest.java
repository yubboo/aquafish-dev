package com.aquafish.theme.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.core.operation.InMemoryExtensionOperationCoordinator;
import com.aquafish.theme.core.ActiveThemeResolver;
import com.aquafish.theme.core.ThemeScanner;
import com.aquafish.theme.install.ThemeArchiveExtractor;
import com.aquafish.theme.install.ThemeInstallFileOperations;
import com.aquafish.theme.manifest.ThemeManifestParser;
import com.aquafish.theme.validation.ThemePackageValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 主题启用、升级、保护卸载的文件系统闭环测试。
 */
class ThemeLifecycleServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldActivateAndSafelyUninstallNonActiveTheme() throws Exception {
        TestContext context = context();
        createTheme(context.workDirResolver(), "default", "0.3.0");
        createTheme(context.workDirResolver(), "paper-garden", "1.0.0");

        ThemeLifecycleResult activated =
            context.service().activate("paper-garden");
        assertTrue(activated.active());
        assertEquals("paper-garden", context.properties().activeTheme());
        assertThrows(
            ThemeLifecycleException.class,
            () -> context.service().uninstall("paper-garden")
        );

        context.service().activate("default");
        ThemeLifecycleResult removed =
            context.service().uninstall("paper-garden");
        assertEquals("uninstall", removed.action());
        assertFalse(
            Files.exists(context.workDirResolver().themesDir().resolve("paper-garden"))
        );
        try (var backups = Files.walk(
            context.workDirResolver().backupsDir().resolve("themes/uninstalled")
        )) {
            assertTrue(
                backups.anyMatch(path ->
                    path.getFileName().toString().startsWith("paper-garden-")
                )
            );
        }
    }

    @Test
    void shouldUpgradeThemeAndKeepDefaultThemeProtected() throws Exception {
        TestContext context = context();
        createTheme(context.workDirResolver(), "default", "0.3.0");
        createTheme(context.workDirResolver(), "paper-garden", "1.0.0");
        Path upgradePackage = createThemeZip("paper-garden", "2.0.0");

        ThemeLifecycleResult upgraded =
            context.service().upgrade("paper-garden", upgradePackage);
        assertEquals("2.0.0", upgraded.version());
        assertEquals(
            "2.0.0",
            context.scanner().scanInstalledThemes().stream()
                .filter(theme -> "paper-garden".equals(theme.name()))
                .findFirst()
                .orElseThrow()
                .version()
        );
        assertThrows(
            ThemeLifecycleException.class,
            () -> context.service().uninstall("default")
        );
        assertThrows(
            ThemeLifecycleException.class,
            () -> context.service().upgrade("default", upgradePackage)
        );
    }

    private TestContext context() throws Exception {
        AquafishProperties properties = new AquafishProperties(
            temporaryDirectory.toString(),
            "http://127.0.0.1:8520",
            "aq_",
            "default"
        );
        WorkDirResolver workDirResolver = new WorkDirResolver(properties);
        workDirResolver.ensureBaseDirectories();
        Files.writeString(
            workDirResolver.applicationYamlFile(),
            """
            aquafish:
              theme:
                active: 'default'
            """,
            StandardCharsets.UTF_8
        );
        ThemeManifestParser parser = new ThemeManifestParser();
        ThemeScanner scanner = new ThemeScanner(workDirResolver, parser);
        ActiveThemeResolver activeThemeResolver =
            new ActiveThemeResolver(properties, scanner);
        ThemeInstallFileOperations fileOperations =
            new ThemeInstallFileOperations();
        ThemeRuntimeConfigurationService configurationService =
            new ThemeRuntimeConfigurationService(workDirResolver, properties);
        ThemeLifecycleService service = new ThemeLifecycleService(
            scanner,
            activeThemeResolver,
            configurationService,
            new ThemePackageValidator(),
            new ThemeArchiveExtractor(workDirResolver, parser, fileOperations),
            fileOperations,
            workDirResolver,
            new InMemoryExtensionOperationCoordinator()
        );
        return new TestContext(
            properties,
            workDirResolver,
            scanner,
            service
        );
    }

    private void createTheme(
        WorkDirResolver resolver,
        String themeId,
        String version
    ) throws Exception {
        Path themeDirectory = resolver.themesDir().resolve(themeId);
        Files.createDirectories(themeDirectory.resolve("templates"));
        Files.writeString(
            themeDirectory.resolve("theme.yaml"),
            manifest(themeId, version),
            StandardCharsets.UTF_8
        );
        Files.writeString(
            themeDirectory.resolve("templates/index.html"),
            "<html><body>" + version + "</body></html>",
            StandardCharsets.UTF_8
        );
    }

    private Path createThemeZip(String themeId, String version) throws Exception {
        Path zip = temporaryDirectory.resolve(themeId + "-" + version + ".zip");
        try (ZipOutputStream output = new ZipOutputStream(
            Files.newOutputStream(zip),
            StandardCharsets.UTF_8
        )) {
            writeEntry(output, "theme.yaml", manifest(themeId, version));
            writeEntry(
                output,
                "templates/index.html",
                "<html><body>" + version + "</body></html>"
            );
        }
        return zip;
    }

    private void writeEntry(
        ZipOutputStream output,
        String name,
        String content
    ) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private String manifest(String themeId, String version) {
        return """
            id: %s
            title: %s
            version: %s
            engine: thymeleaf
            author:
              name: Aquafish Test
            apiVersion: 1
            description: 主题生命周期测试。
            """.formatted(themeId, themeId, version);
    }

    private record TestContext(
        AquafishProperties properties,
        WorkDirResolver workDirResolver,
        ThemeScanner scanner,
        ThemeLifecycleService service
    ) {
    }
}
