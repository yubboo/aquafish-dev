package com.aquafish.plugin.runtime;

import java.nio.file.Path;

/**
 * 提供给插件的只读运行上下文。
 *
 * @param pluginId 插件稳定标识
 * @param version 当前插件版本
 * @param pluginPath PF4J 实际加载的 JAR 或开发目录
 * @param dataDirectory 插件唯一可写的持久化数据目录
 */
public record AquafishPluginContext(
    String pluginId,
    String version,
    Path pluginPath,
    Path dataDirectory
) {
}
