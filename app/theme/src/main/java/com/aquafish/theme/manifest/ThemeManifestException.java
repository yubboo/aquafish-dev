package com.aquafish.theme.manifest;

/**
 * theme.yaml 读取、语法解析或字段校验异常。
 */
public class ThemeManifestException
    extends RuntimeException {

    /**
     * 创建主题清单异常。
     *
     * @param message 错误说明
     */
    public ThemeManifestException(
        String message
    ) {
        super(message);
    }

    /**
     * 创建带原始异常的主题清单异常。
     *
     * @param message 错误说明
     * @param cause 原始异常
     */
    public ThemeManifestException(
        String message,
        Throwable cause
    ) {
        super(
            message,
            cause
        );
    }
}
