package com.aquafish.theme.install;

import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.core.operation.ExtensionOperationCoordinator;
import com.aquafish.core.operation.ExtensionOperationHandle;
import com.aquafish.core.operation.ExtensionOperationKeys;
import com.aquafish.core.operation.InMemoryExtensionOperationCoordinator;
import com.aquafish.theme.core.ThemeDescriptor;
import com.aquafish.theme.core.ThemeScanner;
import com.aquafish.theme.manifest.ThemeManifest;
import com.aquafish.theme.validation.ThemePackageValidationResult;
import com.aquafish.theme.validation.ThemePackageValidator;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Aquafish 主题正式安装服务。
 *
 * <p>当前安装流程：</p>
 *
 * <pre>
 * 主题 ZIP
 * -> ThemePackageValidator
 * -> 安装策略检查
 * -> 已安装主题和父主题检查
 * -> ThemeArchiveExtractor
 * -> workdir/storage/temp 独立临时目录
 * -> 原子移动或安全降级移动
 * -> workdir/themes/{manifest.id}
 * -> ThemeScanner 二次确认
 * -> 清理安装工作目录
 * -> ThemeInstallResult
 * </pre>
 *
 * <p>当前版本明确不负责：</p>
 *
 * <ul>
 *     <li>不覆盖已有主题；</li>
 *     <li>不升级已有主题；</li>
 *     <li>不启用新安装主题；</li>
 *     <li>不修改 application.yaml；</li>
 *     <li>不删除 default 主题。</li>
 * </ul>
 */
@Service
public class ThemeInstallService {

    /**
     * 安装临时工作目录默认保留时间。
     *
     * <p>
     * 正常安装成功或失败都会立即清理，
     * 只有进程崩溃、断电或被强制终止时才会留下目录。
     * </p>
     */
    private static final Duration
        STALE_WORKSPACE_MAXIMUM_AGE =
            Duration.ofHours(24);

    /**
     * 主题 ZIP 安全校验器。
     */
    private final ThemePackageValidator
        packageValidator;

    /**
     * 主题 ZIP 临时解压器。
     */
    private final ThemeArchiveExtractor
        archiveExtractor;

    /**
     * Aquafish workdir 解析器。
     */
    private final WorkDirResolver
        workDirResolver;

    /**
     * 已安装主题扫描器。
     */
    private final ThemeScanner
        themeScanner;

    /**
     * 安装文件系统操作。
     */
    private final ThemeInstallFileOperations
        fileOperations;

    /**
     * 主题和插件统一操作协调器。
     */
    private final ExtensionOperationCoordinator
        operationCoordinator;

    /**
     * 过期安装临时目录清理器。
     */
    private final ThemeInstallWorkspaceCleaner
        workspaceCleaner;

    /**
     * 创建主题安装服务。
     *
     * @param packageValidator 主题包校验器
     * @param archiveExtractor 临时解压器
     * @param workDirResolver workdir 解析器
     * @param themeScanner 已安装主题扫描器
     * @param fileOperations 文件系统操作
     */
    public ThemeInstallService(
        ThemePackageValidator packageValidator,
        ThemeArchiveExtractor archiveExtractor,
        WorkDirResolver workDirResolver,
        ThemeScanner themeScanner,
        ThemeInstallFileOperations fileOperations
    ) {
        this(
            packageValidator,
            archiveExtractor,
            workDirResolver,
            themeScanner,
            fileOperations,
            new InMemoryExtensionOperationCoordinator(),
            new ThemeInstallWorkspaceCleaner(
                workDirResolver,
                fileOperations
            )
        );
    }

