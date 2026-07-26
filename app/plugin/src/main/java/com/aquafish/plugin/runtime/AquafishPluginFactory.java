package com.aquafish.plugin.runtime;

import com.aquafish.core.config.WorkDirResolver;
import java.lang.reflect.Constructor;
import org.pf4j.Plugin;
import org.pf4j.PluginFactory;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginWrapper;

/**
 * 构造 Aquafish 插件入口并绑定私有运行上下文。
 */
final class AquafishPluginFactory implements PluginFactory {

    private final AquafishPluginApplicationContextFactory contextFactory;
    private final WorkDirResolver workDirResolver;

    AquafishPluginFactory(
        AquafishPluginApplicationContextFactory contextFactory,
        WorkDirResolver workDirResolver
    ) {
        this.contextFactory = contextFactory;
        this.workDirResolver = workDirResolver;
    }

    @Override
    public Plugin create(PluginWrapper wrapper) {
        String className = wrapper.getDescriptor().getPluginClass();
        try {
            Class<?> rawClass = wrapper.getPluginClassLoader()
                .loadClass(className);
            if (!AquafishPlugin.class.isAssignableFrom(rawClass)) {
                throw new PluginRuntimeException(
                    "插件主类必须继承 AquafishPlugin：" + className
                );
            }

            @SuppressWarnings("unchecked")
            Class<? extends AquafishPlugin> pluginClass =
                (Class<? extends AquafishPlugin>) rawClass;
            Constructor<? extends AquafishPlugin> constructor =
                pluginClass.getDeclaredConstructor(PluginWrapper.class);
            constructor.setAccessible(true);
            AquafishPlugin plugin = constructor.newInstance(wrapper);
            plugin.attach(
                contextFactory,
                new AquafishPluginContext(
                    wrapper.getPluginId(),
                    wrapper.getDescriptor().getVersion(),
                    wrapper.getPluginPath()
                        .toAbsolutePath()
                        .normalize(),
                    workDirResolver.pluginDataDir(
                        wrapper.getPluginId()
                    )
                )
            );
            return plugin;
        } catch (PluginRuntimeException error) {
            throw error;
        } catch (ReflectiveOperationException error) {
            throw new PluginRuntimeException(
                "无法构造插件主类：" + className,
                error
            );
        }
    }
}
