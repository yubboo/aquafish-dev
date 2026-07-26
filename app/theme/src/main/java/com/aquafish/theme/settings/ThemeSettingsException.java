package com.aquafish.theme.settings;

/**
 * 主题设置清单、设置值校验或实例设置文件读写失败。
 *
 * <p>异常代码会原样交给后台 API，用于区分主题不存在、清单损坏和字段值非法；
 * 绝对路径、堆栈和底层文件系统信息不会返回给浏览器。</p>
 */
public class ThemeSettingsException extends RuntimeException {

    private final String code;

    public ThemeSettingsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ThemeSettingsException(
        String code,
        String message,
        Throwable cause
    ) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