    /**
     * Spring 正式构造方法。
     *
     * @param packageValidator 主题包校验器
     * @param archiveExtractor 临时解压器
     * @param workDirResolver workdir 解析器
     * @param themeScanner 已安装主题扫描器
     * @param fileOperations 文件系统操作
     * @param operationCoordinator 扩展操作协调器
     * @param workspaceCleaner 过期临时目录清理器
     */
    @Autowired
    public ThemeInstallService(
        ThemePackageValidator packageValidator,
        ThemeArchiveExtractor archiveExtractor,
        WorkDirResolver workDirResolver,
        ThemeScanner themeScanner,
        ThemeInstallFileOperations fileOperations,
        ExtensionOperationCoordinator
            operationCoordinator,
        ThemeInstallWorkspaceCleaner
            workspaceCleaner
    ) {
        if (packageValidator == null) {
            throw new IllegalArgumentException(
                "主题包校验器不能为空。"
            );
        }

        if (archiveExtractor == null) {
            throw new IllegalArgumentException(
                "主题包临时解压器不能为空。"
            );
        }

        if (workDirResolver == null) {
            throw new IllegalArgumentException(
                "工作目录解析器不能为空。"
            );
        }

        if (themeScanner == null) {
            throw new IllegalArgumentException(
                "主题扫描器不能为空。"
            );
        }

        if (fileOperations == null) {
            throw new IllegalArgumentException(
                "主题安装文件操作不能为空。"
            );
        }

        if (operationCoordinator == null) {
            throw new IllegalArgumentException(
                "扩展操作协调器不能为空。"
            );
        }

        if (workspaceCleaner == null) {
            throw new IllegalArgumentException(
                "主题安装临时目录清理器不能为空。"
            );
        }

        this.packageValidator =
            packageValidator;

        this.archiveExtractor =
            archiveExtractor;

        this.workDirResolver =
            workDirResolver;

        this.themeScanner =
            themeScanner;

        this.fileOperations =
            fileOperations;

        this.operationCoordinator =
            operationCoordinator;

        this.workspaceCleaner =
            workspaceCleaner;
    }

    /**
     * 使用默认策略安装主题。
     *
     * @param packagePath 主题 ZIP
     * @return 安装结果
     */
    public ThemeInstallResult install(
        Path packagePath
    ) {
        return install(
            packagePath,
            ThemeInstallPolicy.defaults()
        );
    }

    /**
     * 使用指定策略安装主题。
     *
     * @param packagePath 主题 ZIP
     * @param policy 安装策略
     * @return 安装结果
     */
    public ThemeInstallResult install(
        Path packagePath,
        ThemeInstallPolicy policy
    ) {
        if (policy == null) {
            throw new IllegalArgumentException(
                "主题安装策略不能为空。"
            );
        }

        final Optional<
            ExtensionOperationHandle
        > operationAttempt;

        try {
            operationAttempt =
                operationCoordinator.tryAcquire(
                    ExtensionOperationKeys
                        .THEME_GLOBAL
                );
        } catch (RuntimeException error) {
            return ThemeInstallResult.failed(
                ThemeInstallStage.PREPARATION,
                ThemeInstallErrorCode
                    .UNEXPECTED_INSTALL_ERROR,
                "获取主题操作协调权失败："
                    + safeMessage(error),
                null,
                null,
                true
            );
        }

        if (operationAttempt.isEmpty()) {
            return ThemeInstallResult.rejected(
                ThemeInstallStage.PREPARATION,
                ThemeInstallErrorCode
                    .THEME_OPERATION_BUSY,
                "当前已经有其他冲突主题操作运行。",
                null,
                null
            );
        }

        try (
            ExtensionOperationHandle ignored =
                operationAttempt.orElseThrow()
        ) {
            ThemeWorkspaceCleanupResult
                cleanupResult =
                    workspaceCleaner.cleanupStale(
                        STALE_WORKSPACE_MAXIMUM_AGE
                    );

            if (!cleanupResult.success()) {
                return ThemeInstallResult.failed(
                    ThemeInstallStage.CLEANUP,
                    ThemeInstallErrorCode
                        .CLEANUP_FAILED,
                    "清理过期主题安装临时目录失败："
                        + String.join(
                            "；",
                            cleanupResult.failures()
                        ),
                    null,
                    null,
                    false
                );
            }

            return installWithinOperation(
                packagePath,
                policy
            );
        }
    }

