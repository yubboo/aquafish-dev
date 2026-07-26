package com.aquafish.admin.plugin.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.plugin.runtime.AquafishPluginManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;

class PluginUiResourceServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldDiscoverStartedDirectoryPluginAndReadDeclaredAssets()
        throws Exception {
        Path pluginRoot = createDirectoryPlugin(
            "demo-plugin",
            "1.0.0"
        );
        PluginWrapper wrapper = wrapper(
            "demo-plugin",
            "1.0.0",
            pluginRoot,
            PluginState.STARTED
        );
        PluginUiResourceService service = service(wrapper);

        PluginUiResourceService.Catalog catalog = service.scan();

        assertThat(catalog.failures()).isEmpty();
        assertThat(catalog.items()).singleElement()
            .satisfies(descriptor -> {
                assertThat(descriptor.pluginId())
                    .isEqualTo("demo-plugin");
                assertThat(descriptor.entry())
                    .isEqualTo("main.js");
                assertThat(descriptor.style())
                    .isEqualTo("style.css");
            });

        PluginUiResourceService.Asset asset = service.asset(
            "demo-plugin",
            "main.js"
        );
        assertThat(asset.resource().getContentAsByteArray())
            .isEqualTo("window.demo=true;".getBytes());
        assertThat(asset.mediaType().toString())
            .isEqualTo("text/javascript;charset=UTF-8");
    }

    @Test
    void shouldIgnoreStoppedPluginAndReportManifestMismatch()
        throws Exception {
        Path stoppedRoot = createDirectoryPlugin(
            "stopped",
            "1.0.0"
        );
        Path invalidRoot = createDirectoryPlugin(
            "another-id",
            "1.0.0"
        );
        PluginWrapper stopped = wrapper(
            "stopped",
            "1.0.0",
            stoppedRoot,
            PluginState.STOPPED
        );
        PluginWrapper invalid = wrapper(
            "invalid",
            "1.0.0",
            invalidRoot,
            PluginState.STARTED
        );
        PluginUiResourceService service = service(
            stopped,
            invalid
        );

        PluginUiResourceService.Catalog catalog = service.scan();

        assertThat(catalog.items()).isEmpty();
        assertThat(catalog.failures()).singleElement()
            .satisfies(failure -> {
                assertThat(failure.pluginId()).isEqualTo("invalid");
                assertThat(failure.message()).contains("ID");
            });
    }

    @Test
    void shouldReadJarAssetAndRejectUndeclaredPath()
        throws Exception {
        Path jarPath = createJarPlugin("jar-demo", "2.0.0");
        PluginWrapper wrapper = wrapper(
            "jar-demo",
            "2.0.0",
            jarPath,
            PluginState.STARTED
        );
        PluginUiResourceService service = service(wrapper);

        assertThat(
            service.asset("jar-demo", "/style.css")
                .resource()
                .getContentAsByteArray()
        ).isEqualTo(".demo{}".getBytes());
        assertThatThrownBy(() ->
            service.asset("jar-demo", "../plugin.yaml")
        )
            .isInstanceOf(IOException.class)
            .hasMessageContaining("不合法");
    }

    @Test
    void shouldRejectPluginPackageOutsideConfiguredPluginsDirectory()
        throws Exception {
        Path allowedRoot = Files.createDirectory(
            tempDirectory.resolve("allowed")
        );
        Path outsidePlugin = createDirectoryPlugin(
            "outside",
            "1.0.0"
        );
        PluginWrapper wrapper = wrapper(
            "outside",
            "1.0.0",
            outsidePlugin,
            PluginState.STARTED
        );

        PluginUiResourceService.Catalog catalog = serviceAt(
            allowedRoot,
            wrapper
        ).scan();

        assertThat(catalog.items()).isEmpty();
        assertThat(catalog.failures()).singleElement()
            .satisfies(failure ->
                assertThat(failure.message())
                    .contains("workdir/plugins")
            );
    }

    private PluginUiResourceService service(
        PluginWrapper... wrappers
    ) {
        return serviceAt(tempDirectory, wrappers);
    }

    private PluginUiResourceService serviceAt(
        Path pluginsRoot,
        PluginWrapper... wrappers
    ) {
        AquafishPluginManager manager = mock(
            AquafishPluginManager.class
        );
        List<PluginWrapper> plugins = List.of(wrappers);
        when(manager.getPlugins()).thenReturn(plugins);
        for (PluginWrapper wrapper : wrappers) {
            when(manager.getPlugin(wrapper.getPluginId()))
                .thenReturn(wrapper);
        }
        WorkDirResolver workDirResolver = mock(
            WorkDirResolver.class
        );
        when(workDirResolver.pluginsDir())
            .thenReturn(pluginsRoot);
        return new PluginUiResourceService(
            manager,
            workDirResolver
        );
    }

    private PluginWrapper wrapper(
        String pluginId,
        String version,
        Path pluginPath,
        PluginState state
    ) {
        PluginWrapper wrapper = mock(PluginWrapper.class);
        PluginDescriptor descriptor = mock(PluginDescriptor.class);
        when(wrapper.getPluginId()).thenReturn(pluginId);
        when(wrapper.getPluginPath()).thenReturn(pluginPath);
        when(wrapper.getPluginState()).thenReturn(state);
        when(wrapper.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getVersion()).thenReturn(version);
        return wrapper;
    }

    private Path createDirectoryPlugin(
        String manifestPluginId,
        String version
    ) throws IOException {
        Path pluginRoot = Files.createDirectory(
            tempDirectory.resolve("directory-" + manifestPluginId)
        );
        Path ui = Files.createDirectory(
            pluginRoot.resolve("ui")
        );
        Files.writeString(
            ui.resolve("ui-manifest.json"),
            manifest(manifestPluginId, version)
        );
        Files.writeString(
            ui.resolve("main.js"),
            "window.demo=true;"
        );
        Files.writeString(
            ui.resolve("style.css"),
            ".demo{}"
        );
        return pluginRoot;
    }

    private Path createJarPlugin(
        String pluginId,
        String version
    ) throws IOException {
        Path jarPath = tempDirectory.resolve(pluginId + ".jar");
        try (JarOutputStream output = new JarOutputStream(
            Files.newOutputStream(jarPath)
        )) {
            jarEntry(
                output,
                "ui/ui-manifest.json",
                manifest(pluginId, version)
            );
            jarEntry(output, "ui/main.js", "window.jarDemo=true;");
            jarEntry(output, "ui/style.css", ".demo{}");
        }
        return jarPath;
    }

    private void jarEntry(
        JarOutputStream output,
        String name,
        String content
    ) throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(content.getBytes());
        output.closeEntry();
    }

    private String manifest(String pluginId, String version) {
        String globalName = "AqPlugin_"
            + pluginId.replaceAll("[^A-Za-z0-9_$]", "_");
        return """
            {
              "schemaVersion": 1,
              "pluginId": "%s",
              "pluginVersion": "%s",
              "format": "iife",
              "globalName": "%s",
              "entry": "main.js",
              "style": "style.css",
              "externals": [
                "vue",
                "vue-router",
                "pinia",
                "axios",
                "@aquafish/components",
                "@aquafish/api-client",
                "@aquafish/ui-shared"
              ]
            }
            """.formatted(pluginId, version, globalName);
    }
}
