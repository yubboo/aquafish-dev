package com.aquafish.theme.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.theme.manifest.ThemeAuthor;
import com.aquafish.theme.manifest.ThemeManifest;
import com.aquafish.theme.manifest.ThemeRequirements;
import com.aquafish.theme.validation.ThemePackageIssue;
import com.aquafish.theme.validation.ThemePackageIssueCode;
import com.aquafish.theme.validation.ThemePackageValidationResult;
import com.aquafish.theme.validation.ThemePackageValidator;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 主题 ZIP 临时安全解压测试。
 */
class ThemeArchiveExtractorTest {

    /**
     * 每个测试独立临时目录。
     */
    @TempDir
    Path temporaryDirectory;

    /**
     * 验证单层主题目录包装能够被剥离。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldExtractWrappedTheme()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "wrapped-theme.zip"
            );

        List<TestZipEntry> entries =
            List.of(
                entry(
                    "sample-theme/theme.yaml",
                    validManifest(
                        "sample-theme"
                    )
                ),
                entry(
                    "sample-theme/templates/index.html",
                    "<html>wrapped</html>"
                ),
                entry(
                    "sample-theme/assets/css/style.css",
                    "body {}"
                )
            );

        createZip(
            zipFile,
            entries
        );

        ThemePackageValidationResult
            validationResult =
                new ThemePackageValidator()
                    .validate(zipFile);

        assertTrue(
            validationResult.valid(),
            () -> validationResult
                .issues()
                .toString()
        );

        ThemeExtractionResult result =
            newExtractor().extract(
                zipFile,
                validationResult
            );

        assertEquals(
            "sample-theme",
            result.themeId()
        );

        assertTrue(
            result.wrappedArchiveRoot()
        );

        assertEquals(
            validationResult.entryCount(),
            result.extractedEntryCount()
        );

        assertEquals(
            validationResult
                .totalUncompressedSize(),
            result.extractedBytes()
        );

        assertTrue(
            Files.isRegularFile(
                result
                    .extractedThemePath()
                    .resolve("theme.yaml")
            )
        );

        assertTrue(
            Files.isRegularFile(
                result
                    .extractedThemePath()
                    .resolve(
                        "templates/index.html"
                    )
            )
        );

        assertFalse(
            Files.exists(
                result
                    .extractedThemePath()
                    .resolve("sample-theme")
            )
        );
    }

    /**
     * 验证根目录平铺主题能够被解压。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldExtractFlatTheme()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "flat-theme.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "theme.yaml",
                    validManifest(
                        "flat-theme"
                    )
                ),
                entry(
                    "templates/index.html",
                    "<html>flat</html>"
                )
            )
        );

        ThemePackageValidationResult
            validationResult =
                new ThemePackageValidator()
                    .validate(zipFile);

        ThemeExtractionResult result =
            newExtractor().extract(
                zipFile,
                validationResult
            );

        assertFalse(
            result.wrappedArchiveRoot()
        );

        assertEquals(
            "flat-theme",
            result.manifest().id()
        );

        assertEquals(
            2,
            result.extractedFileCount()
        );
    }

    /**
     * 验证校验后修改 ZIP 会在创建工作目录前被拒绝。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectPackageChangedAfterValidation()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "changed-theme.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "changed-theme/theme.yaml",
                    validManifest(
                        "changed-theme"
                    )
                )
            )
        );

        ThemePackageValidationResult
            validationResult =
                new ThemePackageValidator()
                    .validate(zipFile);

        createZip(
            zipFile,
            List.of(
                entry(
                    "changed-theme/theme.yaml",
                    validManifest(
                        "changed-theme"
                    )
                ),
                entry(
                    "changed-theme/changed.txt",
                    "changed"
                )
            )
        );

        ThemeArchiveExtractionException error =
            assertThrows(
                ThemeArchiveExtractionException.class,
                () -> newExtractor().extract(
                    zipFile,
                    validationResult
                )
            );

        assertEquals(
            ThemeInstallErrorCode
                .PACKAGE_CHANGED_AFTER_VALIDATION,
            error.errorCode()
        );

        assertTrue(
            error.temporaryDirectoryCleaned()
        );

        assertEquals(
            0L,
            installationWorkspaceCount()
        );
    }

    /**
     * 验证条目数量和校验结果不一致时，
     * 已创建的独立工作目录会被删除。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldCleanWorkspaceWhenEntryCountMismatches()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "count-mismatch.zip"
            );

        List<TestZipEntry> entries =
            List.of(
                entry(
                    "count-theme/theme.yaml",
                    validManifest(
                        "count-theme"
                    )
                ),
                entry(
                    "count-theme/index.html",
                    "index"
                )
            );

        createZip(
            zipFile,
            entries
        );

        ThemeManifest manifest =
            createManifest(
                "count-theme"
            );

        ThemePackageValidationResult
            fakeValidationResult =
                manualValidation(
                    zipFile,
                    manifest,
                    "count-theme",
                    entries,
                    99
                );

        ThemeArchiveExtractionException error =
            assertThrows(
                ThemeArchiveExtractionException.class,
                () -> newExtractor().extract(
                    zipFile,
                    fakeValidationResult
                )
            );

        assertEquals(
            ThemeInstallErrorCode
                .EXTRACTED_CONTENT_INVALID,
            error.errorCode()
        );

        assertTrue(
            error.temporaryDirectoryCleaned()
        );

        assertEquals(
            0L,
            installationWorkspaceCount()
        );
    }

    /**
     * 验证解压阶段会独立重新检查路径穿越。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRecheckPathTraversalAndCleanWorkspace()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "traversal-theme.zip"
            );

        List<TestZipEntry> entries =
            List.of(
                entry(
                    "sample-theme/theme.yaml",
                    validManifest(
                        "sample-theme"
                    )
                ),
                entry(
                    "sample-theme/../outside.txt",
                    "outside"
                )
            );

        createZip(
            zipFile,
            entries
        );

        ThemeManifest manifest =
            createManifest(
                "sample-theme"
            );

        ThemePackageValidationResult
            fakeValidationResult =
                manualValidation(
                    zipFile,
                    manifest,
                    "sample-theme",
                    entries,
                    entries.size()
                );

        ThemeArchiveExtractionException error =
            assertThrows(
                ThemeArchiveExtractionException.class,
                () -> newExtractor().extract(
                    zipFile,
                    fakeValidationResult
                )
            );

        assertEquals(
            ThemeInstallErrorCode
                .EXTRACTED_CONTENT_INVALID,
            error.errorCode()
        );

        assertTrue(
            error.temporaryDirectoryCleaned()
        );

        assertFalse(
            Files.exists(
                temporaryDirectory.resolve(
                    "outside.txt"
                )
            )
        );

        assertEquals(
            0L,
            installationWorkspaceCount()
        );
    }

    /**
     * 验证解压后的清单与原始校验清单不一致时拒绝。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectExtractedManifestMismatch()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "manifest-mismatch.zip"
            );

        List<TestZipEntry> entries =
            List.of(
                entry(
                    "sample-theme/theme.yaml",
                    validManifest(
                        "sample-theme"
                    )
                )
            );

        createZip(
            zipFile,
            entries
        );

        ThemePackageValidationResult
            fakeValidationResult =
                manualValidation(
                    zipFile,
                    createManifest(
                        "different-theme"
                    ),
                    "sample-theme",
                    entries,
                    entries.size()
                );

        ThemeArchiveExtractionException error =
            assertThrows(
                ThemeArchiveExtractionException.class,
                () -> newExtractor().extract(
                    zipFile,
                    fakeValidationResult
                )
            );

        assertEquals(
            ThemeInstallErrorCode
                .EXTRACTED_MANIFEST_MISMATCH,
            error.errorCode()
        );

        assertTrue(
            error.temporaryDirectoryCleaned()
        );

        assertEquals(
            0L,
            installationWorkspaceCount()
        );
    }

    /**
     * 验证无效校验结果不会创建安装工作目录。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectInvalidValidationBeforeWorkspaceCreation()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "invalid-validation.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "theme.yaml",
                    validManifest(
                        "invalid-theme"
                    )
                )
            )
        );

        ThemePackageValidationResult
            invalidValidation =
                new ThemePackageValidationResult(
                    null,
                    "",
                    Files.size(zipFile),
                    1,
                    0L,
                    "",
                    List.of(
                        ThemePackageIssue.error(
                            ThemePackageIssueCode
                                .MANIFEST_MISSING,
                            "",
                            "缺少主题清单。"
                        )
                    )
                );

        ThemeArchiveExtractionException error =
            assertThrows(
                ThemeArchiveExtractionException.class,
                () -> newExtractor().extract(
                    zipFile,
                    invalidValidation
                )
            );

        assertEquals(
            ThemeInstallErrorCode
                .PACKAGE_VALIDATION_FAILED,
            error.errorCode()
        );

        assertTrue(
            error.temporaryDirectoryCleaned()
        );

        assertEquals(
            0L,
            installationWorkspaceCount()
        );
    }

    /**
     * 创建测试解压器。
     */
    private ThemeArchiveExtractor newExtractor() {
        return new ThemeArchiveExtractor(
            temporaryDirectory.resolve(
                "storage-temp"
            )
        );
    }