    /**
     * 已取得全局主题操作协调权时执行正式安装。
     */
    private ThemeInstallResult installWithinOperation(
        Path packagePath,
        ThemeInstallPolicy policy
    ) {
        final ThemePackageValidationResult
            validationResult;

        try {
            validationResult =
                packageValidator.validate(
                    packagePath
                );
        } catch (RuntimeException error) {
            return ThemeInstallResult.failed(
                ThemeInstallStage.VALIDATION,
                ThemeInstallErrorCode
                    .UNEXPECTED_INSTALL_ERROR,
                "执行主题包安全校验失败："
                    + safeMessage(error),
                null,
                null,
                true
            );
        }

        if (!validationResult.valid()) {
            return ThemeInstallResult.rejected(
                ThemeInstallStage.VALIDATION,
                ThemeInstallErrorCode
                    .PACKAGE_VALIDATION_FAILED,
                validationFailureMessage(
                    validationResult
                ),
                validationResult.manifest(),
                validationResult
            );
        }

        ThemeManifest manifest =
            validationResult.manifest();

        if (manifest == null) {
            return ThemeInstallResult.rejected(
                ThemeInstallStage.VALIDATION,
                ThemeInstallErrorCode
                    .MANIFEST_UNAVAILABLE,
                "主题包校验结果中没有可用主题清单。",
                null,
                validationResult
            );
        }

        if (
            policy.rejectPackageWarnings()
                && validationResult
                    .hasWarnings()
        ) {
            return ThemeInstallResult.rejected(
                ThemeInstallStage.VALIDATION,
                ThemeInstallErrorCode
                    .PACKAGE_WARNING_REJECTED,
                "严格安装策略拒绝包含警告的主题包。",
                manifest,
                validationResult
            );
        }

        final Path themesDirectory;

        try {
            workDirResolver
                .ensureBaseDirectories();

            themesDirectory =
                workDirResolver
                    .themesDir()
                    .toAbsolutePath()
                    .normalize();

            if (
                Files.isSymbolicLink(
                    themesDirectory
                )
                    || !Files.isDirectory(
                        themesDirectory,
                        LinkOption.NOFOLLOW_LINKS
                    )
            ) {
                return ThemeInstallResult.failed(
                    ThemeInstallStage.PREPARATION,
                    ThemeInstallErrorCode
                        .TARGET_DIRECTORY_CREATE_FAILED,
                    "正式主题根目录不是安全普通目录："
                        + themesDirectory,
                    manifest,
                    validationResult,
                    true
                );
            }
        } catch (RuntimeException error) {
            return ThemeInstallResult.failed(
                ThemeInstallStage.PREPARATION,
                ThemeInstallErrorCode
                    .TARGET_DIRECTORY_CREATE_FAILED,
                "准备正式主题目录失败："
                    + safeMessage(error),
                manifest,
                validationResult,
                true
            );
        }

        Path targetDirectory =
            themesDirectory
                .resolve(manifest.id())
                .normalize();

        if (
            !fileOperations.isWithin(
                themesDirectory,
                targetDirectory
            )
                || targetDirectory.getParent()
                    == null
                || !targetDirectory
                    .getParent()
                    .equals(themesDirectory)
        ) {
            return ThemeInstallResult.rejected(
                ThemeInstallStage.PREPARATION,
                ThemeInstallErrorCode
                    .TARGET_PATH_INVALID,
                "主题正式目标目录不安全。",
                manifest,
                validationResult
            );
        }

        final List<ThemeDescriptor>
            installedThemes;

        try {
            installedThemes =
                themeScanner
                    .scanInstalledThemes();
        } catch (RuntimeException error) {
            return ThemeInstallResult.failed(
                ThemeInstallStage.PREPARATION,
                ThemeInstallErrorCode
                    .UNEXPECTED_INSTALL_ERROR,
                "扫描已有主题失败："
                    + safeMessage(error),
                manifest,
                validationResult,
                true
            );
        }

        if (
            Files.exists(
                targetDirectory,
                LinkOption.NOFOLLOW_LINKS
            )
                || containsTheme(
                    installedThemes,
                    manifest.id()
                )
        ) {
            return ThemeInstallResult.rejected(
                ThemeInstallStage.PREPARATION,
                ThemeInstallErrorCode
                    .THEME_ALREADY_INSTALLED,
                "主题已经安装，当前安装服务禁止覆盖："
                    + manifest.id(),
                manifest,
                validationResult
            );
        }

        ThemeInstallResult
            parentValidationResult =
                validateParentTheme(
                    manifest,
                    installedThemes,
                    validationResult
                );

        if (parentValidationResult != null) {
            return parentValidationResult;
        }

        final ThemeExtractionResult
            extractionResult;

        try {
            extractionResult =
                archiveExtractor.extract(
                    packagePath,
                    validationResult
                );
        } catch (
            ThemeArchiveExtractionException
                error
        ) {
            return convertExtractionFailure(
                error,
                manifest,
                validationResult
            );
        } catch (RuntimeException error) {
            return ThemeInstallResult.failed(
                ThemeInstallStage.EXTRACTION,
                ThemeInstallErrorCode
                    .UNEXPECTED_INSTALL_ERROR,
                "临时解压主题包失败："
                    + safeMessage(error),
                manifest,
                validationResult,
                false
            );
        }

        /*
         * 解压可能持续一段时间。
         * 正式移动前必须再次检查目标，
         * 防止外部程序或异常文件操作在此期间创建同名目录。
         */
        if (
            Files.exists(
                targetDirectory,
                LinkOption.NOFOLLOW_LINKS
            )
        ) {
            boolean cleaned =
                cleanupWorkspace(
                    extractionResult
                        .workspacePath()
                );

            if (!cleaned) {
                return ThemeInstallResult.failed(
                    ThemeInstallStage.CLEANUP,
                    ThemeInstallErrorCode
                        .CLEANUP_FAILED,
                    "发现同名主题后无法清理临时工作目录。",
                    manifest,
                    validationResult,
                    false
                );
            }

            return ThemeInstallResult.rejected(
                ThemeInstallStage.COMMIT,
                ThemeInstallErrorCode
                    .THEME_ALREADY_INSTALLED,
                "主题安装提交前发现同名主题，已拒绝覆盖："
                    + manifest.id(),
                manifest,
                validationResult
            );
        }

        final boolean atomicMoveUsed;

        try {
            atomicMoveUsed =
                fileOperations.moveDirectory(
                    extractionResult
                        .extractedThemePath(),
                    targetDirectory,
                    policy.requireAtomicMove()
                );
        } catch (
            FileAlreadyExistsException error
        ) {
            boolean cleaned =
                cleanupWorkspace(
                    extractionResult
                        .workspacePath()
                );

            if (!cleaned) {
                return ThemeInstallResult.failed(
                    ThemeInstallStage.CLEANUP,
                    ThemeInstallErrorCode
                        .CLEANUP_FAILED,
                    "同名主题冲突后无法清理临时工作目录。",
                    manifest,
                    validationResult,
                    false
                );
            }

            return ThemeInstallResult.rejected(
                ThemeInstallStage.COMMIT,
                ThemeInstallErrorCode
                    .THEME_ALREADY_INSTALLED,
                "主题正式目录已经存在，已拒绝覆盖："
                    + manifest.id(),
                manifest,
                validationResult
            );
        } catch (
            AtomicMoveNotSupportedException
                error
        ) {
            boolean cleaned =
                cleanupWorkspace(
                    extractionResult
                        .workspacePath()
                );

            return ThemeInstallResult.failed(
                ThemeInstallStage.COMMIT,
                ThemeInstallErrorCode
                    .ATOMIC_MOVE_REQUIRED_BUT_UNAVAILABLE,
                "当前文件系统不支持严格策略要求的原子移动："
                    + safeMessage(error),
                manifest,
                validationResult,
                cleaned
            );
        } catch (IOException error) {
            boolean cleaned =
                cleanupWorkspace(
                    extractionResult
                        .workspacePath()
                );

            return ThemeInstallResult.failed(
                ThemeInstallStage.COMMIT,
                ThemeInstallErrorCode
                    .INSTALL_MOVE_FAILED,
                "把主题提交到正式目录失败："
                    + safeMessage(error),
                manifest,
                validationResult,
                cleaned
            );
        }

        final boolean scanConfirmed;

        try {
            scanConfirmed =
                confirmInstalledTheme(
                    manifest,
                    targetDirectory
                );
        } catch (RuntimeException error) {
            return rollbackAfterCommit(
                targetDirectory,
                extractionResult
                    .workspacePath(),
                manifest,
                validationResult,
                ThemeInstallErrorCode
                    .POST_INSTALL_SCAN_FAILED,
                "主题提交后重新扫描失败："
                    + safeMessage(error)
            );
        }

        if (!scanConfirmed) {
            return rollbackAfterCommit(
                targetDirectory,
                extractionResult
                    .workspacePath(),
                manifest,
                validationResult,
                ThemeInstallErrorCode
                    .POST_INSTALL_SCAN_FAILED,
                "主题提交后没有通过 ThemeScanner 二次确认。"
            );
        }

        if (
            !cleanupWorkspace(
                extractionResult.workspacePath()
            )
        ) {
            return rollbackAfterCommit(
                targetDirectory,
                extractionResult
                    .workspacePath(),
                manifest,
                validationResult,
                ThemeInstallErrorCode
                    .CLEANUP_FAILED,
                "主题提交成功后无法清理临时工作目录。"
            );
        }

        return ThemeInstallResult.installed(
            manifest,
            targetDirectory.toString(),
            validationResult.sha256(),
            atomicMoveUsed,
            validationResult
        );
    }

