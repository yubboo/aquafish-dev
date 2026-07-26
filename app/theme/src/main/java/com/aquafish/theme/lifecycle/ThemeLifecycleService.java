package com.aquafish.theme.lifecycle;

import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.core.operation.ExtensionOperationCoordinator;
import com.aquafish.core.operation.ExtensionOperationHandle;
import com.aquafish.core.operation.ExtensionOperationKeys;
import com.aquafish.theme.core.ActiveThemeResolver;
import com.aquafish.theme.core.DefaultThemeResolver;
import com.aquafish.theme.core.ThemeDescriptor;
import com.aquafish.theme.core.ThemeScanner;
import com.aquafish.theme.install.ThemeArchiveExtractionException;
import com.aquafish.theme.install.ThemeArchiveExtractor;
import com.aquafish.theme.install.ThemeExtractionResult;
import com.aquafish.theme.install.ThemeInstallFileOperations;
import com.aquafish.theme.manifest.ThemeManifest;
import com.aquafish.theme.manifest.ThemeManifestParser;
import com.aquafish.theme.validation.ThemePackageIssue;
import com.aquafish.theme.validation.ThemePackageValidationResult;
import com.aquafish.theme.validation.ThemePackageValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 已安装主题的启用、升级和安全卸载服务。
 *
 * <p>与 Halo 的主题生命周期边界一致：源码主题包与实例运行目录分离；所有写操作
 * 统一使用主题全局协调锁；默认主题、活动主题和仍被子主题依赖的父主题不能卸载。
 * 升级先解压校验，再备份旧目录并提交新目录，失败时恢复旧版本。</p>
 */
@Service
public class ThemeLifecycleService {

    private static final DateTimeFormatter OPERATION_TIME =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ThemeScanner themeScanner;
    private final ActiveThemeResolver activeThemeResolver;
    private final ThemeRuntimeConfigurationService configurationService;
    private final ThemePackageValidator packageValidator;
    private final ThemeArchiveExtractor archiveExtractor;
    private final ThemeInstallFileOperations fileOperations;
    private final WorkDirResolver workDirResolver;
    private final ExtensionOperationCoordinator operationCoordinator;
    private final ThemeManifestParser manifestParser = new ThemeManifestParser();

    public ThemeLifecycleService(
        ThemeScanner themeScanner,
        ActiveThemeResolver activeThemeResolver,
        ThemeRuntimeConfigurationService configurationService,
        ThemePackageValidator packageValidator,
        ThemeArchiveExtractor archiveExtractor,
        ThemeInstallFileOperations fileOperations,
        WorkDirResolver workDirResolver,
        ExtensionOperationCoordinator operationCoordinator
    ) {
        this.themeScanner = themeScanner;
        this.activeThemeResolver = activeThemeResolver;
        this.configurationService = configurationService;
        this.packageValidator = packageValidator;
        this.archiveExtractor = archiveExtractor;
        this.fileOperations = fileOperations;
        this.workDirResolver = workDirResolver;
        this.operationCoordinator = operationCoordinator;
    }

    /** 启用已安装且具备模板目录的主题。 */
    public ThemeLifecycleResult activate(String themeId) {
        String normalizedId = normalizeThemeId(themeId);
        try (ExtensionOperationHandle ignored = acquireOperation()) {
            List<ThemeDescriptor> installed = themeScanner.scanInstalledThemes();
            ThemeDescriptor theme = requireInstalled(installed, normalizedId);
            if (!theme.templatesDirExists()) {
                throw new ThemeLifecycleException(
                    "THEME_TEMPLATES_MISSING",
                    "主题缺少 templates 目录，不能启用：" + normalizedId
                );
            }
            validateParent(theme.name(), theme.parent(), theme.engine(), installed);
            configurationService.activate(theme.name());
            return new ThemeLifecycleResult(
                "activate",
                theme.name(),
                theme.version(),
                true,
                "主题已启用，当前进程与 application.yaml 已同步更新。"
            );
        }
    }

