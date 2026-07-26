package com.aquafish.plugin.manifest;

import java.util.List;
import org.pf4j.DefaultPluginDescriptor;

/**
 * Aquafish 在 PF4J 标准清单上的扩展描述。
 *
 * <p>PF4J 负责插件 ID、版本、主类和依赖图；Aquafish 只额外保存后台展示名称，
 * 以及需要注册到插件 Spring 子上下文的配置类。这样运行代码不依赖固定页面或固定插件。</p>
 */
public final class AquafishPluginDescriptor extends DefaultPluginDescriptor {

    private final String displayName;
    private final List<String> springComponents;

    public AquafishPluginDescriptor(
        String pluginId,
        String displayName,
        String description,
        String pluginClass,
        String version,
        String requires,
        String provider,
        String license,
        List<String> springComponents
    ) {
        super(
            pluginId,
            description,
            pluginClass,
            version,
            requires,
            provider,
            license
        );
        this.displayName = displayName;
        this.springComponents = springComponents == null
            ? List.of()
            : List.copyOf(springComponents);
    }

    public String displayName() {
        return displayName;
    }

    public List<String> springComponents() {
        return springComponents;
    }
}
