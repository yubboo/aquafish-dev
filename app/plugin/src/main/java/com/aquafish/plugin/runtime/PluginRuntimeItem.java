package com.aquafish.plugin.runtime;

import java.util.List;

/**
 * 后台插件页使用的不可变运行状态快照。
 */
public record PluginRuntimeItem(
    String pluginId,
    String name,
    String version,
    String provider,
    String description,
    String packageType,
    String state,
    boolean started,
    String classLoader,
    List<PluginDependencyItem> dependencies,
    String error
) {

    public record PluginDependencyItem(
        String pluginId,
        String versionRequirement,
        boolean optional,
        boolean present,
        String state
    ) {
    }
}
