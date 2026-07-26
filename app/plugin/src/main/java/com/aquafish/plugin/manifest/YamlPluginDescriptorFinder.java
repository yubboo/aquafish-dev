package com.aquafish.plugin.manifest;

import com.aquafish.plugin.runtime.DefaultAquafishPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import org.pf4j.PluginDependency;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginRuntimeException;
import org.pf4j.util.FileUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 从 {@code plugin.yaml} 读取 PF4J 插件清单。
 *
 * <p>同时支持开发目录和正式 JAR。解析器使用 SnakeYAML SafeConstructor，不创建清单中指定的
 * 任意 Java 类型；所有 ID、依赖和 Spring 组件仍由后续 PF4J/ClassLoader 阶段二次校验。</p>
 */
public final class YamlPluginDescriptorFinder
    implements PluginDescriptorFinder {

    public static final String MANIFEST_NAME = "plugin.yaml";

    @Override
    public boolean isApplicable(Path pluginPath) {
        if (pluginPath == null || !Files.exists(pluginPath)) {
            return false;
        }
        if (Files.isDirectory(pluginPath)) {
            return Files.isRegularFile(pluginPath.resolve(MANIFEST_NAME));
        }
        return FileUtils.isJarFile(pluginPath);
    }

    @Override
    public PluginDescriptor find(Path pluginPath) {
        try (InputStream input = openManifest(pluginPath)) {
            Object loaded = new Yaml(
                new SafeConstructor(new LoaderOptions())
            ).load(input);
            if (!(loaded instanceof Map<?, ?> raw)) {
                throw new PluginRuntimeException("plugin.yaml 根节点必须是对象。");
            }
            return descriptor(stringObjectMap(raw));
        } catch (IOException error) {
            throw new PluginRuntimeException(
                "读取插件清单失败：" + pluginPath,
                error
            );
        }
    }

    private InputStream openManifest(Path pluginPath) throws IOException {
        if (Files.isDirectory(pluginPath)) {
            return Files.newInputStream(pluginPath.resolve(MANIFEST_NAME));
        }

        JarFile jarFile = new JarFile(pluginPath.toFile());
        var entry = jarFile.getJarEntry(MANIFEST_NAME);
        if (entry == null) {
            jarFile.close();
            throw new PluginRuntimeException(
                "插件 JAR 根目录缺少 " + MANIFEST_NAME + "：" + pluginPath
            );
        }
        InputStream input = jarFile.getInputStream(entry);
        return new InputStream() {
            @Override
            public int read() throws IOException {
                return input.read();
            }

            @Override
            public int read(byte[] bytes, int offset, int length)
                throws IOException {
                return input.read(bytes, offset, length);
            }

            @Override
            public void close() throws IOException {
                try {
                    input.close();
                } finally {
                    jarFile.close();
                }
            }
        };
    }

    private AquafishPluginDescriptor descriptor(
        Map<String, Object> root
    ) {
        Map<String, Object> metadata = map(root.get("metadata"));
        Map<String, Object> spec = map(root.get("spec"));

        String pluginId = firstText(
            root.get("id"),
            root.get("key"),
            metadata.get("name")
        );
        requirePluginId(pluginId);

        String displayName = firstText(
            root.get("name"),
            spec.get("displayName"),
            metadata.get("displayName"),
            pluginId
        );
        String version = requiredText(
            "插件版本",
            firstText(root.get("version"), spec.get("version"))
        );
        String pluginClass = firstText(
            root.get("main"),
            root.get("pluginClass"),
            spec.get("main"),
            spec.get("pluginClass"),
            DefaultAquafishPlugin.class.getName()
        );
        String description = firstText(
            root.get("description"),
            spec.get("description"),
            ""
        );
        String provider = provider(root, spec);
        String requires = firstText(
            root.get("requires"),
            spec.get("requires"),
            "0.0.1"
        );
        String license = license(
            root.containsKey("license")
                ? root.get("license")
                : spec.get("license")
        );

        AquafishPluginDescriptor descriptor =
            new AquafishPluginDescriptor(
                pluginId,
                displayName,
                description,
                requiredText("插件主类", pluginClass),
                version,
                requires,
                provider,
                license,
                springComponents(root, spec)
            );

        Object dependencies = root.containsKey("dependencies")
            ? root.get("dependencies")
            : spec.get("pluginDependencies");
        dependencyExpressions(dependencies).stream()
            .map(PluginDependency::new)
            .forEach(descriptor::addDependency);
        return descriptor;
    }

    private List<String> dependencyExpressions(Object value) {
        if (value == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((id, version) -> result.add(
                dependencyExpression(
                    text(id),
                    text(version),
                    false
                )
            ));
            return List.copyOf(result);
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Map<?, ?> raw) {
                    Map<String, Object> dependency = stringObjectMap(raw);
                    result.add(dependencyExpression(
                        firstText(
                            dependency.get("id"),
                            dependency.get("key")
                        ),
                        firstText(
                            dependency.get("version"),
                            dependency.get("requires"),
                            "*"
                        ),
                        bool(dependency.get("optional"))
                    ));
                } else if (!text(item).isBlank()) {
                    result.add(text(item));
                }
            }
            return List.copyOf(result);
        }
        String single = text(value);
        return single.isBlank() ? List.of() : List.of(single);
    }

    private String dependencyExpression(
        String id,
        String version,
        boolean optional
    ) {
        requirePluginId(id);
        String normalizedVersion = version == null || version.isBlank()
            || "*".equals(version.trim())
            ? ""
            : "@" + version.trim();
        return id.trim() + (optional ? "?" : "") + normalizedVersion;
    }

    private List<String> springComponents(
        Map<String, Object> root,
        Map<String, Object> spec
    ) {
        Object value = root.get("springComponents");
        if (value == null) {
            value = map(root.get("spring")).get("components");
        }
        if (value == null) {
            value = spec.get("springComponents");
        }
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
            .map(this::text)
            .filter(component -> !component.isBlank())
            .distinct()
            .toList();
    }

    private String provider(
        Map<String, Object> root,
        Map<String, Object> spec
    ) {
        Object author = spec.get("author");
        String authorName = author instanceof Map<?, ?> raw
            ? text(stringObjectMap(raw).get("name"))
            : text(author);
        return firstText(root.get("provider"), authorName, "");
    }

    private String license(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                .map(item -> {
                    if (item instanceof Map<?, ?> raw) {
                        return text(stringObjectMap(raw).get("name"));
                    }
                    return text(item);
                })
                .filter(item -> !item.isBlank())
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        }
        return text(value);
    }

    private void requirePluginId(String pluginId) {
        if (pluginId == null
            || !pluginId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")) {
            throw new PluginRuntimeException(
                "插件 ID 只能包含字母、数字、点、下划线和短横线，长度 1-120。"
            );
        }
    }

    private String requiredText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new PluginRuntimeException(field + "不能为空。");
        }
        return value.trim();
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean bool(Object value) {
        return value instanceof Boolean bool
            ? bool
            : "true".equalsIgnoreCase(text(value))
                || "1".equals(text(value));
    }

    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw
            ? stringObjectMap(raw)
            : Map.of();
    }

    private Map<String, Object> stringObjectMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(text(key), value));
        return result;
    }
}
