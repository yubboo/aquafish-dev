package com.aquafish.plugin.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlPluginDescriptorFinderTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldParseAquafishManifestAndDependencies() throws Exception {
        Path plugin = tempDirectory.resolve("article-tools");
        Files.createDirectories(plugin);
        Files.writeString(
            plugin.resolve("plugin.yaml"),
            """
            id: article-tools
            name: 文章工具
            version: 1.2.0
            description: 提供文章扩展能力
            provider: Aquafish Lab
            dependencies:
              - id: common-tools
                version: ">=1.0.0"
              - id: optional-ai
                optional: true
            spring:
              components:
                - example.ArticleConfiguration
            """
        );

        AquafishPluginDescriptor descriptor =
            (AquafishPluginDescriptor)
                new YamlPluginDescriptorFinder().find(plugin);

        assertThat(descriptor.getPluginId()).isEqualTo("article-tools");
        assertThat(descriptor.displayName()).isEqualTo("文章工具");
        assertThat(descriptor.getVersion()).isEqualTo("1.2.0");
        assertThat(descriptor.getDependencies())
            .extracting(dependency -> dependency.getPluginId())
            .containsExactly("common-tools", "optional-ai");
        assertThat(descriptor.getDependencies().get(1).isOptional())
            .isTrue();
        assertThat(descriptor.springComponents())
            .containsExactly("example.ArticleConfiguration");
    }

    @Test
    void shouldParseHaloCompatibleMetadataShape() throws Exception {
        Path plugin = tempDirectory.resolve("halo-shape");
        Files.createDirectories(plugin);
        Files.writeString(
            plugin.resolve("plugin.yaml"),
            """
            apiVersion: plugin.aquafish.dev/v1alpha1
            kind: Plugin
            metadata:
              name: halo-shape
            spec:
              displayName: Halo 兼容清单
              version: 2.0.0
              requires: ">=0.0.1"
              author:
                name: Demo
              pluginDependencies:
                base-plugin: ">=1.0.0"
            """
        );

        AquafishPluginDescriptor descriptor =
            (AquafishPluginDescriptor)
                new YamlPluginDescriptorFinder().find(plugin);

        assertThat(descriptor.getPluginId()).isEqualTo("halo-shape");
        assertThat(descriptor.displayName()).isEqualTo("Halo 兼容清单");
        assertThat(descriptor.getProvider()).isEqualTo("Demo");
        assertThat(descriptor.getDependencies())
            .singleElement()
            .extracting(dependency -> dependency.getPluginId())
            .isEqualTo("base-plugin");
    }
}
