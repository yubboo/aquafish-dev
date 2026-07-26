package com.aquafish.plugin.runtime;

import com.aquafish.plugin.manifest.AquafishPluginDescriptor;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * 为每个 PF4J 插件创建独立 Spring 子上下文。
 */
final class AquafishPluginApplicationContextFactory {

    private final GenericApplicationContext sharedContext;

    AquafishPluginApplicationContextFactory(
        GenericApplicationContext sharedContext
    ) {
        this.sharedContext = sharedContext;
    }

    ConfigurableApplicationContext create(
        PluginWrapper wrapper,
        AquafishPlugin plugin,
        AquafishPluginContext pluginContext
    ) {
        try {
            Files.createDirectories(pluginContext.dataDirectory());
        } catch (IOException error) {
            throw new PluginRuntimeException(
                "创建插件数据目录失败：" + pluginContext.dataDirectory(),
                error
            );
        }

        ClassLoader pluginClassLoader = wrapper.getPluginClassLoader();
        DefaultListableBeanFactory beanFactory =
            new DefaultListableBeanFactory();
        beanFactory.setBeanClassLoader(pluginClassLoader);

        AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(beanFactory);
        context.setId("aquafish-plugin-" + wrapper.getPluginId());
        context.setParent(sharedContext);
        context.setClassLoader(pluginClassLoader);
        context.setResourceLoader(
            new DefaultResourceLoader(pluginClassLoader)
        );

        beanFactory.registerSingleton("pluginWrapper", wrapper);
        beanFactory.registerSingleton("aquafishPlugin", plugin);
        beanFactory.registerSingleton(
            "aquafishPluginContext",
            pluginContext
        );

        Set<String> componentNames = new LinkedHashSet<>();
        if (wrapper.getDescriptor()
            instanceof AquafishPluginDescriptor descriptor) {
            componentNames.addAll(descriptor.springComponents());
        }
        componentNames.addAll(
            wrapper.getPluginManager()
                .getExtensionClassNames(wrapper.getPluginId())
        );
        componentNames.stream()
            .map(name -> loadComponent(pluginClassLoader, name))
            .forEach(context::register);

        ClassLoader previous =
            Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(
                pluginClassLoader
            );
            context.refresh();
        } catch (Throwable error) {
            context.close();
            throw new PluginRuntimeException(
                "刷新插件 Spring 子上下文失败：" + wrapper.getPluginId(),
                error
            );
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
        return context;
    }

    private Class<?> loadComponent(
        ClassLoader classLoader,
        String className
    ) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException error) {
            throw new PluginRuntimeException(
                "插件 Spring 组件不存在：" + className,
                error
            );
        }
    }
}
