package com.aquafish.theme.lifecycle;

/**
 * 主题安装后生命周期操作的稳定业务异常。
 *
 * <p>后台接口使用 code 决定 HTTP 状态和提示，不依赖可能调整的中文说明。</p>
 */
public class ThemeLifecycleException extends RuntimeException {

    private final String code;

    public ThemeLifecycleException(String code, String message) {
        this(code, message, null);
    }

    public ThemeLifecycleException(
        String code,
        String message,
        Throwable cause
    ) {
        super(requireText(message, "主题操作失败。"), cause);
        this.code = requireText(code, "THEME_OPERATION_FAILED");
    }

    public String code() {
        return code;
    }

    private static String requireText(String value, String fallback) {
        return value == null || value.isBlank()
            ? fallback
            : value.trim();
    }
}
