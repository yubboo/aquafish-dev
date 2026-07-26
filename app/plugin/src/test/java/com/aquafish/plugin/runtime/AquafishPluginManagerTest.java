package com.aquafish.plugin.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginState;

class AquafishPluginManagerTest {

    @TempDir
    Path tempDirectory;

    private AquafishPluginManager manager;

    @AfterEach
    void closeManager() {
        if (manager != null) {
            manager.close();
        }
    }

    @Test
    void shouldLoadIsolatedPluginsAndOrderLifecycleByDependencies()
        throws Exception {
        WorkDirResolver resolver = resolver();
        createPlugin(
            resolver.pluginsDir().resolve("base-tools"),
            "base-tools",
            ""
        );
        createPlugin(
            resolver.pluginsDir().resolve("article-tools"),
            "article-tools",
            """
            dependencies:
              - id: base-tools
                version: ">=1.0.0"
            """
        );

        manager = new AquafishPluginManager(resolver);
        manager.afterPropertiesSet();
        manager.loadAll();

        assertThat(manager.snapshot()).hasSize(2);
        assertThat(manager.getPlugin("article-tools")
            .getPluginClassLoader())
            .isNotSameAs(
                manager.getPlugin("base-tools").getPluginClassLoader()
            );

        assertThat(manager.startWithDependencies("article-tools"))
            .isEqualTo(PluginState.STARTED);
        assertThat(manager.getPlugin("base-tools").getPluginState())
            .isEqualTo(PluginState.STARTED);
        assertThat(manager.getPlugin("article-tools").getPluginState())
            .isEqualTo(PluginState.STARTED);
        assertThat(
            resolver.pluginDataDir("article-tools")
        ).isDirectory();

        manager.stopWithDependents("base-tools");
        assertThat(manager.getPlugin("article-tools").getPluginState())
            .isEqualTo(PluginState.STOPPED);
        assertThat(manager.getPlugin("base-tools").getPluginState())
            .isEqualTo(PluginState.STOPPED);
    }

    private WorkDirResolver resolver() {
        AquafishProperties properties = new AquafishProperties(
            tempDirectory.resolve("workdir").toString(),
            "http://127.0.0.1:8520",
            "aq_",
            "default"
        );
        WorkDirResolver resolver = new WorkDirResolver(properties);
        resolver.ensureBaseDirectories();
        return resolver;
    }

    private void createPlugin(
        Path directory,
        String pluginId,
        String extra
    ) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(
            directory.resolve("plugin.yaml"),
            """
            id: %s
            name: %s
            version: 1.0.0
            %s
            """.formatted(pluginId, pluginId, extra)
        );
    }
}
