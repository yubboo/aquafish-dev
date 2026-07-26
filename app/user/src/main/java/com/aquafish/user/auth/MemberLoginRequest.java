package com.aquafish.user.auth;

/**
 * 前台会员登录请求。
 *
 * <p>loginName 支持用户名或邮箱；原始密码只在本次 BCrypt 校验期间存在，
 * 不写入日志、响应和会话表。</p>
 */
public record MemberLoginRequest(
    String loginName,
    String password,
    boolean rememberMe
) {

    /**
     * 清理登录名两端空白，但不修改密码内容。
     */
    public MemberLoginRequest normalized() {
        return new MemberLoginRequest(
            loginName == null ? "" : loginName.strip(),
            password == null ? "" : password,
            rememberMe
        );
    }

    /**
     * 按数据库字段和拒绝超大请求原则执行基础校验。
     */
    public String validateMessage() {
        if (loginName == null || loginName.isBlank()) {
            return "用户名或邮箱不能为空。";
        }
        if (loginName.length() > 191) {
            return "用户名或邮箱长度不正确。";
        }
        if (password == null || password.isEmpty()) {
            return "密码不能为空。";
        }
        if (password.length() > 256) {
            return "密码长度不正确。";
        }
        return null;
    }
}
