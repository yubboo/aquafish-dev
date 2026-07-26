package com.aquafish.core.admin.auth;

/**
 * 后台登录请求的网络环境信息。
 *
 * 密码等敏感信息绝对不能存入该对象。
 */
public record AdminLoginMetadata(
    String clientIp,
    String remoteAddress,
    String xForwardedFor,
    String xRealIp,
    String userAgent
) {

    public static AdminLoginMetadata empty() {
        return new AdminLoginMetadata(
            "",
            "",
            "",
            "",
            ""
        );
    }

    /**
     * 限制数据库日志字段长度，避免异常请求头撑爆数据库字段。
     */
    public AdminLoginMetadata normalized() {
        return new AdminLoginMetadata(
            normalizeText(clientIp, 45),
            normalizeText(remoteAddress, 45),
            normalizeText(xForwardedFor, 500),
            normalizeText(xRealIp, 45),
            normalizeText(userAgent, 500)
        );
    }

    private static String normalizeText(
        String value,
        int maxLength
    ) {
        if (value == null) {
            return "";
        }

        String result = value
            .replace("\r", "")
            .replace("\n", "")
            .trim();

        if (result.length() <= maxLength) {
            return result;
        }

        return result.substring(0, maxLength);
    }
}