    /**
     * 统计仍然存在的独立安装工作目录。
     *
     * @throws Exception 目录读取失败
     */
    private long installationWorkspaceCount()
        throws Exception {

        Path installTempRoot =
            temporaryDirectory
                .resolve("storage-temp")
                .resolve("theme-install");

        if (!Files.isDirectory(installTempRoot)) {
            return 0L;
        }

        try (
            var stream =
                Files.list(installTempRoot)
        ) {
            return stream.count();
        }
    }

    /**
     * 创建手工校验结果。
     */
    private ThemePackageValidationResult
        manualValidation(
            Path zipFile,
            ThemeManifest manifest,
            String archiveRoot,
            List<TestZipEntry> entries,
            int expectedEntryCount
        ) throws Exception {

        long totalBytes =
            entries
                .stream()
                .mapToLong(
                    entry ->
                        entry.content()
                            .getBytes(
                                StandardCharsets.UTF_8
                            )
                            .length
                )
                .sum();

        return new ThemePackageValidationResult(
            manifest,
            archiveRoot,
            Files.size(zipFile),
            expectedEntryCount,
            totalBytes,
            calculateSha256(zipFile),
            List.of()
        );
    }

    /**
     * 创建合法测试清单对象。
     */
    private ThemeManifest createManifest(
        String themeId
    ) {
        return new ThemeManifest(
            themeId,
            "临时解压测试主题",
            "1.0.0",
            "thymeleaf",
            new ThemeAuthor(
                "Aquafish Test",
                ""
            ),
            null,
            "主题临时解压测试。",
            1,
            ThemeRequirements.empty()
        );
    }

    /**
     * 创建合法 theme.yaml。
     */
    private String validManifest(
        String themeId
    ) {
        return """
            id: %s
            title: 临时解压测试主题
            version: 1.0.0
            engine: thymeleaf
            author:
              name: Aquafish Test
            apiVersion: 1
            description: 主题临时解压测试。
            """.formatted(themeId);
    }

    /**
     * 创建普通 ZIP 条目。
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

                byte[] content =
                    definition.content()
                        .getBytes(
                            StandardCharsets.UTF_8
                        );

                output.write(content);
                output.closeArchiveEntry();
            }

            output.finish();
        }
    }

    /**
     * 独立计算 ZIP SHA-256。
     */
    private String calculateSha256(
        Path file
    ) throws Exception {

        MessageDigest digest =
            MessageDigest.getInstance(
                "SHA-256"
            );

        try (
            InputStream input =
                Files.newInputStream(file)
        ) {
            byte[] buffer =
                new byte[8192];

            int read;

            while (
                (read = input.read(buffer))
                    != -1
            ) {
                digest.update(
                    buffer,
                    0,
                    read
                );
            }
        }

        return HexFormat
            .of()
            .formatHex(
                digest.digest()
            );
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
