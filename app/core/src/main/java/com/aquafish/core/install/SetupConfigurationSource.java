package com.aquafish.core.install;

/**
 * 安装参数来源。
 *
 * <p>INSTALLER 表示用户可在 Aquafish 安装器中填写；ENVIRONMENT 表示参数由
 * 1Panel、Docker Compose 或其他部署平台在服务器端注入，前端只能检测，不能修改。</p>
 */
public enum SetupConfigurationSource {
    INSTALLER("installer"),
    ENVIRONMENT("environment");

    private final String value;

    SetupConfigurationSource(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static SetupConfigurationSource fromValue(String value) {
        if (value != null && ENVIRONMENT.value.equalsIgnoreCase(value.trim())) {
            return ENVIRONMENT;
        }

        return INSTALLER;
    }
}
