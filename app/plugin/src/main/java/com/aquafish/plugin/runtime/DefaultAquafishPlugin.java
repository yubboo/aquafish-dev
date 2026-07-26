package com.aquafish.plugin.runtime;

import org.pf4j.PluginWrapper;

/**
 * 没有自定义生命周期入口时使用的默认插件。
 *
 * <p>它仍会创建独立 Spring 子上下文并装配 {@code springComponents}，因此纯配置型插件
 * 不需要额外编写空的主类。</p>
 */
public final class DefaultAquafishPlugin extends AquafishPlugin {

    public DefaultAquafishPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }
}
