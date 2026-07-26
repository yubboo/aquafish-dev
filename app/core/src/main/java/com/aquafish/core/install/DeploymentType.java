package com.aquafish.core.install;

/**
 * Aquafish 对外展示的部署类型。
 *
 * <p>部署类型只决定安装向导的说明和默认步骤；数据库是否允许在页面编辑，
 * 必须继续由 {@link SetupConfigurationSource} 决定，不能仅凭“运行在容器中”猜测。</p>
 */
public enum DeploymentType {
    ARCHIVE("archive", "分发包部署"),
    DOCKER("docker", "Docker 一键部署"),
    ONEPANEL("onepanel", "1Panel 应用部署");

    private final String value;
    private final String label;

    DeploymentType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    public static DeploymentType fromValue(String value) {
        if (value != null) {
            for (DeploymentType type : values()) {
                if (type.value.equalsIgnoreCase(value.trim())) {
                    return type;
                }
            }
        }

        return ARCHIVE;
    }
}
