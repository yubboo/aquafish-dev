package com.aquafish.theme.settings;

import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.theme.core.ActiveThemeResolver;
import com.aquafish.theme.core.ThemeDescriptor;
import com.aquafish.theme.core.ThemeScanner;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 已安装主题的设置清单解析与实例值持久化服务。
 *
 * <p>主题包中的 {@code settings.yaml} 只声明字段、控件类型和默认值，运行期
 * 设置保存到 {@code workdir/settings/themes/<themeId>.json}。因此后台改设置
 * 不会篡改主题源码，主题升级也不会覆盖站点自己的配置。</p>
 */
@Service
public class ThemeSettingsService {

    private static final int MAX_FIELDS = 100;
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_LONG_TEXT_LENGTH = 65535;
    private static final Pattern SETTING_KEY =
        Pattern.compile("^[a-z][a-zA-Z0-9._-]{0,63}$");
    private static final Pattern COLOR_VALUE =
        Pattern.compile("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$");
    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "text",
        "textarea",
        "select",
        "boolean",
        "number",
        "color",
        "image"
    );

    private final ThemeScanner themeScanner;
    private final ActiveThemeResolver activeThemeResolver;
    private final WorkDirResolver workDirResolver;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public ThemeSettingsService(
        ThemeScanner themeScanner,
        ActiveThemeResolver activeThemeResolver,
        WorkDirResolver workDirResolver
    ) {
        this.themeScanner = themeScanner;
        this.activeThemeResolver = activeThemeResolver;
        this.workDirResolver = workDirResolver;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.jsonMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 读取指定主题的字段定义和当前有效值。
     *
     * @param themeId 已安装主题 ID
     * @return 可直接交给后台动态表单的只读快照
     */
    public synchronized ThemeSettingsSnapshot load(String themeId) {
        ThemeDescriptor theme = requireInstalled(themeId);
        if (!theme.settingsYamlExists()) {
            return new ThemeSettingsSnapshot(
                theme.name(),
                theme.title(),
                false,
                false,
                List.of(),
                Map.of()
            );
        }

        List<ThemeSettingField> fields = parseSchema(safeSchemaFile(theme));
        Path valuesFile = valuesFile(theme.name());
        Map<String, Object> persisted = readPersistedValues(valuesFile);
        Map<String, Object> values = normalizeValues(
            fields,
            persisted,
            false
        );
        return new ThemeSettingsSnapshot(
            theme.name(),
            theme.title(),
            true,
            Files.isRegularFile(valuesFile, LinkOption.NOFOLLOW_LINKS),
            fields,
            values
        );
    }

    /**
     * 保存完整主题设置。
     *
     * <p>未知字段和非法值会被拒绝；缺少的字段恢复清单默认值。写入采用同目录
     * 临时文件加原子替换，避免进程中断留下半个 JSON 文件。</p>
     */
    public synchronized ThemeSettingsSnapshot save(
        String themeId,
        Map<String, Object> submittedValues
    ) {
        ThemeDescriptor theme = requireInstalled(themeId);
        if (!theme.settingsYamlExists()) {
            throw new ThemeSettingsException(
                "THEME_SETTINGS_NOT_AVAILABLE",
                "主题没有声明 settings.yaml，不能保存设置。"
            );
        }

        List<ThemeSettingField> fields = parseSchema(safeSchemaFile(theme));
        Map<String, Object> submitted = submittedValues == null
            ? Map.of()
            : submittedValues;
        rejectUnknownFields(fields, submitted);
        Map<String, Object> normalized = normalizeValues(
            fields,
            submitted,
            true
        );
        writeValues(valuesFile(theme.name()), normalized);
        return load(theme.name());
    }

    /**
     * 删除实例覆盖值，恢复主题包默认值。
     */
    public synchronized ThemeSettingsSnapshot reset(String themeId) {
        ThemeDescriptor theme = requireInstalled(themeId);
        try {
            Files.deleteIfExists(valuesFile(theme.name()));
        } catch (IOException error) {
            throw new ThemeSettingsException(
                "THEME_SETTINGS_RESET_FAILED",
                "主题设置恢复默认值失败。",
                error
            );
        }
        return load(theme.name());
    }

    /**
     * 为公共页面读取活动主题设置。
     *
     * <p>访客页面不能因为管理员手工破坏了设置 JSON 就整体 500；该入口在异常时
     * 返回空设置，后台专用 {@link #load(String)} 仍会报告真实错误。</p>
     */
    public ThemeSettingsSnapshot loadActiveSafely() {
        String activeThemeId = activeThemeResolver.activeThemeName();
        try {
            return load(activeThemeId);
        } catch (RuntimeException ignored) {
            return new ThemeSettingsSnapshot(
                activeThemeId,
                activeThemeId,
                false,
                false,
                List.of(),
                Map.of()
            );
        }
    }

    private List<ThemeSettingField> parseSchema(Path schemaFile) {
        final JsonNode root;
        try {
            root = yamlMapper.readTree(schemaFile.toFile());
        } catch (IOException error) {
            throw new ThemeSettingsException(
                "THEME_SETTINGS_SCHEMA_INVALID",
                "主题 settings.yaml 无法解析。",
                error
            );
        }

        JsonNode settingsNode = root == null ? null : root.get("settings");
        if (settingsNode == null || !settingsNode.isObject()) {
            throw new ThemeSettingsException(
                "THEME_SETTINGS_SCHEMA_INVALID",
                "主题 settings.yaml 必须包含 settings 对象。"
            );
        }

        List<ThemeSettingField> fields = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> iterator =
            settingsNode.fields();
        while (iterator.hasNext()) {
            if (fields.size() >= MAX_FIELDS) {
                throw new ThemeSettingsException(
                    "THEME_SETTINGS_SCHEMA_TOO_LARGE",
                    "单个主题最多声明 " + MAX_FIELDS + " 个设置项。"
                );
            }
            Map.Entry<String, JsonNode> entry = iterator.next();
            fields.add(parseField(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(fields);
    }

    private ThemeSettingField parseField(String key, JsonNode node) {
        if (key == null || !SETTING_KEY.matcher(key).matches()) {
            throw new ThemeSettingsException(
                "THEME_SETTING_KEY_INVALID",
                "主题设置字段名非法：" + safeText(key, "空字段")
            );
        }
        if (node == null || !node.isObject()) {
            throw new ThemeSettingsException(
                "THEME_SETTING_FIELD_INVALID",
                "主题设置项 " + key + " 必须是对象。"
            );
        }

        String type = safeText(node.path("type").asText(), "text")
            .toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new ThemeSettingsException(
                "THEME_SETTING_TYPE_UNSUPPORTED",
                "主题设置项 " + key + " 使用了不支持的类型：" + type
            );
        }

        List<ThemeSettingOption> options = parseOptions(
            key,
            node.get("options")
        );
        if ("select".equals(type) && options.isEmpty()) {
            throw new ThemeSettingsException(
                "THEME_SETTING_OPTIONS_REQUIRED",
                "下拉设置项 " + key + " 至少需要一个 options 选项。"
            );
        }

        Object rawDefault = node.has("default")
            ? scalarValue(key, node.get("default"))
            : fallbackValue(type, options);
        ThemeSettingField provisional = new ThemeSettingField(
            key,
            safeText(node.path("label").asText(), key),
            safeText(node.path("description").asText(), ""),
            type,
            rawDefault,
            options
        );
        Object normalizedDefault = normalizeValue(
            provisional,
            rawDefault
        );
        return new ThemeSettingField(
            key,
            provisional.label(),
            provisional.description(),
            type,
            normalizedDefault,
            options
        );
    }

    private List<ThemeSettingOption> parseOptions(
        String fieldKey,
        JsonNode optionsNode
    ) {
        if (optionsNode == null || optionsNode.isNull()) {
            return List.of();
        }
        if (!optionsNode.isArray()) {
            throw new ThemeSettingsException(
                "THEME_SETTING_OPTIONS_INVALID",
                "主题设置项 " + fieldKey + " 的 options 必须是数组。"
            );
        }
        List<ThemeSettingOption> options = new ArrayList<>();
        for (JsonNode optionNode : optionsNode) {
            if (!optionNode.isObject() || !optionNode.has("value")) {
                throw new ThemeSettingsException(
                    "THEME_SETTING_OPTION_INVALID",
                    "主题设置项 " + fieldKey + " 存在无效选项。"
                );
            }
            Object value = scalarValue(fieldKey, optionNode.get("value"));
            options.add(new ThemeSettingOption(
                safeText(optionNode.path("label").asText(), String.valueOf(value)),
                value
            ));
        }
        return List.copyOf(options);
    }

    private Map<String, Object> normalizeValues(
        List<ThemeSettingField> fields,
        Map<String, Object> source,
        boolean submitted
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ThemeSettingField field : fields) {
            Object raw = source != null && source.containsKey(field.key())
                ? source.get(field.key())
                : field.defaultValue();
            try {
                values.put(field.key(), normalizeValue(field, raw));
            } catch (ThemeSettingsException error) {
                if (submitted) {
                    throw error;
                }
                values.put(field.key(), field.defaultValue());
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private Object normalizeValue(ThemeSettingField field, Object raw) {
        return switch (field.type()) {
            case "boolean" -> normalizeBoolean(field.key(), raw);
            case "number" -> normalizeNumber(field.key(), raw);
            case "color" -> normalizeColor(field.key(), raw);
            case "image" -> normalizeImage(field.key(), raw);
            case "select" -> normalizeSelect(field, raw);
            case "textarea" -> normalizeTextValue(
                field.key(),
                raw,
                MAX_LONG_TEXT_LENGTH,
                false
            );
            default -> normalizeTextValue(
                field.key(),
                raw,
                MAX_TEXT_LENGTH,
                true
            );
        };
    }

    private Boolean normalizeBoolean(String key, Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.valueOf(text);
        }
        throw invalidValue(key, "必须是布尔值。");
    }

    private Number normalizeNumber(String key, Object raw) {
        try {
            BigDecimal number = raw instanceof Number
                ? new BigDecimal(String.valueOf(raw))
                : new BigDecimal(String.valueOf(raw).trim());
            BigDecimal normalized = number.stripTrailingZeros();
            return normalized.scale() <= 0
                ? normalized.longValueExact()
                : normalized.doubleValue();
        } catch (Exception error) {
            throw invalidValue(key, "必须是有效数字。");
        }
    }

    private String normalizeColor(String key, Object raw) {
        String value = normalizeTextValue(
            key,
            raw,
            9,
            true
        );
        if (!COLOR_VALUE.matcher(value).matches()) {
            throw invalidValue(key, "必须是 #RRGGBB 或 #RRGGBBAA 颜色。");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String normalizeImage(String key, Object raw) {
        String value = normalizeTextValue(
            key,
            raw,
            MAX_TEXT_LENGTH,
            true
        );
        if (value.isBlank()) {
            return "";
        }
        boolean allowedRoot = value.startsWith("/theme-assets/")
            || value.startsWith("/uploads/");
        if (
            !allowedRoot
                || value.contains("..")
                || value.contains("\\")
                || value.startsWith("//")
        ) {
            throw invalidValue(
                key,
                "只能使用 /theme-assets/ 或 /uploads/ 下的站内图片。"
            );
        }
        return value;
    }

    private Object normalizeSelect(ThemeSettingField field, Object raw) {
        String candidate = raw == null ? "" : String.valueOf(raw);
        return field.options().stream()
            .map(ThemeSettingOption::value)
            .filter(value -> String.valueOf(value).equals(candidate))
            .findFirst()
            .orElseThrow(() -> invalidValue(
                field.key(),
                "不在允许的下拉选项中。"
            ));
    }

    private String normalizeTextValue(
        String key,
        Object raw,
        int maxLength,
        boolean trim
    ) {
        String value = raw == null ? "" : String.valueOf(raw);
        if (trim) {
            value = value.trim();
        }
        if (value.length() > maxLength) {
            throw invalidValue(key, "长度不能超过 " + maxLength + " 个字符。");
        }
        return value;
    }

    private void rejectUnknownFields(
        List<ThemeSettingField> fields,
        Map<String, Object> submitted
    ) {
        Set<String> allowed = fields.stream()
            .map(ThemeSettingField::key)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        submitted.keySet().stream()
            .filter(key -> !allowed.contains(key))
            .findFirst()
            .ifPresent(key -> {
                throw new ThemeSettingsException(
                    "THEME_SETTING_UNKNOWN",
                    "主题没有声明设置项：" + key
                );
            });
    }

    private Map<String, Object> readPersistedValues(Path valuesFile) {
        if (!Files.isRegularFile(valuesFile, LinkOption.NOFOLLOW_LINKS)) {
            return Map.of();
        }
        try {
            Map<String, Object> values = jsonMapper.readValue(
                valuesFile.toFile(),
                new TypeReference<Map<String, Object>>() {
                }
            );
            return values == null ? Map.of() : values;
        } catch (IOException error) {
            throw new ThemeSettingsException(
                "THEME_SETTINGS_FILE_INVALID",
                "主题实例设置文件损坏，请在后台恢复默认值。",
                error
            );
        }
    }

    private void writeValues(Path valuesFile, Map<String, Object> values) {
        Path parent = valuesFile.getParent();
        Path temporary = parent.resolve(
            valuesFile.getFileName() + "." + UUID.randomUUID() + ".tmp"
        );
        try {
            Files.createDirectories(parent);
            jsonMapper.writeValue(temporary.toFile(), values);
            try {
                Files.move(
                    temporary,
                    valuesFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                    temporary,
                    valuesFile,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException error) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // 临时文件清理失败不能覆盖原始保存错误。
            }
            throw new ThemeSettingsException(
                "THEME_SETTINGS_SAVE_FAILED",
                "主题设置保存失败。",
                error
            );
        }
    }

    private ThemeDescriptor requireInstalled(String themeId) {
        String normalized = themeId == null
            ? ""
            : themeId.trim().toLowerCase(Locale.ROOT);
        return themeScanner.scanInstalledThemes().stream()
            .filter(theme -> theme.name().equals(normalized))
            .findFirst()
            .orElseThrow(() -> new ThemeSettingsException(
                "THEME_NOT_FOUND",
                "没有找到已安装主题：" + normalized
            ));
    }

    private Path safeSchemaFile(ThemeDescriptor theme) {
        Path themeDirectory = Path.of(theme.themeDir())
            .toAbsolutePath()
            .normalize();
        Path schemaFile = Path.of(theme.settingsYamlFile())
            .toAbsolutePath()
            .normalize();
        if (
            !schemaFile.startsWith(themeDirectory)
                || schemaFile.getParent() == null
                || !schemaFile.getParent().equals(themeDirectory)
                || Files.isSymbolicLink(schemaFile)
                || !Files.isRegularFile(schemaFile, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw new ThemeSettingsException(
                "THEME_SETTINGS_SCHEMA_UNSAFE",
                "主题 settings.yaml 路径不安全或文件不存在。"
            );
        }
        return schemaFile;
    }

    private Path valuesFile(String themeId) {
        workDirResolver.ensureBaseDirectories();
        return workDirResolver.workDir()
            .resolve("settings")
            .resolve("themes")
            .resolve(themeId + ".json")
            .toAbsolutePath()
            .normalize();
    }

    private Object scalarValue(String key, JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (!node.isValueNode()) {
            throw new ThemeSettingsException(
                "THEME_SETTING_VALUE_INVALID",
                "主题设置项 " + key + " 的值必须是字符串、数字或布尔值。"
            );
        }
        return jsonMapper.convertValue(node, Object.class);
    }

    private Object fallbackValue(
        String type,
        List<ThemeSettingOption> options
    ) {
        return switch (type) {
            case "boolean" -> false;
            case "number" -> 0;
            case "select" -> options.get(0).value();
            default -> "";
        };
    }

    private ThemeSettingsException invalidValue(
        String key,
        String details
    ) {
        return new ThemeSettingsException(
            "THEME_SETTING_VALUE_INVALID",
            "主题设置项 " + key + " " + details
        );
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /**
     * 后台表单可选项。
     */
    public record ThemeSettingOption(String label, Object value) {

        public ThemeSettingOption {
            label = label == null ? "" : label.trim();
        }
    }

    /**
     * settings.yaml 中一个经过校验的动态字段。
     */
    public record ThemeSettingField(
        String key,
        String label,
        String description,
        String type,
        Object defaultValue,
        List<ThemeSettingOption> options
    ) {

        public ThemeSettingField {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /**
     * 某个主题的设置清单与当前有效值。
     */
    public record ThemeSettingsSnapshot(
        String themeId,
        String title,
        boolean available,
        boolean customized,
        List<ThemeSettingField> fields,
        Map<String, Object> values
    ) {

        public ThemeSettingsSnapshot {
            fields = fields == null ? List.of() : List.copyOf(fields);
            values = values == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }
}