    /**
     * 校验父主题存在性和模板引擎。
     */
    private ThemeInstallResult
        validateParentTheme(
            ThemeManifest manifest,
            List<ThemeDescriptor>
                installedThemes,
            ThemePackageValidationResult
                validationResult
        ) {

        if (!manifest.hasParent()) {
            return null;
        }

        ThemeDescriptor parentTheme =
            installedThemes
                .stream()
                .filter(
                    theme ->
                        theme.name().equals(
                            manifest.parent()
                        )
                )
                .findFirst()
                .orElse(null);

        if (parentTheme == null) {
            return ThemeInstallResult.rejected(
                ThemeInstallStage.PREPARATION,
                ThemeInstallErrorCode
                    .PARENT_THEME_NOT_INSTALLED,
                "子主题依赖的父主题尚未安装："
                    + manifest.parent(),
                manifest,
                validationResult
            );
        }

        if (
            !parentTheme.engine().equals(
                manifest.engine()
            )
        ) {
            return ThemeInstallResult.rejected(
                ThemeInstallStage.PREPARATION,
                ThemeInstallErrorCode
                    .PARENT_THEME_ENGINE_MISMATCH,
                "子主题与父主题模板引擎不一致："
                    + manifest.engine()
                    + " / "
                    + parentTheme.engine(),
                manifest,
                validationResult
            );
        }

        return null;
    }

