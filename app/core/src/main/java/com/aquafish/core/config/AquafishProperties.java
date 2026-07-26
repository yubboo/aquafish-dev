package com.aquafish.core.config;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Aquafish 运行配置对象。
 *
 * 当前阶段：
 * Step 17-19-2：读取 workdir/application.yaml 里的核心配置。
 *
 * 当前职责：
 * 1. 读取 aquafish.work-dir。
 * 2. 读取 aquafish.external-url。
 * 3. 读取 aquafish.database.table-prefix。
 * 4. 读取 aquafish.theme.active。
 *
 * 为什么不用业务代码直接写 @Value：
 * 如果每个模块都自己写：
 * @Value("${aquafish.work-dir}")
 *
 * 后续配置字段改名时，会到处改。
 *
 * 所以统一收口到 AquafishProperties。
 */
@Component
public class AquafishProperties {

    /**
     * Aquafish 工作目录原始配置值。
     *
     * 示例：
     * ${user.home}/.aquafish/dev
     */
    private final String workDir;

    /**
     * 站点外部访问地址。
     *
     * 本地开发：
     * http://127.0.0.1:8080
     *
     * 线上部署：
     * https://你的域名
     */
    private final String externalUrl;

    /**
     * 数据库表前缀。
     *
     * 示例：
     * aq_
     *
     * 注意：
     * 业务代码不能写死 aq_users。
     * 必须通过 TableNameResolver 生成真实表名。
     */
    private final String tablePrefix;

    /**
     * 当前启用主题。
     *
     * 当前默认：
     * default
     */
    private final AtomicReference<String> activeTheme;

    /**
     * 构造方法注入配置。
     *
     * 这些值来自：
     * 1. app/boot/src/main/resources/application.yml 默认配置；
     * 2. workdir/application.yaml 外置配置；
     * 3. 环境变量。
     *
     * Spring Boot 会按优先级合并配置。
     */
    public AquafishProperties(
        @Value("${aquafish.work-dir:workdir}") String workDir,
        @Value("${aquafish.external-url:http://127.0.0.1:8080}") String externalUrl,
        @Value("${aquafish.database.table-prefix:aq_}") String tablePrefix,
        @Value("${aquafish.theme.active:default}") String activeTheme
    ) {
        this.workDir = workDir;
        this.externalUrl = externalUrl;
        this.tablePrefix = tablePrefix;
        this.activeTheme = new AtomicReference<>(
            normalizeActiveTheme(activeTheme)
        );
    }

    public String workDir() {
        return workDir;
    }

    public String externalUrl() {
        return externalUrl;
    }

    public String tablePrefix() {
        return tablePrefix;
    }

    public String activeTheme() {
        return activeTheme.get();
    }

    /**
     * 在主题切换成功后更新当前进程采用的活动主题。
     *
     * <p>持久化由 theme 模块的运行配置服务先完成；本方法只让当前 JVM
     * 无需重启即可采用同一主题。调用方不能绕过持久化直接使用本入口。</p>
     *
     * @param themeId 已通过主题清单校验的主题唯一标识
     */
    public void useActiveTheme(String themeId) {
        activeTheme.set(
            normalizeActiveTheme(themeId)
        );
    }

    private static String normalizeActiveTheme(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
