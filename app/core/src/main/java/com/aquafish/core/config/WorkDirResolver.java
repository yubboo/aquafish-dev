package com.aquafish.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Aquafish 工作目录解析器。
 *
 * 当前阶段：
 * Step 17-19-2：正式引入 Halo 式 workdir 工作目录。
 *
 * 当前职责：
 * 1. 根据 aquafish.work-dir 获取真实工作目录。
 * 2. 统一生成 storage / themes / plugins / licenses / backups 等目录路径。
 * 3. 提供基础目录初始化能力。
 *
 * 设计目标：
 * 后续所有模块都不要自己拼：
 * ${user.home}/.aquafish/dev/themes
 *
 * 应该统一调用：
 * workDirResolver.themesDir()
 *
 * 这样后续部署到 Linux / Docker / JAR 时，路径规则不会乱。
 */
@Component
public class WorkDirResolver {

    /**
     * Aquafish 运行配置。
     */
    private final AquafishProperties properties;

    /**
     * 构造方法注入。
     */
    public WorkDirResolver(AquafishProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取 workdir 根目录。
     *
     * @return workdir 绝对路径
     */
    public Path workDir() {
        return AquafishPathResolver.resolveWorkDirPath(properties.workDir());
    }

    /**
     * 获取外置 application.yaml 配置文件路径。
     *
     * @return workdir/application.yaml
     */
    public Path applicationYamlFile() {
        return workDir().resolve("application.yaml").normalize();
    }

    /**
     * 获取站点实例 ID 文件路径。
     *
     * 后续授权、主题市场、插件市场会用到。
     *
     * @return workdir/instance.id
     */
    public Path instanceIdFile() {
        return workDir().resolve("instance.id").normalize();
    }

    /**
     * 获取运行数据目录。
     *
     * @return workdir/storage
     */
    public Path storageDir() {
        return workDir().resolve("storage").normalize();
    }

    /**
     * 获取缓存目录。
     *
     * @return workdir/storage/cache
     */
    public Path cacheDir() {
        return storageDir().resolve("cache").normalize();
    }

    /**
     * 获取日志目录。
     *
     * @return workdir/storage/logs
     */
    public Path logsDir() {
        return storageDir().resolve("logs").normalize();
    }

    /**
     * 获取上传文件目录。
     *
     * @return workdir/storage/uploads
     */
    public Path uploadsDir() {
        return storageDir().resolve("uploads").normalize();
    }

    /**
     * 获取临时文件目录。
     *
     * @return workdir/storage/temp
     */
    public Path tempDir() {
        return storageDir().resolve("temp").normalize();
    }

    /**
     * 获取主题目录。
     *
     * @return workdir/themes
     */
    public Path themesDir() {
        return workDir().resolve("themes").normalize();
    }

    /**
     * 获取插件目录。
     *
     * @return workdir/plugins
     */
    public Path pluginsDir() {
        return workDir().resolve("plugins").normalize();
    }

    /**
     * 获取单个插件可写的私有数据目录。
     *
     * <p>插件 JAR 与开发目录只负责代码加载；配置、缓存和业务数据必须写入
     * {@code workdir/storage/plugins/<pluginId>}，这样升级或替换插件包时不会丢失数据。</p>
     *
     * @param pluginId PF4J 清单中的稳定插件标识
     * @return 规范化后的插件私有数据目录
     */
    public Path pluginDataDir(String pluginId) {
        String safePluginId = pluginId == null ? "" : pluginId.trim();
        if (!safePluginId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")) {
            throw new IllegalArgumentException("插件标识格式不正确。");
        }
        Path root = storageDir().resolve("plugins").toAbsolutePath().normalize();
        Path resolved = root.resolve(safePluginId).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("插件数据目录越过了 workdir 安全边界。");
        }
        return resolved;
    }

    /**
     * 获取授权文件目录。
     *
     * 后续用于保存：
     * 1. 核心许可证；
     * 2. 商业主题许可证；
     * 3. 商业插件许可证。
     *
     * @return workdir/licenses
     */
    public Path licensesDir() {
        return workDir().resolve("licenses").normalize();
    }

    /**
     * 获取备份目录。
     *
     * @return workdir/backups
     */
    public Path backupsDir() {
        return workDir().resolve("backups").normalize();
    }

    /**
     * 创建 workdir 基础目录。
     *
     * 当前只创建目录，不写入业务数据。
     *
     * 后续启动时可以调用这个方法，确保基础目录存在。
     */
    public void ensureBaseDirectories() {
        try {
            Files.createDirectories(workDir());
            Files.createDirectories(storageDir());
            Files.createDirectories(cacheDir());
            Files.createDirectories(logsDir());
            Files.createDirectories(uploadsDir());
            Files.createDirectories(tempDir());
            Files.createDirectories(themesDir());
            Files.createDirectories(pluginsDir());
            Files.createDirectories(storageDir().resolve("plugins"));
            Files.createDirectories(licensesDir());
            Files.createDirectories(backupsDir());
        } catch (IOException error) {
            throw new IllegalStateException("创建 Aquafish workdir 基础目录失败：" + workDir(), error);
        }
    }
}
