package com.aquafish.plugin.runtime;

import java.util.List;
import java.util.Set;

/**
 * 后台插件管理页的完整状态。
 */
public record PluginManagementSnapshot(
    boolean runtimeDirectoryReady,
    boolean loaderAvailable,
    boolean lifecycleAvailable,
    int candidateCount,
    List<PluginRuntimeItem> items,
    Set<String> enabledPluginIds,
    String message
) {
}
