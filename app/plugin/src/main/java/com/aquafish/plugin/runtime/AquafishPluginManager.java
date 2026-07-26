package com.aquafish.plugin.runtime;

import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.plugin.manifest.AquafishPluginDescriptor;
import com.aquafish.plugin.manifest.YamlPluginDescriptorFinder;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginFactory;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Aquafish PF4J 插件管理器。
 *
 * <p>PF4J 负责独立 ClassLoader、版本约束和依赖解析；本类负责工作目录、安全的 Spring
 * 子上下文、按依赖顺序启停以及后台可读状态快照。所有生命周期写操作使用同一把锁，
 * 防止后台并发点击导致一个插件被重复启动或卸载。</p>
 */
@Component
public class AquafishPluginManager
    extends DefaultPluginManager
    implements InitializingBean {

    private final WorkDirResolver workDirResolver;
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    private GenericApplicationContext sharedContext;
    private AquafishPluginApplicationContextFactory contextFactory;
    private volatile boolean loaded;

    public AquafishPluginManager(WorkDirResolver workDirResolver) {
        this.workDirResolver = workDirResolver;
    }

    /**
     * DefaultPluginManager 构造阶段字段尚未注入，延后到 afterPropertiesSet 再初始化。
     */
    @Override
    protected void initialize() {
    }

    @Override
    public void afterPropertiesSet() {
        workDirResolver.ensureBaseDirectories();
        sharedContext = new GenericApplicationContext();
        sharedContext.setId("aquafish-plugin-shared");
        sharedContext.getBeanFactory().registerSingleton(
            "aquafishPluginHost",
            new DefaultPluginHost()
        );
        sharedContext.refresh();
        contextFactory =
            new AquafishPluginApplicationContextFactory(sharedContext);

        setSystemVersion(
            firstText(
                AquafishPluginManager.class.getPackage()
                    .getImplementationVersion(),
                "0.0.1"
            )
        );
        super.initialize();
    }

    @Override
    protected List<java.nio.file.Path> createPluginsRoot() {
        return List.of(
            workDirResolver.pluginsDir()
                .toAbsolutePath()
                .normalize()
        );
    }

    @Override
    protected PluginDescriptorFinder createPluginDescriptorFinder() {
        return new YamlPluginDescriptorFinder();
    }

    @Override
    protected PluginFactory createPluginFactory() {
        return new AquafishPluginFactory(
            contextFactory,
            workDirResolver
        );
    }

    /**
     * 扫描 workdir/plugins 并让 PF4J 构建一次真实依赖图。
     */
    public void loadAll() {
        lifecycleLock.lock();
        try {
            if (!loaded) {
                super.loadPlugins();
                loaded = true;
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * 重新扫描插件目录。调用者应在重新扫描后按数据库 enabled_flag 恢复需要启用的插件。
     */
    public void reloadAll() {
        lifecycleLock.lock();
        try {
            if (loaded) {
                super.stopPlugins();
                super.unloadPlugins();
            }
            super.loadPlugins();
            loaded = true;
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * 深度优先启动所有必选依赖，再启动目标插件。
     */
    public PluginState startWithDependencies(String pluginId) {
        lifecycleLock.lock();
        try {
            requireLoaded();
            return startTree(pluginId, new LinkedHashSet<>());
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * 先停止所有依赖目标插件的下游插件，再停止目标插件，防止留下失效 Bean 引用。
     */
    public PluginState stopWithDependents(String pluginId) {
        lifecycleLock.lock();
        try {
            requireLoaded();
            stopDependents(pluginId, new LinkedHashSet<>());
            PluginWrapper wrapper = requirePlugin(pluginId);
            return wrapper.getPluginState().isStarted()
                ? super.stopPlugin(pluginId)
                : wrapper.getPluginState();
        } finally {
            lifecycleLock.unlock();
        }
    }

    public List<PluginRuntimeItem> snapshot() {
        lifecycleLock.lock();
        try {
            if (!loaded) {
                return List.of();
            }
            return getPlugins().stream()
                .map(this::runtimeItem)
                .sorted((left, right) ->
                    left.pluginId().compareToIgnoreCase(
                        right.pluginId()
                    )
                )
                .toList();
        } finally {
            lifecycleLock.unlock();
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    @PreDestroy
    public void close() {
        lifecycleLock.lock();
        try {
            if (loaded) {
                super.stopPlugins();
                super.unloadPlugins();
                loaded = false;
            }
            if (sharedContext != null) {
                sharedContext.close();
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    private PluginState startTree(
        String pluginId,
        Set<String> visiting
    ) {
        PluginWrapper wrapper = requirePlugin(pluginId);
        if (wrapper.getPluginState().isStarted()) {
            return wrapper.getPluginState();
        }
        if (!visiting.add(pluginId)) {
            throw new PluginRuntimeException(
                "插件依赖出现循环：" + String.join(" -> ", visiting)
                    + " -> " + pluginId
            );
        }
        try {
            wrapper.getDescriptor().getDependencies().forEach(dependency -> {
                PluginWrapper required = getPlugin(
                    dependency.getPluginId()
                );
                if (required == null) {
                    if (!dependency.isOptional()) {
                        throw new PluginRuntimeException(
                            "缺少必选插件依赖："
                                + pluginId + " -> "
                                + dependency.getPluginId()
                        );
                    }
                    return;
                }
                startTree(required.getPluginId(), visiting);
            });
            if (wrapper.getPluginState() == PluginState.DISABLED) {
                super.enablePlugin(pluginId);
            }
            return super.startPlugin(pluginId);
        } finally {
            visiting.remove(pluginId);
        }
    }

    private void stopDependents(
        String pluginId,
        Set<String> visited
    ) {
        if (!visited.add(pluginId)) {
            return;
        }
        for (PluginWrapper candidate : getPlugins()) {
            boolean dependsOnTarget = candidate.getDescriptor()
                .getDependencies()
                .stream()
                .anyMatch(dependency ->
                    dependency.getPluginId().equals(pluginId)
                );
            if (dependsOnTarget) {
                stopDependents(candidate.getPluginId(), visited);
                if (candidate.getPluginState().isStarted()) {
                    super.stopPlugin(candidate.getPluginId());
                }
            }
        }
    }

    private PluginRuntimeItem runtimeItem(PluginWrapper wrapper) {
        String name = wrapper.getDescriptor()
            instanceof AquafishPluginDescriptor descriptor
                ? descriptor.displayName()
                : wrapper.getPluginId();
        List<PluginRuntimeItem.PluginDependencyItem> dependencies =
            new ArrayList<>();
        wrapper.getDescriptor().getDependencies().forEach(dependency -> {
            PluginWrapper resolved = getPlugin(
                dependency.getPluginId()
            );
            dependencies.add(
                new PluginRuntimeItem.PluginDependencyItem(
                    dependency.getPluginId(),
                    dependency.getPluginVersionSupport(),
                    dependency.isOptional(),
                    resolved != null,
                    resolved == null
                        ? "MISSING"
                        : resolved.getPluginState().name()
                )
            );
        });
        Throwable failed = wrapper.getFailedException();
        return new PluginRuntimeItem(
            wrapper.getPluginId(),
            name,
            wrapper.getDescriptor().getVersion(),
            wrapper.getDescriptor().getProvider(),
            wrapper.getDescriptor().getPluginDescription(),
            Files.isDirectory(wrapper.getPluginPath())
                ? "directory"
                : "jar",
            wrapper.getPluginState().name(),
            wrapper.getPluginState().isStarted(),
            wrapper.getPluginClassLoader().getClass().getSimpleName(),
            List.copyOf(dependencies),
            failed == null ? "" : safeMessage(failed)
        );
    }

    private PluginWrapper requirePlugin(String pluginId) {
        PluginWrapper wrapper = getPlugin(
            pluginId == null ? "" : pluginId.trim()
        );
        if (wrapper == null) {
            throw new PluginRuntimeException(
                "插件不存在或依赖解析失败：" + pluginId
            );
        }
        return wrapper;
    }

    private void requireLoaded() {
        if (!loaded) {
            throw new PluginRuntimeException(
                "插件目录尚未完成 PF4J 扫描。"
            );
        }
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
            ? error.getClass().getSimpleName()
            : message;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static final class DefaultPluginHost
        implements AquafishPluginHost {

        @Override
        public String applicationName() {
            return "Aquafish";
        }

        @Override
        public String applicationVersion() {
            String version = AquafishPluginManager.class.getPackage()
                .getImplementationVersion();
            return version == null || version.isBlank()
                ? "0.0.1"
                : version;
        }
    }
}
