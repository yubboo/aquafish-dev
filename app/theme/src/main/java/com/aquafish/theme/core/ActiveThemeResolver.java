package com.aquafish.theme.core;

import com.aquafish.core.config.AquafishProperties;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 当前启用主题解析器。
 *
 * 当前阶段：
 * Step 17-20-3：从 application.yaml 读取当前启用主题。
 *
 * 配置来源：
 *
 * workdir/application.yaml
 *
 * 配置项：
 *
 * aquafish:
 *   theme:
 *     active: default
 *
 * 当前职责：
 * 1. 读取当前启用主题名称；
 * 2. 在 ThemeScanner 扫描结果中查找当前主题；
 * 3. 后续给模板渲染系统提供当前主题。
 */
@Component
public class ActiveThemeResolver {

    /**
     * Aquafish 运行配置。
     */
    private final AquafishProperties properties;

    /**
     * 主题扫描器。
     */
    private final ThemeScanner themeScanner;

    public ActiveThemeResolver(
        AquafishProperties properties,
        ThemeScanner themeScanner
    ) {
        this.properties = properties;
        this.themeScanner = themeScanner;
    }

    /**
     * 获取当前启用主题名称。
     *
     * 如果配置为空，默认使用 default。
     *
     * @return 当前启用主题名称
     */
    public String activeThemeName() {
        String value = properties.activeTheme();

        if (value == null || value.isBlank()) {
            return "default";
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 获取当前启用主题描述。
     *
     * 如果 workdir/themes 里找不到当前主题，则返回 Optional.empty()。
     *
     * @return 当前启用主题
     */
    public Optional<ThemeDescriptor> activeTheme() {
        String activeThemeName = activeThemeName();

        return themeScanner.scanInstalledThemes()
            .stream()
            .filter(theme -> theme.name().equals(activeThemeName))
            .findFirst();
    }

    /**
     * 获取当前启用主题。
     *
     * 如果找不到，直接抛异常。
     *
     * 后续模板渲染时，如果当前主题不存在，应该及时暴露问题，
     * 或者在更高层做 default / fallback 兜底。
     *
     * @return 当前启用主题
     */
    public ThemeDescriptor requireActiveTheme() {
        return activeTheme().orElseThrow(() -> new IllegalStateException(
            "当前启用主题不存在：" + activeThemeName()
        ));
    }
}