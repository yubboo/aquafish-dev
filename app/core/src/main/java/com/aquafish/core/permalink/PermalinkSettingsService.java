package com.aquafish.core.permalink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.aquafish.core.config.AquafishPathResolver;

/**
 * Aquafish 固定链接设置服务。
 *
 * 当前阶段：
 * Step 17-21-3：固定链接后端配置接口。
 *
 * 当前保存位置：
 * workdir/settings/permalink.json
 *
 * 为什么先保存到 workdir？
 * 1. 固定链接属于系统级运行配置；
 * 2. 安装早期数据库可能还没有完全准备好；
 * 3. 后续设置系统成熟后，可以迁移到数据库设置表；
 * 4. workdir 路线更接近 Halo 的外置配置思想。
 */
@Service
public class PermalinkSettingsService {

    private final ObjectMapper objectMapper;

    private final Path settingsFile;

    public PermalinkSettingsService(
        @Value("${aquafish.work-dir:workdir}") String workDir
    ) {
        this.objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

        this.settingsFile = Path
            .of(AquafishPathResolver.resolveWorkDirPath(workDir).toString())
            .resolve("settings")
            .resolve("permalink.json");
    }

    /**
     * 获取当前固定链接设置。
     *
     * 如果配置文件不存在，则返回默认 short 模式。
     */
    public PermalinkSettings getSettings() {
        if (!Files.exists(settingsFile)) {
            return PermalinkSettings.defaultSettings();
        }

        try {
            PermalinkSettings settings = objectMapper.readValue(
                settingsFile.toFile(),
                PermalinkSettings.class
            );

            return settings.normalized();
        } catch (Exception error) {
            return PermalinkSettings.defaultSettings();
        }
    }

    /**
     * 保存固定链接设置。
     *
     * @param settings 前端提交的设置
     * @return 保存后的归一化设置
     */
    public PermalinkSettings saveSettings(PermalinkSettings settings) {
        PermalinkSettings normalized = safeSettings(settings).normalized();

        try {
            Files.createDirectories(settingsFile.getParent());
            objectMapper.writeValue(settingsFile.toFile(), normalized);
            return normalized;
        } catch (IOException error) {
            throw new IllegalStateException("固定链接设置保存失败：" + error.getMessage(), error);
        }
    }

    /**
     * 生成当前固定链接设置预览。
     */
    public PermalinkPreview previewCurrent() {
        return preview(getSettings());
    }

    /**
     * 根据传入设置生成预览。
     */
    public PermalinkPreview preview(PermalinkSettings settings) {
        PermalinkSettings normalized = safeSettings(settings).normalized();

        String article = build(
            normalized.articlePattern(),
            Map.of(
                "id", "1",
                "slug", "demo",
                "key", "dev"
            )
        );

        String page = build(
            normalized.pagePattern(),
            Map.of(
                "slug", "about",
                "id", "1"
            )
        );

        String category = build(
            normalized.categoryPattern(),
            Map.of(
                "id", "1",
                "slug", "dev",
                "key", "dev"
            )
        );

        String tag = build(
            normalized.tagPattern(),
            Map.of(
                "id", "1",
                "slug", "ai",
                "key", "ai"
            )
        );

        String forum = build(
            normalized.forumPattern(),
            Map.of(
                "fid", "1",
                "id", "1",
                "slug", "general",
                "key", "general"
            )
        );

        String thread = build(
            normalized.threadPattern(),
            Map.of(
                "tid", "1",
                "id", "1",
                "slug", "demo"
            )
        );

        String user = build(
            normalized.userPattern(),
            Map.of(
                "uid", "1",
                "id", "1",
                "name", "admin",
                "username", "admin"
            )
        );

        return new PermalinkPreview(
            normalized.mode(),
            article,
            page,
            category,
            tag,
            forum,
            thread,
            user,
            List.of(
                article,
                page,
                category,
                tag,
                forum,
                thread,
                user
            )
        );
    }

    /**
     * 返回配置文件路径。
     *
     * 开发诊断用。
     */
    public String settingsFilePath() {
        return settingsFile.toString();
    }

    /**
     * 安全设置对象。
     */
    private PermalinkSettings safeSettings(PermalinkSettings settings) {
        if (settings == null) {
            return PermalinkSettings.defaultSettings();
        }

        return settings;
    }

    /**
     * 根据 pattern 和参数生成链接。
     *
     * 支持占位符：
     * {id}
     * {slug}
     * {key}
     * {fid}
     * {tid}
     * {uid}
     * {name}
     */
    private String build(String pattern, Map<String, String> values) {
        if (pattern == null || pattern.isBlank()) {
            return "";
        }

        Map<String, String> safeValues = new LinkedHashMap<>(values);

        String result = pattern.trim();

        for (Map.Entry<String, String> entry : safeValues.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return result;
    }
}
