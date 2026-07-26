package com.aquafish.core.operation;

/**
 * Aquafish 扩展操作键。
 *
 * <p>
 * 当前安装流程使用全局主题操作键。
 * 后续主题升级、删除和启用可以继续使用全局键，
 * 或在确认操作完全独立后使用主题级键。
 * </p>
 */
public final class ExtensionOperationKeys {

    /**
     * 全局主题操作。
     */
    public static final String
        THEME_GLOBAL = "theme:global";

    /**
     * 全局插件操作。
     */
    public static final String
        PLUGIN_GLOBAL = "plugin:global";

    /**
     * 工具类不允许创建实例。
     */
    private ExtensionOperationKeys() {
    }

    /**
     * 创建单主题操作键。
     *
     * @param themeId 主题 ID
     * @return 主题操作键
     */
    public static String theme(
        String themeId
    ) {
        return resourceKey(
            "theme",
            themeId
        );
    }

    /**
     * 创建单插件操作键。
     *
     * @param pluginId 插件 ID
     * @return 插件操作键
     */
    public static String plugin(
        String pluginId
    ) {
        return resourceKey(
            "plugin",
            pluginId
        );
    }

    /**
     * 创建安全资源操作键。
     */
    private static String resourceKey(
        String resourceType,
        String resourceId
    ) {
        if (
            resourceId == null
            || resourceId.isBlank()
        ) {
            throw new IllegalArgumentException(
                "扩展资源 ID 不能为空。"
            );
        }

        String normalizedId =
            resourceId
                .trim()
                .toLowerCase();

        if (
            !normalizedId.matches(
                "[a-z][a-z0-9-]{0,63}"
            )
        ) {
            throw new IllegalArgumentException(
                "非法扩展资源 ID："
                    + normalizedId
            );
        }

        return resourceType
            + ":"
            + normalizedId;
    }
}
