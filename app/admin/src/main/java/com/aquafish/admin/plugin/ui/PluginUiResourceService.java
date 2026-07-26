package com.aquafish.admin.plugin.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.plugin.runtime.AquafishPluginManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.pf4j.PluginWrapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/**
 * 已启动插件 UI 资源的安全解析器。
 *
 * <p>宿主只接受插件包 {@code ui/} 目录中的固定构建协议，不接受清单提供远程 URL，
 * 也不把插件目录直接映射为通用静态目录。目录型插件还会解析真实路径，防止符号链接
 * 和 {@code ..} 越过插件边界。</p>
 */
@Service
public class PluginUiResourceService {

    static final String UI_DIRECTORY = "ui";
    static final String UI_MANIFEST = "ui-manifest.json";
    static final String ENTRY_FILE = "main.js";
    static final String STYLE_FILE = "style.css";
    static final int MAX_MANIFEST_BYTES = 64 * 1024;
    static final int MAX_ASSET_BYTES = 8 * 1024 * 1024;
    static final List<String> REQUIRED_EXTERNALS = List.of(
        "vue",
        "vue-router",
        "pinia",
        "axios",
        "@aquafish/components",
        "@aquafish/api-client",
        "@aquafish/ui-shared"
    );

    private final AquafishPluginManager pluginManager;
    private final WorkDirResolver workDirResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PluginUiResourceService(
        AquafishPluginManager pluginManager,
        WorkDirResolver workDirResolver
    ) {
        this.pluginManager = pluginManager;
        this.workDirResolver = workDirResolver;
    }

    /**
     * 扫描所有已启动插件。没有 UI 清单的后端插件会被正常忽略，清单损坏则进入失败列表。
     */
    public Catalog scan() {
        List<Descriptor> items = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        for (PluginWrapper wrapper : pluginManager.getPlugins()) {
            if (!wrapper.getPluginState().isStarted()) {
                continue;
            }
            try {
                requirePluginPackageBoundary(
                    wrapper.getPluginPath()
                );
                if (!manifestExists(wrapper.getPluginPath())) {
                    continue;
                }
                items.add(readDescriptor(wrapper));
            } catch (Exception error) {
                failures.add(new Failure(
                    wrapper.getPluginId(),
                    rootMessage(error)
                ));
            }
        }
        items.sort((left, right) ->
            left.pluginId().compareToIgnoreCase(right.pluginId())
        );
        failures.sort((left, right) ->
            left.pluginId().compareToIgnoreCase(right.pluginId())
        );
        return new Catalog(
            List.copyOf(items),
            List.copyOf(failures)
        );
    }

    /**
     * 读取清单明确声明的入口或样式文件；其他文件名一律拒绝。
     */
    public Asset asset(String pluginId, String assetPath)
        throws IOException {
        String safePluginId = requirePluginId(pluginId);
        PluginWrapper wrapper = pluginManager.getPlugin(safePluginId);
        if (wrapper == null || !wrapper.getPluginState().isStarted()) {
            throw new IOException("插件不存在或尚未启动。");
        }
        requirePluginPackageBoundary(wrapper.getPluginPath());

        Descriptor descriptor = readDescriptor(wrapper);
        String safeAsset = normalizeAssetName(assetPath);
        boolean isEntry = descriptor.entry().equals(safeAsset);
        boolean isStyle = descriptor.style() != null
            && descriptor.style().equals(safeAsset);
        if (!isEntry && !isStyle) {
            throw new IOException("插件 UI 资源不在清单允许范围内。");
        }

        ResourceBytes resource = readPluginResource(
            wrapper.getPluginPath(),
            UI_DIRECTORY + "/" + safeAsset,
            MAX_ASSET_BYTES
        );
        MediaType mediaType = isEntry
            ? MediaType.parseMediaType("text/javascript;charset=UTF-8")
            : MediaType.parseMediaType("text/css;charset=UTF-8");
        Resource body = resource.path() == null
            ? new ByteArrayResource(resource.bytes())
            : new FileSystemResource(resource.path());
        return new Asset(
            body,
            mediaType,
            resource.length(),
            resource.lastModified()
        );
    }

