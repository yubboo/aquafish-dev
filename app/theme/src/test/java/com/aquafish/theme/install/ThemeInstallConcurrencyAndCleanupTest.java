package com.aquafish.theme.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.core.operation.ExtensionOperationCoordinator;
import com.aquafish.core.operation.ExtensionOperationHandle;
import com.aquafish.core.operation.ExtensionOperationKeys;
import com.aquafish.core.operation.InMemoryExtensionOperationCoordinator;
import com.aquafish.theme.core.ThemeScanner;
import com.aquafish.theme.manifest.ThemeManifestParser;
import com.aquafish.theme.validation.ThemePackageValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 主题操作协调和过期安装目录清理测试。
 */
class ThemeInstallConcurrencyAndCleanupTest {

    /**
     * 每个测试独立临时目录。
     */
    @TempDir
    Path temporaryDirectory;

    /**
     * 已有冲突主题操作时，
     * ThemeInstallService 应立即返回繁忙结果。
     *
     * @throws Exception 测试主题包创建失败
     */
    @Test
    void shouldRejectWhenThemeOperationIsBusy()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "operation-busy-workdir"
            );

        Path zipFile =
            temporaryDirectory.resolve(
                "operation-busy-theme.zip"
            );

        createThemeZip(
            zipFile,
            "operation-busy-theme"
        );

        ExtensionOperationCoordinator
            coordinator =
                new InMemoryExtensionOperationCoordinator();

        Optional<ExtensionOperationHandle>
            heldOperation =
                coordinator.tryAcquire(
                    ExtensionOperationKeys
                        .THEME_GLOBAL
                );

        assertTrue(heldOperation.isPresent());

        try (
            ExtensionOperationHandle ignored =
                heldOperation.orElseThrow()
        ) {
            ThemeInstallResult result =
                createService(
                    workDir,
                    coordinator
                ).install(zipFile);

            assertTrue(result.rejected());

            assertEquals(
                ThemeInstallErrorCode
                    .THEME_OPERATION_BUSY,
                result.errorCode()
            );

            assertFalse(
                Files.exists(
                    workDir
                        .resolve("themes")
                        .resolve(
                            "operation-busy-theme"
                        )
                )
            );
        }
    }

    /**
     * 过期工作目录会删除，
     * 新鲜工作目录不会被误删。
     *
     * @throws Exception 文件系统测试失败
     */
    @Test
    void shouldDeleteStaleWorkspaceButKeepFreshWorkspace()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "cleanup-workdir"
            );

        WorkDirResolver resolver =
            createWorkDirResolver(workDir);

        ThemeInstallFileOperations
            fileOperations =
                new ThemeInstallFileOperations();

        ThemeInstallWorkspaceCleaner cleaner =
            new ThemeInstallWorkspaceCleaner(
                resolver,
                fileOperations
            );

        Path installTempRoot =
            cleaner.installTempRoot();

        Path staleWorkspace =
            installTempRoot.resolve(
                "stale-theme-old"
            );

        Path freshWorkspace =
            installTempRoot.resolve(
                "fresh-theme-new"
            );

        Files.createDirectories(
            staleWorkspace.resolve("theme")
        );

        Files.createDirectories(
            freshWorkspace.resolve("theme")
        );

        Files.writeString(
            staleWorkspace
                .resolve("theme")
                .resolve("old.txt"),
            "old",
            StandardCharsets.UTF_8
        );

        Files.writeString(
            freshWorkspace
                .resolve("theme")
                .resolve("new.txt"),
            "new",
            StandardCharsets.UTF_8
        );

        Files.setLastModifiedTime(
            staleWorkspace,
            FileTime.from(
                Instant.now().minus(
                    Duration.ofHours(48)
                )
            )
        );

        Files.setLastModifiedTime(
            freshWorkspace,
            FileTime.from(
                Instant.now()
            )
        );

        ThemeWorkspaceCleanupResult result =
            cleaner.cleanupStale(
                Duration.ofHours(24)
            );

        assertTrue(
            result.success(),
            result.failures().toString()
        );

        assertEquals(
            2,
            result.scannedDirectories()
        );

        assertEquals(
            1,
            result.deletedDirectories()
        );

        assertFalse(
            Files.exists(staleWorkspace)
        );

        assertTrue(
            Files.isDirectory(
                freshWorkspace
            )
        );
    }

    /**
     * 正式安装开始前会清理崩溃遗留的过期目录。
     *
     * @throws Exception 安装测试失败
     */
    @Test
    void shouldCleanupStaleWorkspaceBeforeInstall()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "cleanup-before-install"
            );

        WorkDirResolver resolver =
            createWorkDirResolver(workDir);

        ThemeInstallWorkspaceCleaner cleaner =
            new ThemeInstallWorkspaceCleaner(
                resolver,
                new ThemeInstallFileOperations()
            );

        Path staleWorkspace =
            cleaner
                .installTempRoot()
                .resolve("crashed-theme-old");

        Files.createDirectories(
            staleWorkspace.resolve("theme")
        );

        Files.writeString(
            staleWorkspace
                .resolve("theme")
                .resolve("partial.txt"),
            "partial",
            StandardCharsets.UTF_8
        );

        Files.setLastModifiedTime(
            staleWorkspace,
            FileTime.from(
                Instant.now().minus(
                    Duration.ofHours(72)
                )
            )
        );

        Path zipFile =
            temporaryDirectory.resolve(
                "cleanup-success-theme.zip"
            );

        createThemeZip(
            zipFile,
            "cleanup-success-theme"
        );

        ThemeInstallResult result =
            createService(
                workDir,
                new InMemoryExtensionOperationCoordinator()
            ).install(zipFile);

        assertTrue(
            result.success(),
            result.message()
        );

        assertFalse(
            Files.exists(staleWorkspace)
        );

        assertTrue(
            Files.isDirectory(
                workDir
                    .resolve("themes")
                    .resolve(
                        "cleanup-success-theme"
                    )
            )
        );
    }

    /**
     * 安装完成后必须释放主题操作协调权，
     * 同一个服务可以继续安装第二个主题。
     *
     * @throws Exception 安装测试失败
     */
    @Test
    void shouldReleaseOperationAfterInstall()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "operation-release-workdir"
            );

        ThemeInstallService service =
            createService(
                workDir,
                new InMemoryExtensionOperationCoordinator()
            );

        Path firstZip =
            temporaryDirectory.resolve(
                "first-theme.zip"
            );

        Path secondZip =
            temporaryDirectory.resolve(
                "second-theme.zip"
            );

        createThemeZip(
            firstZip,
            "first-theme"
        );

        createThemeZip(
            secondZip,
            "second-theme"
        );

        ThemeInstallResult firstResult =
            service.install(firstZip);

        ThemeInstallResult secondResult =
            service.install(secondZip);

        assertTrue(
            firstResult.success(),
            firstResult.message()
        );

        assertTrue(
            secondResult.success(),
            secondResult.message()
        );

        assertTrue(
            Files.isDirectory(
                workDir
                    .resolve("themes")
                    .resolve("first-theme")
            )
        );

        assertTrue(
            Files.isDirectory(
                workDir
                    .resolve("themes")
                    .resolve("second-theme")
            )
        );
    }

    /**
     * 主题安装不再创建 .install.lock 文件。
     *
     * @throws Exception 安装测试失败
     */
    @Test
    void shouldNotCreateThemeInstallLockFile()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "no-lock-file-workdir"
            );

        Path zipFile =
            temporaryDirectory.resolve(
                "no-lock-file-theme.zip"
            );

        createThemeZip(
            zipFile,
            "no-lock-file-theme"
        );

        ThemeInstallResult result =
            createService(
                workDir,
                new InMemoryExtensionOperationCoordinator()
            ).install(zipFile);

        assertTrue(
            result.success(),
            result.message()
        );

        assertFalse(
            Files.exists(
                workDir
                    .resolve("storage")
                    .resolve("temp")
                    .resolve("theme-install")
                    .resolve(".install.lock")
            )
        );
    }

    /**
     * 创建完整安装服务。
     */
    private ThemeInstallService createService(
        Path workDir,
        ExtensionOperationCoordinator
            coordinator
    ) {
        WorkDirResolver resolver =
            createWorkDirResolver(workDir);

        ThemeManifestParser parser =
            new ThemeManifestParser();

        ThemeInstallFileOperations
            fileOperations =
                new ThemeInstallFileOperations();

        ThemePackageValidator validator =
            new ThemePackageValidator();

        ThemeArchiveExtractor extractor =
            new ThemeArchiveExtractor(
                resolver,
                parser,
                fileOperations
            );

        ThemeScanner scanner =
            new ThemeScanner(
                resolver,
                parser
            );

        ThemeInstallWorkspaceCleaner cleaner =
            new ThemeInstallWorkspaceCleaner(
                resolver,
                fileOperations
            );

        return new ThemeInstallService(
            validator,
            extractor,
            resolver,
            scanner,
            fileOperations,
            coordinator,
            cleaner
        );
    }

    /**
     * 创建测试 WorkDirResolver。
     */
    private WorkDirResolver
        createWorkDirResolver(
            Path workDir
        ) {

        AquafishProperties properties =
            new AquafishProperties(
                workDir.toString(),
                "http://127.0.0.1:8080",
                "aq_",
                "default"
            );

        return new WorkDirResolver(
            properties
        );
    }

    /**
     * 创建合法主题 ZIP。
     */
    private void createThemeZip(
        Path zipFile,
        String themeId
    ) throws Exception {

        String prefix =
            themeId + "/";

        createZip(
            zipFile,
            List.of(
                entry(
                    prefix + "theme.yaml",
                    """
                    id: %s
                    title: %s
                    version: 1.0.0
                    engine: thymeleaf
                    author:
                      name: Aquafish Test
                    apiVersion: 1
                    description: 统一扩展操作协调器测试主题。
                    """.formatted(
                        themeId,
                        themeId
                    )
                ),
                entry(
                    prefix
                        + "templates/index.html",
                    "<html>"
                        + themeId
                        + "</html>"
                ),
                entry(
                    prefix
                        + "assets/css/style.css",
                    "body {}"
                )
            )
        );
    }

    /**
     * 创建测试 ZIP 条目。
     */
    private TestZipEntry entry(
        String name,
        String content
    ) {
        return new TestZipEntry(
            name,
            content
        );
    }

    /**
     * 创建真实 ZIP。
     */
    private void createZip(
        Path zipFile,
        List<TestZipEntry> entries
    ) throws Exception {

        Files.deleteIfExists(zipFile);

        try (
            ZipArchiveOutputStream output =
                new ZipArchiveOutputStream(
                    zipFile.toFile()
                )
        ) {
            for (
                TestZipEntry definition
                : entries
            ) {
                ZipArchiveEntry entry =
                    new ZipArchiveEntry(
                        definition.name()
                    );

                output.putArchiveEntry(entry);

                byte[] bytes =
                    definition.content()
                        .getBytes(
                            StandardCharsets.UTF_8
                        );

                output.write(bytes);
                output.closeArchiveEntry();
            }

            output.finish();
        }
    }

    /**
     * 测试 ZIP 条目。
     */
    private record TestZipEntry(
        String name,
        String content
    ) {
    }
}