    /**
     * 判断主题列表中是否已经存在指定 ID。
     */
    private boolean containsTheme(
        List<ThemeDescriptor> themes,
        String themeId
    ) {
        return themes
            .stream()
            .anyMatch(
                theme ->
                    theme.name().equals(
                        themeId
                    )
            );
    }

    /**
     * 使用 ThemeScanner 确认提交后的主题。
     */
    private boolean confirmInstalledTheme(
        ThemeManifest manifest,
        Path targetDirectory
    ) {
        List<ThemeDescriptor> matches =
            themeScanner
                .scanInstalledThemes()
                .stream()
                .filter(
                    theme ->
                        theme.name().equals(
                            manifest.id()
                        )
                )
                .toList();

        if (matches.size() != 1) {
            return false;
        }

        ThemeDescriptor descriptor =
            matches.get(0);

        Path scannedDirectory =
            Path.of(
                descriptor.themeDir()
            )
                .toAbsolutePath()
                .normalize();

        return scannedDirectory.equals(
                targetDirectory
                    .toAbsolutePath()
                    .normalize()
            )
            && descriptor.version().equals(
                manifest.version()
            )
            && descriptor.engine().equals(
                manifest.engine()
            )
            && Objects.equals(
                descriptor.parent(),
                manifest.parent()
            );
    }