    private Descriptor readDescriptor(
        PluginWrapper wrapper
    ) throws IOException {
        ResourceBytes resource = readPluginResource(
            wrapper.getPluginPath(),
            UI_DIRECTORY + "/" + UI_MANIFEST,
            MAX_MANIFEST_BYTES
        );
        JsonNode root = objectMapper.readTree(resource.bytes());
        if (root == null || !root.isObject()) {
            throw new IOException("ui-manifest.json 根节点必须是对象。");
        }

        int schemaVersion = root.path("schemaVersion").asInt(-1);
        String pluginId = text(root, "pluginId");
        String pluginVersion = text(root, "pluginVersion");
        String format = text(root, "format");
        String globalName = text(root, "globalName");
        String entry = text(root, "entry");
        String style = nullableText(root.get("style"));
        List<String> externals = stringList(root.get("externals"));

        if (schemaVersion != 1) {
            throw new IOException("不支持的插件 UI 清单版本。");
        }
        if (!wrapper.getPluginId().equals(pluginId)) {
            throw new IOException("插件 UI 清单 ID 与 PF4J 插件 ID 不一致。");
        }
        if (!wrapper.getDescriptor().getVersion().equals(pluginVersion)) {
            throw new IOException("插件 UI 清单版本与 PF4J 插件版本不一致。");
        }
        if (!"iife".equals(format)) {
            throw new IOException("插件 UI 只允许使用 iife 格式。");
        }
        if (!globalName(pluginId).equals(globalName)) {
            throw new IOException("插件 UI 全局变量名不符合宿主协议。");
        }
        if (!ENTRY_FILE.equals(entry)) {
            throw new IOException("插件 UI 入口必须是 " + ENTRY_FILE + "。");
        }
        if (style != null && !STYLE_FILE.equals(style)) {
            throw new IOException("插件 UI 样式文件必须是 " + STYLE_FILE + "。");
        }
        if (!REQUIRED_EXTERNALS.equals(externals)) {
            throw new IOException("插件 UI 共享依赖与宿主协议不一致。");
        }

        return new Descriptor(
            pluginId,
            pluginVersion,
            globalName,
            entry,
            style,
            List.copyOf(externals),
            Set.of()
        );
    }

