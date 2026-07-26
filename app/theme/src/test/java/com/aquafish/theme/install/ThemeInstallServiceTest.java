package com.aquafish.theme.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.theme.core.ThemeDescriptor;
import com.aquafish.theme.core.ThemeScanner;
import com.aquafish.theme.manifest.ThemeManifestParser;
import com.aquafish.theme.validation.ThemePackageValidator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ThemeInstallService 正式安装流程测试。
 */
class ThemeInstallServiceTest {

    /**
     * 每个测试使用独立文件系统。
     */
    @TempDir
    Path temporaryDirectory;

    /**
     * 验证包装目录主题能够安装到 manifest.id 对应目录。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldInstallWrappedTheme()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "workdir-wrapped"
            );

        Path zipFile =
            temporaryDirectory.resolve(
                "wrapped-theme.zip"
            );

        createThemeZip(
            zipFile,
            "package-folder",
            "sample-theme",
            "thymeleaf",
            null,
            false
        );

        ThemeInstallResult result =
            createService(
                workDir,
                new ThemeInstallFileOperations()
            ).install(zipFile);

        assertTrue(
            result.success(),
            result.message()
        );

        assertEquals(
            "sample-theme",
            result.themeId()
        );

        Path installedDirectory =
            workDir
                .resolve("themes")
                .resolve("sample-theme");

        assertEquals(
            installedDirectory
                .toAbsolutePath()
                .normalize(),
            Path.of(
                result.installedDirectory()
            )
                .toAbsolutePath()
                .normalize()
        );

        assertTrue(
            Files.isRegularFile(
                installedDirectory.resolve(
                    "theme.yaml"
                )
            )
        );

        assertTrue(
            Files.isRegularFile(
                installedDirectory.resolve(
                    "templates/index.html"
                )
            )
        );

        assertFalse(
            Files.exists(
                installedDirectory.resolve(
                    "package-folder"
                )
            )
        );

        assertEquals(
            0L,
            installationWorkspaceCount(
                workDir
            )
        );
    }

    /**
     * 验证平铺主题包能够安装。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldInstallFlatTheme()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "workdir-flat"
            );

        Path zipFile =
            temporaryDirectory.resolve(
                "flat-theme.zip"
            );

        createThemeZip(
            zipFile,
            "",
            "flat-theme",
            "thymeleaf",
            null,
            false
        );

        ThemeInstallResult result =
            createService(
                workDir,
                new ThemeInstallFileOperations()
            ).install(zipFile);

        assertTrue(
            result.success(),
            result.message()
        );

        assertTrue(
            Files.isRegularFile(
                workDir
                    .resolve("themes")
                    .resolve("flat-theme")
                    .resolve("theme.yaml")
            )
        );
    }

    /**
     * 验证非法主题包在创建正式目录前被拒绝。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectInvalidPackage()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "workdir-invalid"
            );

        Path zipFile =
            temporaryDirectory.resolve(
                "invalid-theme.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "invalid/index.html",
                    "<html></html>"
                )
            )
        );

        ThemeInstallResult result =
            createService(
                workDir,
                new ThemeInstallFileOperations()
            ).install(zipFile);

        assertTrue(result.rejected());

        assertEquals(
            ThemeInstallErrorCode
                .PACKAGE_VALIDATION_FAILED,
            result.errorCode()
        );

        assertEquals(
            0L,
            installedThemeCount(workDir)
        );
    }

    /**
     * 验证已有主题不能被覆盖。
     *
     * @throws Exception 文件创建失败
     */
    @Test
    void shouldRejectAlreadyInstalledTheme()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "workdir-existing"
            );

        createInstalledTheme(
            workDir,
            "sample-theme",
            "thymeleaf",
            null
        );

        Path existingFile =
            workDir
                .resolve("themes")
                .resolve("sample-theme")
                .resolve("existing.txt");

        Files.writeString(
            existingFile,
            "do-not-overwrite",
            StandardCharsets.UTF_8
        );

        Path zipFile =
            temporaryDirectory.resolve(
                "existing-theme.zip"
            );

        createThemeZip(
            zipFile,
            "sample-theme",
            "sample-theme",
            "thymeleaf",
            null,
            false
        );

        ThemeInstallResult result =
            createService(
                workDir,
                new ThemeInstallFileOperations()
            ).install(zipFile);

        assertTrue(result.rejected());

        assertEquals(
            ThemeInstallErrorCode
                .THEME_ALREADY_INSTALLED,
            result.errorCode()
        );

        assertEquals(
            "do-not-overwrite",
            Files.readString(
                existingFile,
                StandardCharsets.UTF_8
            )
        );

        assertEquals(
            0L,
            installationWorkspaceCount(
                workDir
            )
        );
    }

    /**
     * 验证父主题未安装时拒绝子主题。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectMissingParentTheme()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "workdir-parent-missing"
            );

        Path zipFile =
            temporaryDirectory.resolve(
                "child-missing-parent.zip"
            );

        createThemeZip(
            zipFile,
            "child-theme",
            "child-theme",
            "thymeleaf",
            "parent-theme",
            false
        );

        ThemeInstallResult result =
            createService(
                workDir,
                new ThemeInstallFileOperations()
            ).install(zipFile);

        assertTrue(result.rejected());

        assertEquals(
            ThemeInstallErrorCode
                .PARENT_THEME_NOT_INSTALLED,
            result.errorCode()
        );

        assertFalse(
            Files.exists(
                workDir
                    .resolve("themes")
                    .resolve("child-theme")
            )
        );
    }

    /**
     * 验证父子主题模板引擎不同时拒绝安装。
     *
     * @throws Exception 文件创建失败
     */
    @Test
    void shouldRejectParentEngineMismatch()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "workdir-parent-engine"
            );

        createInstalledTheme(
            workDir,
            "parent-theme",
            "thymeleaf",
            null
        );

        Path zipFile =
            temporaryDirectory.resolve(
                "child-engine-mismatch.zip"
            );

        createThemeZip(
            zipFile,
            "child-theme",
            "child-theme",
            "pebble",
            "parent-theme",
            false
        );

        ThemeInstallResult result =
            createService(
                workDir,
                new ThemeInstallFileOperations()
            ).install(zipFile);

        assertTrue(result.rejected());

        assertEquals(
            ThemeInstallErrorCode
                .PARENT_THEME_ENGINE_MISMATCH,
            result.errorCode()
        );
    }

    /**
     * 验证已安装且引擎一致的父主题允许子主题安装。
     *
     * @throws Exception 文件创建失败
     */
    @Test
    void shouldInstallChildWithCompatibleParent()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "workdir-child-success"
            );

        createInstalledTheme(
            workDir,
            "parent-theme",
            "thymeleaf",
            null
        );

        Path zipFile =
            temporaryDirectory.resolve(
                "child-theme.zip"
            );

        createThemeZip(
            zipFile,
            "child-theme",
            "child-theme",
            "thymeleaf",
            "parent-theme",
            false
        );

        ThemeInstallResult result =
            createService(
                workDir,
                new ThemeInstallFileOperations()
            ).install(zipFile);

        assertTrue(
            result.success(),
            result.message()
        );

        assertTrue(
            Files.isDirectory(
                workDir
                    .resolve("themes")
                    .resolve("child-theme")
            )
        );

        ThemeDescriptor installedChild =
            scanThemes(workDir)
                .stream()
                .filter(
                    theme ->
                        theme.name().equals(
                            "child-theme"
                        )
                )
                .findFirst()
                .orElseThrow();

        assertEquals(
            "parent-theme",
            installedChild.parent()
        );
    }

    /**
     * 验证严格策略拒绝带警告主题包。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectWarningsInStrictPolicy()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "workdir-warning-strict"
            );

        Path zipFile =
            temporaryDirectory.resolve(
                "warning-strict.zip"
            );

        createThemeZip(
            zipFile,
            "warning-theme",
            "warning-theme",
            "thymeleaf",
            null,
            true
        );

        ThemeInstallResult result =
            createService(
                workDir,
                new ThemeInstallFileOperations()
            ).install(
                zipFile,
                ThemeInstallPolicy.strict()
            );

        assertTrue(result.rejected());

        assertEquals(
            ThemeInstallErrorCode
                .PACKAGE_WARNING_REJECTED,
            result.errorCode()
        );

        assertFalse(
            Files.exists(
                workDir
                    .resolve("themes")
                    .resolve("warning-theme")
            )
        );
    }

    /**
     * 验证默认策略保留警告但允许安装。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldAllowWarningsInDefaultPolicy()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "workdir-warning-default"
            );

        Path zipFile =
            temporaryDirectory.resolve(
                "warning-default.zip"
            );

        createThemeZip(
            zipFile,
            "warning-theme",
            "warning-theme",
            "thymeleaf",
            null,
            true
        );

        ThemeInstallResult result =
            createService(
                workDir,
                new ThemeInstallFileOperations()
            ).install(zipFile);

        assertTrue(
            result.success(),
            result.message()
        );

        assertTrue(
            result.hasPackageWarnings()
        );
    }

    /**
     * 验证默认策略可以安全降级为普通移动。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldUseNonAtomicFallbackWhenAllowed()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "workdir-non-atomic"
            );

        Path zipFile =
            temporaryDirectory.resolve(
                "non-atomic-theme.zip"
            );

        createThemeZip(
            zipFile,
            "non-atomic-theme",
            "non-atomic-theme",
            "thymeleaf",
            null,
            false
        );

        ThemeInstallResult result =
            createService(
                workDir,
                new NonAtomicFileOperations()
            ).install(zipFile);

        assertTrue(
            result.success(),
            result.message()
        );

        assertFalse(
            result.atomicMoveUsed()
        );
    }

    /**
     * 验证严格策略要求原子移动时不允许降级。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldFailWhenStrictAtomicMoveUnavailable()
        throws Exception {

        Path workDir =
            temporaryDirectory.resolve(
                "workdir-atomic-required"
            );

        Path zipFile =
            temporaryDirectory.resolve(
                "atomic-required-theme.zip"
            );

        createThemeZip(
            zipFile,
            "atomic-theme",
            "atomic-theme",
            "thymeleaf",
            null,
            false
        );

        ThemeInstallResult result =
            createService(
                workDir,
                new NonAtomicFileOperations()
            ).install(
                zipFile,
                ThemeInstallPolicy.strict()
            );

        assertTrue(result.failed());

        assertEquals(
            ThemeInstallErrorCode
                .ATOMIC_MOVE_REQUIRED_BUT_UNAVAILABLE,
            result.errorCode()
        );

        assertTrue(
            result.temporaryDirectoryCleaned()
        );

        assertFalse(
            Files.exists(
                workDir
                    .resolve("themes")
                    .resolve("atomic-theme")
            )
        );

        assertEquals(
            0L,
            installationWorkspaceCount(
                workDir
            )
        );
    }

    /**
     * 创建正式测试服务。
     */
    private ThemeInstallService createService(
        Path workDir,
        ThemeInstallFileOperations
            fileOperations
    ) {
        WorkDirResolver workDirResolver =
            createWorkDirResolver(workDir);

        ThemeManifestParser parser =
            new ThemeManifestParser();

        ThemeScanner themeScanner =
            new ThemeScanner(
                workDirResolver,
                parser
            );

        ThemePackageValidator validator =
            new ThemePackageValidator();

        ThemeArchiveExtractor extractor =
            new ThemeArchiveExtractor(
                workDirResolver,
                parser,
                fileOperations
            );

        return new ThemeInstallService(
            validator,
            extractor,
            workDirResolver,
            themeScanner,
            fileOperations
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
     * 扫描测试 workdir 中的主题。
     */
    private List<ThemeDescriptor> scanThemes(
        Path workDir
    ) {
        WorkDirResolver resolver =
            createWorkDirResolver(workDir);

        return new ThemeScanner(
            resolver,
            new ThemeManifestParser()
        ).scanInstalledThemes();
    }

    /**
     * 创建一个已经安装的主题。
     */
    private void createInstalledTheme(
        Path workDir,
        String themeId,
        String engine,
        String parent
    ) throws Exception {

        Path themeDirectory =
            workDir
                .resolve("themes")
                .resolve(themeId);

        Files.createDirectories(
            themeDirectory.resolve(
                "templates"
            )
        );

        Files.writeString(
            themeDirectory.resolve(
                "theme.yaml"
            ),
            manifestYaml(
                themeId,
                engine,
                parent
            ),
            StandardCharsets.UTF_8
        );

        Files.writeString(
            themeDirectory
                .resolve("templates")
                .resolve("index.html"),
            "<html>installed</html>",
            StandardCharsets.UTF_8
        );
    }

    /**
     * 创建测试主题 ZIP。
     */
    private void createThemeZip(
        Path zipFile,
        String archiveRoot,
        String themeId,
        String engine,
        String parent,
        boolean includeWarningFile
    ) throws Exception {

        String prefix =
            archiveRoot == null
                || archiveRoot.isBlank()
                    ? ""
                    : archiveRoot + "/";

        List<TestZipEntry> entries =
            new java.util.ArrayList<>();

        entries.add(
            entry(
                prefix + "theme.yaml",
                manifestYaml(
                    themeId,
                    engine,
                    parent
                )
            )
        );

        entries.add(
            entry(
                prefix
                    + "templates/index.html",
                "<html>"
                    + themeId
                    + "</html>"
            )
        );

        entries.add(
            entry(
                prefix
                    + "assets/css/style.css",
                "body {}"
            )
        );

        if (includeWarningFile) {
            entries.add(
                entry(
                    prefix + ".DS_Store",
                    "metadata"
                )
            );
        }

        createZip(
            zipFile,
            entries
        );
    }

    /**
     * 生成合法 theme.yaml。
     */
    private String manifestYaml(
        String themeId,
        String engine,
        String parent
    ) {
        String parentLine =
            parent == null
                || parent.isBlank()
                    ? ""
                    : "parent: "
                        + parent
                        + "\n";

        return """
            id: %s
            title: %s
            version: 1.0.0
            engine: %s
            author:
              name: Aquafish Test
            %sapiVersion: 1
            description: ThemeInstallService 测试主题。
            """.formatted(
                themeId,
                themeId,
                engine,
                parentLine
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
            for (TestZipEntry definition : entries) {
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
     * 统计正式主题数量。
     */
    private long installedThemeCount(
        Path workDir
    ) throws Exception {

        Path themesDirectory =
            workDir.resolve("themes");

        if (!Files.isDirectory(themesDirectory)) {
            return 0L;
        }

        try (
            var stream =
                Files.list(themesDirectory)
        ) {
            return stream.count();
        }
    }

    /**
     * 统计临时安装工作目录数量。
     */
    private long installationWorkspaceCount(
        Path workDir
    ) throws Exception {

        Path installTempRoot =
            workDir
                .resolve("storage")
                .resolve("temp")
                .resolve("theme-install");

        if (!Files.isDirectory(installTempRoot)) {
            return 0L;
        }

        try (
            var stream =
                Files.list(installTempRoot)
        ) {
            /*
             * theme-install 目录只用于保存独立临时工作目录。
             * 这里只统计直接子目录，忽略意外出现的普通文件。
             */
            return stream
                .filter(Files::isDirectory)
                .count();
        }
    }

    /**
     * 强制模拟不支持原子移动的文件系统。
     */
    private static class
        NonAtomicFileOperations
        extends ThemeInstallFileOperations {

        /**
         * 默认策略执行普通移动；
         * 严格策略模拟不支持原子移动。
         */
        @Override
        public boolean moveDirectory(
            Path sourceDirectory,
            Path targetDirectory,
            boolean requireAtomicMove
        ) throws IOException {

            if (requireAtomicMove) {
                throw new AtomicMoveNotSupportedException(
                    sourceDirectory.toString(),
                    targetDirectory.toString(),
                    "测试模拟文件系统不支持原子移动"
                );
            }

            Files.move(
                sourceDirectory,
                targetDirectory
            );

            return false;
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
