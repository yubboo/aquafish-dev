package com.aquafish.core.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import com.aquafish.core.config.AquafishPathResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Aquafish 安装锁兼容服务。
 *
 * <p>安装锁文件位于 workdir/install.lock，只保留给旧部署识别和故障恢复。
 * 是否已经安装必须以数据库 system_instances 为准，不能单独根据该文件
 * 开放或关闭安装、登录接口。</p>
 */
@Service
public class InstallLockService {

    private final Path workDir;

    private final Path lockFile;

    private final Path applicationConfigFile;

    private final Path storageDir;

    private final Path themesDir;

    private final Path pluginsDir;

    private final Path backupsDir;

    public InstallLockService(
        @Value("${aquafish.work-dir:workdir}") String workDir
    ) {
        this.workDir = AquafishPathResolver.resolveWorkDirPath(workDir);
        this.lockFile = this.workDir.resolve("install.lock");
        this.applicationConfigFile = this.workDir.resolve("application.yaml");
        this.storageDir = this.workDir.resolve("storage");
        this.themesDir = this.workDir.resolve("themes");
        this.pluginsDir = this.workDir.resolve("plugins");
        this.backupsDir = this.workDir.resolve("backups");
    }

    /**
     * 获取文件兼容状态，不代表数据库权威安装状态。
     */
    public InstallStatus status() {
        boolean locked = Files.exists(lockFile);
        boolean workDirExists = Files.exists(workDir);
        boolean workDirWritable = isWritableOrCreatable(workDir);
        boolean storageDirExists = Files.exists(storageDir);
        boolean storageDirWritable = isWritableOrCreatable(storageDir);
        boolean themesDirExists = Files.exists(themesDir);
        boolean pluginsDirExists = Files.exists(pluginsDir);
        boolean applicationConfigExists = Files.exists(applicationConfigFile);

        return new InstallStatus(
            locked,
            locked,
            !locked && workDirWritable,
            normalizePath(workDir),
            normalizePath(lockFile),
            normalizePath(applicationConfigFile),
            workDirExists,
            workDirWritable,
            storageDirExists,
            storageDirWritable,
            themesDirExists,
            pluginsDirExists,
            applicationConfigExists,
            installedAt(),
            lockSummary()
        );
    }

    /**
     * 获取安装环境信息。
     */
    public InstallEnvironmentInfo environment() {
        return new InstallEnvironmentInfo(
            System.getProperty("java.version", ""),
            System.getProperty("java.vendor", ""),
            System.getProperty("java.home", ""),
            System.getProperty("os.name", ""),
            System.getProperty("os.version", ""),
            System.getProperty("os.arch", ""),
            System.getProperty("user.dir", ""),
            normalizePath(workDir),
            normalizePath(storageDir),
            normalizePath(themesDir),
            normalizePath(pluginsDir),
            normalizePath(backupsDir),
            normalizePath(lockFile),
            Files.exists(workDir),
            isWritableOrCreatable(workDir),
            Files.exists(storageDir),
            isWritableOrCreatable(storageDir),
            Files.exists(themesDir),
            Files.exists(pluginsDir),
            Files.exists(backupsDir),
            Files.exists(lockFile)
        );
    }

    public Path workDir() {
        return workDir;
    }

    public Path lockFile() {
        return lockFile;
    }

    /**
     * 安装成功或兼容恢复时写入安装锁。
     */
    public void writeInstallLock(String content) {
        try {
            Files.createDirectories(workDir);

            String safeContent = content == null || content.isBlank()
                ? "installed=true"
                : content.trim();

            Files.writeString(lockFile, safeContent);
        } catch (IOException error) {
            throw new IllegalStateException("写入安装锁失败：" + error.getMessage(), error);
        }
    }

    /**
     * 用户完成危险确认且数据库精确清理成功后，
     * 删除旧的兼容安装锁。
     */
    public void deleteInstallLock() {
        try {
            Files.deleteIfExists(lockFile);
        } catch (IOException error) {
            throw new IllegalStateException(
                "删除旧安装锁失败：" + error.getMessage(),
                error
            );
        }
    }

    private String installedAt() {
        if (!Files.exists(lockFile)) {
            return null;
        }

        try {
            FileTime time = Files.getLastModifiedTime(lockFile);
            return time.toString();
        } catch (IOException error) {
            return null;
        }
    }

    private String lockSummary() {
        if (!Files.exists(lockFile)) {
            return null;
        }

        try {
            String content = Files.readString(lockFile).trim();

            if (content.isBlank()) {
                return "install.lock exists";
            }

            if (content.length() > 500) {
                return content.substring(0, 500);
            }

            return content;
        } catch (IOException error) {
            return "install.lock exists, but read failed: " + error.getMessage();
        }
    }

    private boolean isWritableOrCreatable(Path path) {
        try {
            if (Files.exists(path)) {
                return Files.isDirectory(path) && Files.isWritable(path);
            }

            Path parent = path.getParent();

            while (parent != null && !Files.exists(parent)) {
                parent = parent.getParent();
            }

            return parent != null && Files.isWritable(parent);
        } catch (Exception error) {
            return false;
        }
    }

    private String normalizePath(Path path) {
        if (path == null) {
            return "";
        }

        return path.toAbsolutePath().normalize().toString();
    }
}
