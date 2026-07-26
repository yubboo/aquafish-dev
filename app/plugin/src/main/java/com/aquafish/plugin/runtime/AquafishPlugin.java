package com.aquafish.plugin.runtime;

import org.pf4j.Plugin;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginWrapper;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Aquafish 插件入口基类。
 *
 * <p>{@link #start()}、{@link #stop()} 和 {@link #delete()} 固定管理 Spring 子上下文，
 * 插件只重写 {@link #onStart()}、{@link #onStop()}、{@link #onDelete()}，避免忘记释放
 * ClassLoader 引用、线程和 Bean。</p>
 */
public abstract class AquafishPlugin extends Plugin {

    private AquafishPluginApplicationContextFactory contextFactory;
    private AquafishPluginContext pluginContext;
    private volatile ConfigurableApplicationContext applicationContext;

    protected AquafishPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    final void attach(
        AquafishPluginApplicationContextFactory contextFactory,
        AquafishPluginContext pluginContext
    ) {
        this.contextFactory = contextFactory;
        this.pluginContext = pluginContext;
    }

    @Override
    public final synchronized void start() {
        if (applicationContext != null && applicationContext.isActive()) {
            return;
        }
        if (contextFactory == null || pluginContext == null) {
            throw new PluginRuntimeException("插件尚未绑定 Aquafish 运行上下文。");
        }

        ConfigurableApplicationContext created =
            contextFactory.create(getWrapper(), this, pluginContext);
        try {
            applicationContext = created;
            onStart();
        } catch (Throwable error) {
            applicationContext = null;
            created.close();
            throw new PluginRuntimeException(
                "插件启动回调失败：" + getWrapper().getPluginId(),
                error
            );
        }
    }

    @Override
    public final synchronized void stop() {
        Throwable callbackError = null;
        try {
            onStop();
        } catch (Throwable error) {
            callbackError = error;
        } finally {
            ConfigurableApplicationContext current = applicationContext;
            applicationContext = null;
            if (current != null) {
                current.close();
            }
        }
        if (callbackError != null) {
            throw new PluginRuntimeException(
                "插件停止回调失败：" + getWrapper().getPluginId(),
                callbackError
            );
        }
    }

    @Override
    public final void delete() {
        onDelete();
    }

    protected void onStart() {
    }

    protected void onStop() {
    }

    protected void onDelete() {
    }

    protected final AquafishPluginContext pluginContext() {
        if (pluginContext == null) {
            throw new IllegalStateException("插件运行上下文尚未就绪。");
        }
        return pluginContext;
    }

    protected final ConfigurableApplicationContext applicationContext() {
        ConfigurableApplicationContext current = applicationContext;
        if (current == null || !current.isActive()) {
            throw new IllegalStateException("插件 Spring 子上下文尚未启动。");
        }
        return current;
    }
}
