package com.aquafish.theme.lifecycle;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 当前活动主题的持久化与进程内切换服务。
 *
 * <p>系统继续以 {@code workdir/application.yaml} 为重启后的事实来源。
 * 写入前保留完整备份，写入时使用同目录临时文件和原子替换；仅修改
 * {@code aquafish.theme.active}，不会重建或覆盖数据库、Redis、授权等配置。</p>
 */
@Service
public class ThemeRuntimeConfigurationService {

    private static final Pattern MAPPING_LINE = Pattern.compile(
        "^(\\s*)([A-Za-z0-9_.-]+)\\s*:\\s*(?:#.*)?$"
    );
    private static final Pattern ACTIVE_LINE = Pattern.compile(
        "^(\\s*)active\\s*:.*$"
    );
    private static final DateTimeFormatter BACKUP_TIME =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final WorkDirResolver workDirResolver;
    private final AquafishProperties properties;

    public ThemeRuntimeConfigurationService(
        WorkDirResolver workDirResolver,
        AquafishProperties properties
    ) {
        this.workDirResolver = workDirResolver;
        this.properties = properties;
    }

    /**
     * 持久化并立即采用指定主题。
     *
     * @param themeId 已验证存在的主题 ID
     */
    public synchronized void activate(String themeId) {
        String normalized = normalizeThemeId(themeId);
        Path configFile = workDirResolver.applicationYamlFile()
            .toAbsolutePath()
            .normalize();
        Path temporaryFile = configFile.resolveSibling(
            configFile.getFileName() + ".theme-" + UUID.randomUUID() + ".tmp"
        );

        try {
            workDirResolver.ensureBaseDirectories();
            String original = Files.isRegularFile(configFile)
                ? Files.readString(configFile, StandardCharsets.UTF_8)
                : "";
            String updated = updateActiveTheme(original, normalized);
            backupExistingConfig(configFile);
            Files.writeString(
                temporaryFile,
                updated,
                StandardCharsets.UTF_8
            );
            replaceAtomically(temporaryFile, configFile);
            properties.useActiveTheme(normalized);
        } catch (IOException error) {
            deleteTemporaryFile(temporaryFile);
            throw new ThemeLifecycleException(
                "THEME_CONFIG_WRITE_FAILED",
                "写入当前主题配置失败：" + safeMessage(error),
                error
            );
        } catch (RuntimeException error) {
            deleteTemporaryFile(temporaryFile);
            throw error;
        }
    }

    private String updateActiveTheme(String yaml, String themeId) {
        List<String> lines = new ArrayList<>(
            List.of((yaml == null ? "" : yaml).replace("\r\n", "\n").split("\n", -1))
        );
        int aquafishIndex = findMapping(lines, 0, lines.size(), "aquafish", -1);

        if (aquafishIndex < 0) {
            trimTrailingEmptyLines(lines);
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.add("aquafish:");
            lines.add("  theme:");
            lines.add("    active: " + quote(themeId));
            lines.add("");
            return String.join("\n", lines);
        }

        int aquafishIndent = indentation(lines.get(aquafishIndex));
        int aquafishEnd = blockEnd(lines, aquafishIndex + 1, aquafishIndent);
        int themeIndex = findMapping(
            lines,
            aquafishIndex + 1,
            aquafishEnd,
            "theme",
            aquafishIndent
        );

        if (themeIndex < 0) {
            String childIndent = " ".repeat(aquafishIndent + 2);
            lines.add(aquafishEnd, childIndent + "theme:");
            lines.add(aquafishEnd + 1, childIndent + "  active: " + quote(themeId));
            return ensureTrailingNewline(lines);
        }

        int themeIndent = indentation(lines.get(themeIndex));
        int themeEnd = blockEnd(lines, themeIndex + 1, themeIndent);
        for (int index = themeIndex + 1; index < themeEnd; index++) {
            Matcher matcher = ACTIVE_LINE.matcher(lines.get(index));
            if (matcher.matches() && matcher.group(1).length() > themeIndent) {
                lines.set(index, matcher.group(1) + "active: " + quote(themeId));
                return ensureTrailingNewline(lines);
            }
        }

        lines.add(
            themeEnd,
            " ".repeat(themeIndent + 2) + "active: " + quote(themeId)
        );
        return ensureTrailingNewline(lines);
    }

    private int findMapping(
        List<String> lines,
        int start,
        int end,
        String expectedKey,
        int parentIndent
    ) {
        for (int index = start; index < end; index++) {
            Matcher matcher = MAPPING_LINE.matcher(lines.get(index));
            if (
                matcher.matches()
                    && matcher.group(1).length() > parentIndent
                    && expectedKey.equals(matcher.group(2))
            ) {
                return index;
            }
        }
        return -1;
    }

    private int blockEnd(List<String> lines, int start, int parentIndent) {
        for (int index = start; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            if (indentation(line) <= parentIndent) {
                return index;
            }
        }
        return lines.size();
    }

    private int indentation(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        if (count < line.length() && line.charAt(count) == '\t') {
            throw new ThemeLifecycleException(
                "THEME_CONFIG_FORMAT_UNSUPPORTED",
                "application.yaml 的 aquafish 配置不能使用制表符缩进。"
            );
        }
        return count;
    }

    private void backupExistingConfig(Path configFile) throws IOException {
        if (!Files.isRegularFile(configFile)) {
            return;
        }
        Path backupDirectory = workDirResolver.backupsDir()
            .resolve("theme-config")
            .toAbsolutePath()
            .normalize();
        Files.createDirectories(backupDirectory);
        Path backup = backupDirectory.resolve(
            "application-"
                + LocalDateTime.now().format(BACKUP_TIME)
                + "-"
                + UUID.randomUUID()
                + ".yaml.bak"
        );
        Files.copy(configFile, backup);
    }

    private void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String normalizeThemeId(String value) {
        String normalized = value == null
            ? ""
            : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new ThemeLifecycleException(
                "THEME_ID_INVALID",
                "非法主题 ID：" + normalized
            );
        }
        return normalized;
    }

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private String ensureTrailingNewline(List<String> lines) {
        if (lines.isEmpty() || !lines.get(lines.size() - 1).isEmpty()) {
            lines.add("");
        }
        return String.join("\n", lines);
    }

    private void trimTrailingEmptyLines(List<String> lines) {
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
    }

    private void deleteTemporaryFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 原始配置写入异常优先返回；临时文件位于实例 workdir，不泄露到响应。
        }
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank()
            ? "未知文件系统错误"
            : message;
    }
}