    private boolean manifestExists(Path pluginPath) {
        try {
            if (Files.isDirectory(pluginPath)) {
                Path pluginRoot = pluginPath.toRealPath();
                Path uiRoot = pluginRoot.resolve(UI_DIRECTORY);
                if (!Files.isDirectory(uiRoot)) {
                    return false;
                }
                Path realUiRoot = uiRoot.toRealPath();
                if (!realUiRoot.startsWith(pluginRoot)) {
                    return false;
                }
                Path manifest = realUiRoot.resolve(UI_MANIFEST);
                return Files.isRegularFile(manifest)
                    && manifest.toRealPath().startsWith(realUiRoot);
            }
            try (JarFile jar = new JarFile(pluginPath.toFile())) {
                JarEntry entry = jar.getJarEntry(
                    UI_DIRECTORY + "/" + UI_MANIFEST
                );
                return entry != null && !entry.isDirectory();
            }
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * 目录与 JAR 的真实路径都必须留在 workdir/plugins 内，拒绝通过符号链接借用外部文件。
     */
    private void requirePluginPackageBoundary(Path pluginPath)
        throws IOException {
        Path configuredRoot = workDirResolver.pluginsDir()
            .toAbsolutePath()
            .normalize();
        Path pluginAbsolute = pluginPath
            .toAbsolutePath()
            .normalize();
        if (!pluginAbsolute.startsWith(configuredRoot)
            || !Files.isDirectory(configuredRoot)) {
            throw new IOException("插件包越过 workdir/plugins 安全边界。");
        }
        Path realRoot = configuredRoot.toRealPath();
        Path realPlugin = pluginPath.toRealPath();
        if (!realPlugin.startsWith(realRoot)) {
            throw new IOException("插件包真实路径越过 workdir/plugins 安全边界。");
        }
    }

    private ResourceBytes readPluginResource(
        Path pluginPath,
        String resourceName,
        int maxBytes
    ) throws IOException {
        if (Files.isDirectory(pluginPath)) {
            Path pluginRoot = pluginPath.toRealPath();
            Path uiRoot = pluginRoot.resolve(UI_DIRECTORY);
            if (!Files.isDirectory(uiRoot)) {
                throw new IOException("插件包缺少 ui 目录。");
            }
            Path realUiRoot = uiRoot.toRealPath();
            if (!realUiRoot.startsWith(pluginRoot)) {
                throw new IOException("插件 UI 目录越过插件安全边界。");
            }
            String fileName = resourceName.substring(
                resourceName.lastIndexOf('/') + 1
            );
            Path target = realUiRoot.resolve(fileName).normalize();
            if (!Files.isRegularFile(target)) {
                throw new IOException("插件 UI 资源不存在。");
            }
            Path realTarget = target.toRealPath();
            if (!realTarget.startsWith(realUiRoot)) {
                throw new IOException("插件 UI 资源越过插件安全边界。");
            }
            long length = Files.size(realTarget);
            requireSize(length, maxBytes);
            return new ResourceBytes(
                realTarget,
                Files.readAllBytes(realTarget),
                length,
                Files.getLastModifiedTime(realTarget).toMillis()
            );
        }

        try (JarFile jar = new JarFile(pluginPath.toFile())) {
            JarEntry entry = jar.getJarEntry(resourceName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("插件 JAR 中不存在 UI 资源。");
            }
            requireSize(entry.getSize(), maxBytes);
            byte[] bytes;
            try (InputStream input = jar.getInputStream(entry)) {
                bytes = input.readNBytes(maxBytes + 1);
            }
            requireSize(bytes.length, maxBytes);
            long modified = entry.getTime() > 0
                ? entry.getTime()
                : Files.getLastModifiedTime(pluginPath).toMillis();
            return new ResourceBytes(
                null,
                bytes,
                bytes.length,
                modified
            );
        }
    }

    private void requireSize(long size, int maximum)
        throws IOException {
        if (size < 0 || size > maximum) {
            throw new IOException(
                "插件 UI 资源大小不合法，最大允许 "
                    + maximum + " 字节。"
            );
        }
    }

    private String normalizeAssetName(String assetPath)
        throws IOException {
        String value = assetPath == null
            ? ""
            : assetPath.replace('\\', '/').trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.isBlank()
            || value.contains("/")
            || value.contains("..")
            || !value.matches("[A-Za-z0-9._-]+")) {
            throw new IOException("插件 UI 资源名称不合法。");
        }
        return value;
    }

    private String requirePluginId(String pluginId)
        throws IOException {
        String value = pluginId == null ? "" : pluginId.trim();
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")) {
            throw new IOException("插件 ID 格式不正确。");
        }
        return value;
    }

    private String globalName(String pluginId) {
        return "AqPlugin_"
            + pluginId.replaceAll("[^A-Za-z0-9_$]", "_");
    }

    private String text(JsonNode root, String field) {
        String value = nullableText(root.get(field));
        return value == null ? "" : value;
    }

    private String nullableText(JsonNode value) {
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isBlank() ? null : text;
    }

    private List<String> stringList(JsonNode value)
        throws IOException {
        if (value == null || !value.isArray()) {
            throw new IOException("插件 UI externals 必须是数组。");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.asText().trim().isBlank()) {
                throw new IOException("插件 UI externals 包含无效项目。");
            }
            if (!result.add(item.asText().trim())) {
                throw new IOException("插件 UI externals 不能重复。");
            }
        }
        return List.copyOf(result);
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
            ? current.getClass().getSimpleName()
            : message;
    }

    public record Catalog(
        List<Descriptor> items,
        List<Failure> failures
    ) {
    }

    public record Descriptor(
        String pluginId,
        String pluginVersion,
        String globalName,
        String entry,
        String style,
        List<String> externals,
        Set<String> grantedPermissions
    ) {

        public Descriptor withGrantedPermissions(
            Set<String> permissions
        ) {
            return new Descriptor(
                pluginId,
                pluginVersion,
                globalName,
                entry,
                style,
                externals,
                permissions == null
                    ? Set.of()
                    : Set.copyOf(permissions)
            );
        }
    }

    public record Failure(
        String pluginId,
        String message
    ) {
    }

    public record Asset(
        Resource resource,
        MediaType mediaType,
        long contentLength,
        long lastModified
    ) {
    }

    private record ResourceBytes(
        Path path,
        byte[] bytes,
        long length,
        long lastModified
    ) {
    }
}
