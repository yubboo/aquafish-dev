package com.aquafish.core.admin.auth;

/**
 * 后台登录请求。
 *
 * 当前阶段：
 * Step 17-23：后台登录接口与管理员登录。
 */
public record AdminLoginRequest(
    String username,
    String password,
    Boolean rememberMe
) {

    public AdminLoginRequest normalized() {
        return new AdminLoginRequest(
            username == null ? "" : username.trim(),
            password == null ? "" : password,
            rememberMe != null && rememberMe
        );
    }

    public String validateMessage() {
        AdminLoginRequest safe = normalized();

        if (safe.username().isBlank()) {
            return "请输入用户名或邮箱。";
        }

        if (safe.password().isBlank()) {
            return "请输入密码。";
        }

        return null;
    }
}
