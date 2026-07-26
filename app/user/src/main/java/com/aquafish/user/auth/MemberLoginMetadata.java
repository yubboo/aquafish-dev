package com.aquafish.user.auth;

/**
 * 前台登录审计元数据。
 *
 * <p>IP 地址由统一的可信代理解析器生成。只有 TCP 直连来源位于
 * 可信代理清单时，系统才会采用 X-Forwarded-For 或 X-Real-IP；
 * 本记录本身不再解析或信任浏览器请求头。</p>
 */
public record MemberLoginMetadata(
    String ipAddress,
    String userAgent
) {

    public static MemberLoginMetadata empty() {
        return new MemberLoginMetadata("", "");
    }

    /**
     * 清除换行并限制为数据库字段长度，防止日志注入和超长写入。
     */
    public MemberLoginMetadata normalized() {
        return new MemberLoginMetadata(
            clean(ipAddress, 45),
            clean(userAgent, 500)
        );
    }

    private static String clean(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String result = value
            .replace("\r", "")
            .replace("\n", "")
            .strip();
        return result.length() <= maxLength
            ? result
            : result.substring(0, maxLength);
    }
}
