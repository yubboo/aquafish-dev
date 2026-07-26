package com.aquafish.core.install;

/**
 * 安装阶段管理员账号创建请求。
 *
 * 当前阶段：
 * Step 17-22-5：初始化管理员账号。
 *
 * 注意：
 * 1. password 只用于创建时加密；
 * 2. 返回结果里绝不能返回 password；
 * 3. 管理员创建成功后仍然不写 install.lock；
 * 4. install.lock 要等最终安装完成步骤再写。
 */
public record SetupAdminAccountRequest(
    String username,
    String email,
    String password,
    String displayName
) {

    public SetupAdminAccountRequest normalized() {
        String safeUsername = username == null
            ? ""
            : username.strip();
        String safeEmail = email == null
            ? ""
            : email.strip();
        String safeDisplayName =
            textOrDefault(
                displayName,
                safeUsername
            );

        return new SetupAdminAccountRequest(
            safeUsername,
            safeEmail,
            password == null ? "" : password,
            safeDisplayName
        );
    }

    public String validateMessage() {
        SetupAdminAccountRequest safe = normalized();

        if (!safe.username().matches("^[\\p{L}\\p{N}_-]{1,64}$")) {
            return "管理员用户名必须为 1-64 位中文、字母、数字、下划线或短横线。";
        }

        if (!safe.email().isBlank() && !safe.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return "管理员邮箱格式不正确。";
        }

        if (safe.password().length() < 8) {
            return "管理员密码长度不能少于 8 位。";
        }

        if (safe.password().length() > 128) {
            return "管理员密码长度不能超过 128 位。";
        }

        return null;
    }

    private static String textOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value.trim();
    }
}
