package com.aquafish.user.auth;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 前台用户自主注册请求。
 *
 * <p>用户名用于稳定登录，不允许空格和容易混淆的特殊字符；邮箱统一转为小写；
 * 密码原文只在本次请求与 BCrypt 计算期间存在，不写入日志、响应或数据库。</p>
 */
public record MemberRegistrationRequest(
    String username,
    String email,
    String displayName,
    String password,
    String confirmPassword,
    boolean acceptedTerms
) {

    private static final Pattern USERNAME_PATTERN = Pattern.compile(
        "^[\\p{L}\\p{N}_-]{1,64}$"
    );
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
    );

    /**
     * 标准化普通文本，但绝不修剪或修改密码内容。
     */
    public MemberRegistrationRequest normalized() {
        String safeUsername = username == null ? "" : username.strip();
        String safeDisplayName = displayName == null ? "" : displayName.strip();
        return new MemberRegistrationRequest(
            safeUsername,
            email == null ? "" : email.strip().toLowerCase(Locale.ROOT),
            safeDisplayName.isBlank() ? safeUsername : safeDisplayName,
            password == null ? "" : password,
            confirmPassword == null ? "" : confirmPassword,
            acceptedTerms
        );
    }

    /**
     * 返回可直接展示给注册用户的第一条校验消息；合法时返回 {@code null}。
     */
    public String validateMessage() {
        if (!USERNAME_PATTERN.matcher(username == null ? "" : username).matches()) {
            return "用户名必须为 1 至 64 位中文、字母、数字、下划线或短横线。";
        }
        if (email == null || email.length() > 191
            || !EMAIL_PATTERN.matcher(email).matches()) {
            return "请输入有效的邮箱地址。";
        }
        if (displayName == null || displayName.isBlank()
            || displayName.length() > 100) {
            return "显示名称不能为空且不能超过 100 个字符。";
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            return "密码长度必须为 8 至 72 个字符。";
        }
        if (!password.equals(confirmPassword)) {
            return "两次输入的密码不一致。";
        }
        if (!acceptedTerms) {
            return "请先阅读并同意用户协议与隐私政策。";
        }
        return null;
    }
}