    /**
     * 把临时解压异常转换为正式安装结果。
     */
    private ThemeInstallResult
        convertExtractionFailure(
            ThemeArchiveExtractionException
                error,
            ThemeManifest manifest,
            ThemePackageValidationResult
                validationResult
        ) {

        if (
            error.temporaryDirectoryCleaned()
                && isPackageRejection(
                    error.errorCode()
                )
        ) {
            return ThemeInstallResult.rejected(
                error.stage(),
                error.errorCode(),
                error.getMessage(),
                manifest,
                validationResult
            );
        }

        return ThemeInstallResult.failed(
            error.stage(),
            error.errorCode(),
            error.getMessage(),
            manifest,
            validationResult,
            error.temporaryDirectoryCleaned()
        );
    }

    /**
     * 判断错误是否属于主题包内容拒绝。
     */
    private boolean isPackageRejection(
        ThemeInstallErrorCode errorCode
    ) {
        return errorCode
                == ThemeInstallErrorCode
                    .PACKAGE_VALIDATION_FAILED
            || errorCode
                == ThemeInstallErrorCode
                    .MANIFEST_UNAVAILABLE
            || errorCode
                == ThemeInstallErrorCode
                    .PACKAGE_CHANGED_AFTER_VALIDATION
            || errorCode
                == ThemeInstallErrorCode
                    .EXTRACTED_CONTENT_INVALID
            || errorCode
                == ThemeInstallErrorCode
                    .EXTRACTED_MANIFEST_MISMATCH;
    }

    /**
     * 提交后验证失败时删除刚安装的主题和临时目录。
     */
    private ThemeInstallResult rollbackAfterCommit(
        Path targetDirectory,
        Path workspaceDirectory,
        ThemeManifest manifest,
        ThemePackageValidationResult
            validationResult,
        ThemeInstallErrorCode originalCode,
        String originalMessage
    ) {
        boolean cleaned =
            rollbackCommittedTheme(
                targetDirectory,
                workspaceDirectory
            );

        if (!cleaned) {
            return ThemeInstallResult.failed(
                ThemeInstallStage.CLEANUP,
                ThemeInstallErrorCode
                    .CLEANUP_FAILED,
                originalMessage
                    + " 同时无法完整删除新主题或临时目录，"
                    + "请检查："
                    + targetDirectory,
                manifest,
                validationResult,
                false
            );
        }

        return ThemeInstallResult.failed(
            ThemeInstallStage.POST_VALIDATION,
            originalCode,
            originalMessage
                + " 新提交主题已经回滚。",
            manifest,
            validationResult,
            true
        );
    }

    /**
     * 删除本次新提交的主题以及临时工作目录。
     */
    private boolean rollbackCommittedTheme(
        Path targetDirectory,
        Path workspaceDirectory
    ) {
        boolean success = true;

        try {
            fileOperations.deleteRecursively(
                targetDirectory
            );
        } catch (IOException error) {
            success = false;
        }

        try {
            fileOperations.deleteRecursively(
                workspaceDirectory
            );
        } catch (IOException error) {
            success = false;
        }

        return success
            && !Files.exists(
                targetDirectory,
                LinkOption.NOFOLLOW_LINKS
            )
            && !Files.exists(
                workspaceDirectory,
                LinkOption.NOFOLLOW_LINKS
            );
    }

    /**
     * 删除临时安装工作目录。
     */
    private boolean cleanupWorkspace(
        Path workspaceDirectory
    ) {
        try {
            fileOperations.deleteRecursively(
                workspaceDirectory
            );

            return !Files.exists(
                workspaceDirectory,
                LinkOption.NOFOLLOW_LINKS
            );
        } catch (IOException error) {
            return false;
        }
    }

    /**
     * 创建主题包校验失败说明。
     */
    private String validationFailureMessage(
        ThemePackageValidationResult
            validationResult
    ) {
        if (
            validationResult == null
                || validationResult.errors()
                    .isEmpty()
        ) {
            return "主题包没有通过安全校验。";
        }

        return "主题包没有通过安全校验："
            + validationResult
                .errors()
                .get(0)
                .message();
    }

    /**
     * 获取安全异常说明。
     */
    private String safeMessage(
        Throwable error
    ) {
        if (
            error == null
                || error.getMessage() == null
                || error.getMessage().isBlank()
        ) {
            return error == null
                ? "未知错误"
                : error
                    .getClass()
                    .getSimpleName();
        }

        return error.getMessage();
    }
}