    /**
     * 升级一个非默认主题。
     *
     * <p>上传包 ID 必须与目标主题一致；升级不会改变当前启用主题。</p>
     */
    public ThemeLifecycleResult upgrade(String themeId, Path packagePath) {
        String normalizedId = normalizeThemeId(themeId);
        if (DefaultThemeResolver.DEFAULT_THEME_NAME.equals(normalizedId)) {
            throw new ThemeLifecycleException(
                "DEFAULT_THEME_UPGRADE_FORBIDDEN",
                "官方 default 主题只能随受信任的 Aquafish 版本升级。"
            );
        }

        try (ExtensionOperationHandle ignored = acquireOperation()) {
            List<ThemeDescriptor> installed = themeScanner.scanInstalledThemes();
            ThemeDescriptor current = requireInstalled(installed, normalizedId);
            ThemePackageValidationResult validation = packageValidator.validate(packagePath);
            requireValidPackage(validation);
            ThemeManifest manifest = validation.manifest();
            if (!normalizedId.equals(manifest.id())) {
                throw new ThemeLifecycleException(
                    "THEME_UPGRADE_ID_MISMATCH",
                    "升级包主题 ID 与当前主题不一致："
                        + manifest.id()
                        + " / "
                        + normalizedId
                );
            }
            validateParent(
                manifest.id(),
                manifest.parent(),
                manifest.engine(),
                installed
            );
            validateDependentThemes(current, manifest, installed);
            return commitUpgrade(current, validation, packagePath);
        }
    }

    /**
     * 从实例运行目录卸载主题。
     *
     * <p>目录会移动到实例 backups/themes/uninstalled，避免误操作造成不可恢复的数据丢失。</p>
     */
    public ThemeLifecycleResult uninstall(String themeId) {
        String normalizedId = normalizeThemeId(themeId);
        try (ExtensionOperationHandle ignored = acquireOperation()) {
            List<ThemeDescriptor> installed = themeScanner.scanInstalledThemes();
            ThemeDescriptor theme = requireInstalled(installed, normalizedId);
            rejectProtectedTheme(theme, installed);

            Path source = safeThemeDirectory(theme);
            Path backup = uniqueBackupPath("uninstalled", theme.name());
            try {
                Files.createDirectories(backup.getParent());
                fileOperations.moveDirectory(source, backup, false);
            } catch (IOException error) {
                throw new ThemeLifecycleException(
                    "THEME_UNINSTALL_FAILED",
                    "卸载主题失败：" + safeMessage(error),
                    error
                );
            }

            return new ThemeLifecycleResult(
                "uninstall",
                theme.name(),
                theme.version(),
                false,
                "主题已从运行目录卸载，并保留安全备份。"
            );
        }
    }

