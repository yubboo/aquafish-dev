package com.aquafish.theme.lifecycle;

/**
 * 后台主题启用、升级和卸载的最小可公开结果。
 *
 * <p>结果不包含服务器绝对路径、临时目录或备份目录。</p>
 */
public record ThemeLifecycleResult(
    String action,
    String themeId,
    String version,
    boolean active,
    String message
) {

    public ThemeLifecycleResult {
        action = requireText(action, "unknown");
        themeId = requireText(themeId, "");
        version = requireText(version, "");
        message = requireText(message, "主题操作完成。");
    }

    private static String requireText(String value, String fallback) {
        return value == null || value.isBlank()
            ? fallback
            : value.trim();
    }
}
