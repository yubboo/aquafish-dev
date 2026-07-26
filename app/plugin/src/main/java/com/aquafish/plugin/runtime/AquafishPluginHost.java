package com.aquafish.plugin.runtime;

/**
 * 插件可访问的最小宿主信息。
 *
 * <p>插件 Spring 子上下文不会直接继承 Aquafish 根容器，避免插件绕过权限边界取得数据库、
 * 密钥或管理服务。后续需要开放宿主能力时，应在此接口增加经过授权的窄接口。</p>
 */
public interface AquafishPluginHost {

    String applicationName();

    String applicationVersion();
}