    public List<UninstalledThemeBackup> listUninstalled() {
        Path root = uninstalledRoot();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (var entries = Files.list(root)) {
            return entries
                .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> !Files.isSymbolicLink(path))
                .map(this::readUninstalledBackup)
                .sorted((left, right) -> right.backupId().compareTo(left.backupId()))
                .toList();
        } catch (IOException error) {
            throw new ThemeLifecycleException(
                "THEME_BACKUP_SCAN_FAILED",
                "读取已卸载主题备份失败：" + safeMessage(error),
                error
            );
        }
    }

    public ThemeLifecycleResult restore(String backupId) {
        try (ExtensionOperationHandle ignored = acquireOperation()) {
            UninstalledThemeBackup backup = requireUninstalledBackup(backupId);
            Path source = safeUninstalledBackupDirectory(backup.backupId());
            Path target = workDirResolver.themesDir()
                .toAbsolutePath()
                .normalize()
                .resolve(backup.themeId())
                .normalize();
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new ThemeLifecycleException(
                    "THEME_RESTORE_ALREADY_INSTALLED",
                    "同 ID 主题已经安装，不能恢复：" + backup.themeId()
                );
            }
            try {
                fileOperations.moveDirectory(source, target, false);
            } catch (IOException error) {
                throw new ThemeLifecycleException(
                    "THEME_RESTORE_FAILED",
                    "恢复主题失败：" + safeMessage(error),
                    error
                );
            }
            ThemeDescriptor restored = requireInstalled(
                themeScanner.scanInstalledThemes(),
                backup.themeId()
            );
            return new ThemeLifecycleResult(
                "restore",
                restored.name(),
                restored.version(),
                false,
                "主题已恢复到运行目录，请按需手动启用。"
            );
        }
    }

    public ThemeLifecycleResult deleteUninstalled(String backupId) {
        try (ExtensionOperationHandle ignored = acquireOperation()) {
            UninstalledThemeBackup backup = requireUninstalledBackup(backupId);
            Path target = safeUninstalledBackupDirectory(backup.backupId());
            try {
                fileOperations.deleteRecursively(target);
            } catch (IOException error) {
                throw new ThemeLifecycleException(
                    "THEME_BACKUP_DELETE_FAILED",
                    "永久删除主题备份失败：" + safeMessage(error),
                    error
                );
            }
            return new ThemeLifecycleResult(
                "delete-backup",
                backup.themeId(),
                backup.version(),
                false,
                "已永久删除卸载备份，此操作不可恢复。"
            );
        }
    }

    private UninstalledThemeBackup requireUninstalledBackup(String backupId) {
        String normalized = normalizeBackupId(backupId);
        return listUninstalled().stream()
            .filter(item -> normalized.equals(item.backupId()))
            .findFirst()
            .orElseThrow(() -> new ThemeLifecycleException(
                "THEME_BACKUP_NOT_FOUND",
                "没有找到已卸载主题备份：" + normalized
            ));
    }

    private UninstalledThemeBackup readUninstalledBackup(Path directory) {
        Path manifestPath = directory.resolve("theme.yaml");
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ThemeLifecycleException(
                "THEME_BACKUP_INVALID",
                "已卸载主题备份缺少 theme.yaml：" + directory.getFileName()
            );
        }
        ThemeManifest manifest = manifestParser.parse(manifestPath);
        String backupId = directory.getFileName().toString();
        return new UninstalledThemeBackup(
            backupId,
            manifest.id(),
            manifest.title(),
            manifest.version(),
            manifest.engine()
        );
    }

    private Path safeUninstalledBackupDirectory(String backupId) {
        Path root = uninstalledRoot();
        Path candidate = root.resolve(normalizeBackupId(backupId))
            .toAbsolutePath()
            .normalize();
        if (!fileOperations.isWithin(root, candidate)
            || candidate.getParent() == null
            || !root.equals(candidate.getParent())
            || Files.isSymbolicLink(candidate)) {
            throw new ThemeLifecycleException(
                "THEME_BACKUP_PATH_UNSAFE",
                "主题备份路径不安全，操作已停止。"
            );
        }
        return candidate;
    }

    private Path uninstalledRoot() {
        return workDirResolver.backupsDir()
            .resolve("themes")
            .resolve("uninstalled")
            .toAbsolutePath()
            .normalize();
    }

    private String normalizeBackupId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[a-z][a-z0-9-]{0,63}-[0-9]{8}-[0-9]{6}-[0-9a-fA-F-]{36}")) {
            throw new ThemeLifecycleException(
                "THEME_BACKUP_ID_INVALID",
                "非法主题备份 ID。"
            );
        }
        return normalized;
    }

    public record UninstalledThemeBackup(
        String backupId,
        String themeId,
        String title,
        String version,
        String engine
    ) {
    }

    private ThemeLifecycleResult commitUpgrade(
        ThemeDescriptor current,
        ThemePackageValidationResult validation,
        Path packagePath
    ) {
        ThemeExtractionResult extraction;
        try {
            extraction = archiveExtractor.extract(packagePath, validation);
        } catch (ThemeArchiveExtractionException error) {
            throw new ThemeLifecycleException(
                error.errorCode().name(),
                error.getMessage(),
                error
            );
        }

        Path currentDirectory = safeThemeDirectory(current);
        Path targetDirectory = workDirResolver.themesDir()
            .toAbsolutePath()
            .normalize()
            .resolve(current.name())
            .normalize();
        Path backupDirectory = uniqueBackupPath("upgrades", current.name());
        Path workspace = extraction.workspacePath();
        boolean oldVersionBackedUp = false;

        try {
            Files.createDirectories(backupDirectory.getParent());
            fileOperations.moveDirectory(
                currentDirectory,
                backupDirectory,
                false
            );
            oldVersionBackedUp = true;
            fileOperations.moveDirectory(
                extraction.extractedThemePath(),
                targetDirectory,
                false
            );

            ThemeDescriptor installed = themeScanner.scanInstalledThemes()
                .stream()
                .filter(item -> current.name().equals(item.name()))
                .findFirst()
                .orElseThrow(() -> new ThemeLifecycleException(
                    "THEME_UPGRADE_SCAN_FAILED",
                    "新主题提交后无法通过运行目录扫描。"
                ));
            if (!validation.manifest().version().equals(installed.version())) {
                throw new ThemeLifecycleException(
                    "THEME_UPGRADE_VERSION_MISMATCH",
                    "新主题提交后的版本与上传包不一致。"
                );
            }
            cleanupWorkspace(workspace);
            return new ThemeLifecycleResult(
                "upgrade",
                installed.name(),
                installed.version(),
                installed.name().equals(activeThemeResolver.activeThemeName()),
                "主题升级成功，旧版本已保留安全备份。"
            );
        } catch (IOException | RuntimeException error) {
            rollbackUpgrade(
                targetDirectory,
                backupDirectory,
                workspace,
                oldVersionBackedUp,
                error
            );
            if (error instanceof ThemeLifecycleException lifecycleError) {
                throw lifecycleError;
            }
            throw new ThemeLifecycleException(
                "THEME_UPGRADE_FAILED",
                "主题升级失败：" + safeMessage(error),
                error
            );
        }
    }

    private void rollbackUpgrade(
        Path targetDirectory,
        Path backupDirectory,
        Path workspace,
        boolean oldVersionBackedUp,
        Throwable originalError
    ) {
        try {
            /*
             * 只有旧目录已经成功移动到备份后，target 才可能是新版本。
             * 若第一次备份移动本身失败，绝不能把仍在 target 的旧主题误删。
             */
            if (oldVersionBackedUp) {
                if (Files.exists(targetDirectory, LinkOption.NOFOLLOW_LINKS)) {
                    fileOperations.deleteRecursively(targetDirectory);
                }
                if (Files.exists(backupDirectory, LinkOption.NOFOLLOW_LINKS)) {
                    fileOperations.moveDirectory(
                        backupDirectory,
                        targetDirectory,
                        false
                    );
                }
            }
            cleanupWorkspace(workspace);
        } catch (IOException rollbackError) {
            throw new ThemeLifecycleException(
                "THEME_UPGRADE_ROLLBACK_FAILED",
                "主题升级失败且旧版本回滚失败："
                    + safeMessage(originalError)
                    + "；回滚错误："
                    + safeMessage(rollbackError),
                rollbackError
            );
        }
    }

    private void cleanupWorkspace(Path workspace) throws IOException {
        if (
            workspace != null
                && Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)
        ) {
            fileOperations.deleteRecursively(workspace);
        }
    }

    private void rejectProtectedTheme(
        ThemeDescriptor theme,
        List<ThemeDescriptor> installed
    ) {
        if (DefaultThemeResolver.DEFAULT_THEME_NAME.equals(theme.name())) {
            throw new ThemeLifecycleException(
                "DEFAULT_THEME_UNINSTALL_FORBIDDEN",
                "官方 default 主题是系统安全回退层，不能卸载。"
            );
        }
        if (theme.name().equals(activeThemeResolver.activeThemeName())) {
            throw new ThemeLifecycleException(
                "ACTIVE_THEME_UNINSTALL_FORBIDDEN",
                "当前启用主题不能卸载，请先启用其他主题。"
            );
        }
        Optional<ThemeDescriptor> dependent = installed.stream()
            .filter(item -> theme.name().equals(item.parent()))
            .findFirst();
        if (dependent.isPresent()) {
            throw new ThemeLifecycleException(
                "PARENT_THEME_IN_USE",
                "主题仍被子主题依赖，不能卸载："
                    + dependent.orElseThrow().name()
            );
        }
    }

    private void validateParent(
        String themeId,
        String parentId,
        String engine,
        List<ThemeDescriptor> installed
    ) {
        if (parentId == null || parentId.isBlank()) {
            return;
        }
        if (themeId.equals(parentId)) {
            throw new ThemeLifecycleException(
                "THEME_PARENT_SELF_REFERENCE",
                "主题不能把自己声明为父主题。"
            );
        }
        ThemeDescriptor parent = installed.stream()
            .filter(item -> parentId.equals(item.name()))
            .findFirst()
            .orElseThrow(() -> new ThemeLifecycleException(
                "PARENT_THEME_NOT_INSTALLED",
                "依赖的父主题尚未安装：" + parentId
            ));
        if (!engine.equals(parent.engine())) {
            throw new ThemeLifecycleException(
                "PARENT_THEME_ENGINE_MISMATCH",
                "主题与父主题模板引擎不一致。"
            );
        }
    }

    private void validateDependentThemes(
        ThemeDescriptor current,
        ThemeManifest replacement,
        List<ThemeDescriptor> installed
    ) {
        if (current.engine().equals(replacement.engine())) {
            return;
        }
        Optional<ThemeDescriptor> dependent = installed.stream()
            .filter(item -> current.name().equals(item.parent()))
            .findFirst();
        if (dependent.isPresent()) {
            throw new ThemeLifecycleException(
                "THEME_UPGRADE_BREAKS_DEPENDENT",
                "升级会改变模板引擎并破坏子主题："
                    + dependent.orElseThrow().name()
            );
        }
    }

    private void requireValidPackage(ThemePackageValidationResult validation) {
        if (validation != null && validation.valid() && validation.manifest() != null) {
            return;
        }
        String details = validation == null
            ? "没有返回校验结果。"
            : validation.errors().stream()
                .map(ThemePackageIssue::message)
                .limit(3)
                .reduce((left, right) -> left + "；" + right)
                .orElse("主题包没有可用清单。");
        throw new ThemeLifecycleException(
            "THEME_PACKAGE_VALIDATION_FAILED",
            details
        );
    }

    private ThemeDescriptor requireInstalled(
        List<ThemeDescriptor> installed,
        String themeId
    ) {
        return installed.stream()
            .filter(theme -> themeId.equals(theme.name()))
            .findFirst()
            .orElseThrow(() -> new ThemeLifecycleException(
                "THEME_NOT_FOUND",
                "没有找到已安装主题：" + themeId
            ));
    }

    private Path safeThemeDirectory(ThemeDescriptor theme) {
        Path themesRoot = workDirResolver.themesDir()
            .toAbsolutePath()
            .normalize();
        Path themeDirectory = Path.of(theme.themeDir())
            .toAbsolutePath()
            .normalize();
        if (
            !fileOperations.isWithin(themesRoot, themeDirectory)
                || themeDirectory.getParent() == null
                || !themesRoot.equals(themeDirectory.getParent())
                || Files.isSymbolicLink(themeDirectory)
        ) {
            throw new ThemeLifecycleException(
                "THEME_DIRECTORY_UNSAFE",
                "主题运行目录不安全，操作已停止。"
            );
        }
        return themeDirectory;
    }

    private Path uniqueBackupPath(String operation, String themeId) {
        return workDirResolver.backupsDir()
            .resolve("themes")
            .resolve(operation)
            .resolve(
                themeId
                    + "-"
                    + LocalDateTime.now().format(OPERATION_TIME)
                    + "-"
                    + UUID.randomUUID()
            )
            .toAbsolutePath()
            .normalize();
    }

    private ExtensionOperationHandle acquireOperation() {
        return operationCoordinator.tryAcquire(ExtensionOperationKeys.THEME_GLOBAL)
            .orElseThrow(() -> new ThemeLifecycleException(
                "THEME_OPERATION_BUSY",
                "当前有其他主题写操作正在执行，请稍后重试。"
            ));
    }

    private String normalizeThemeId(String value) {
        String normalized = value == null
            ? ""
            : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new ThemeLifecycleException(
                "THEME_ID_INVALID",
                "非法主题 ID：" + normalized
            );
        }
        return normalized;
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank()
            ? "未知错误"
            : message;
    }
}
