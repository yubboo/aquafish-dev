package com.aquafish.theme.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * 主题 ZIP 内容安全、压缩炸弹和哈希测试。
 */
class ThemePackageValidatorContentSecurityTest {

    /**
     * 每个测试独立临时目录。
     */
    @TempDir
    Path temporaryDirectory;

    /**
     * 验证实际读取时拦截超大单文件。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectOversizedSingleFile()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "single-file-limit.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "large-theme/theme.yaml",
                    validManifest("large-theme")
                ),
                entry(
                    "large-theme/assets/large.txt",
                    "x".repeat(2048)
                )
            )
        );

        ThemePackageValidationPolicy policy =
            policy(
                1024L,
                8192L,
                100.0D
            );

        ThemePackageValidationResult result =
            new ThemePackageValidator(
                policy
            ).validate(zipFile);

        assertFalse(result.valid());

        assertHasIssue(
            result,
            ThemePackageIssueCode
                .SINGLE_FILE_SIZE_EXCEEDED
        );
    }

    /**
     * 验证多个文件累计超过总大小限制时被拒绝。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectExcessiveTotalSize()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "total-size-limit.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "total-theme/theme.yaml",
                    validManifest("total-theme")
                ),
                entry(
                    "total-theme/assets/one.txt",
                    "a".repeat(900)
                ),
                entry(
                    "total-theme/assets/two.txt",
                    "b".repeat(900)
                )
            )
        );

        ThemePackageValidationPolicy policy =
            policy(
                1024L,
                1800L,
                100.0D
            );

        ThemePackageValidationResult result =
            new ThemePackageValidator(
                policy
            ).validate(zipFile);

        assertFalse(result.valid());

        assertHasIssue(
            result,
            ThemePackageIssueCode
                .TOTAL_UNCOMPRESSED_SIZE_EXCEEDED
        );
    }

    /**
     * 验证高压缩比重复内容会被识别为疑似压缩炸弹。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectExtremeCompressionRatio()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "compression-bomb.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "bomb-theme/theme.yaml",
                    validManifest("bomb-theme")
                ),
                entry(
                    "bomb-theme/assets/repeated.txt",
                    "A".repeat(
                        2 * 1024 * 1024
                    )
                )
            )
        );

        ThemePackageValidationPolicy policy =
            policy(
                4L * 1024L * 1024L,
                8L * 1024L * 1024L,
                10.0D
            );

        ThemePackageValidationResult result =
            new ThemePackageValidator(
                policy
            ).validate(zipFile);

        assertFalse(result.valid());

        assertHasIssue(
            result,
            ThemePackageIssueCode
                .COMPRESSION_RATIO_EXCEEDED
        );
    }

    /**
     * 验证服务端脚本和可执行文件会被拒绝。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectDangerousExecutableFileTypes()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "dangerous-files.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "danger-theme/theme.yaml",
                    validManifest("danger-theme")
                ),
                entry(
                    "danger-theme/assets/backdoor.php",
                    "<?php echo 'danger'; ?>"
                ),
                entry(
                    "danger-theme/server.jar",
                    "not-a-real-jar"
                )
            )
        );

        ThemePackageValidationResult result =
            new ThemePackageValidator()
                .validate(zipFile);

        assertFalse(result.valid());

        assertHasIssue(
            result,
            ThemePackageIssueCode
                .DANGEROUS_FILE_TYPE
        );
    }

    /**
     * 验证浏览器端 JavaScript 仍是合法主题资源。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldAllowClientJavaScriptAssets()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "javascript-theme.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "javascript-theme/theme.yaml",
                    validManifest(
                        "javascript-theme"
                    )
                ),
                entry(
                    "javascript-theme/assets/js/app.js",
                    "console.log('theme');"
                )
            )
        );

        ThemePackageValidationResult result =
            new ThemePackageValidator()
                .validate(zipFile);

        assertTrue(
            result.valid(),
            () -> result.issues().toString()
        );

        assertFalse(
            result.issues()
                .stream()
                .anyMatch(
                    issue ->
                        issue.code()
                            == ThemePackageIssueCode
                                .DANGEROUS_FILE_TYPE
                )
        );
    }

    /**
     * 验证系统垃圾文件和开发目录只产生警告。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldWarnAboutSystemAndDevelopmentFiles()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "warning-theme.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "warning-theme/theme.yaml",
                    validManifest(
                        "warning-theme"
                    )
                ),
                entry(
                    "warning-theme/.DS_Store",
                    "metadata"
                ),
                entry(
                    "warning-theme/node_modules/cache.txt",
                    "cache"
                )
            )
        );

        ThemePackageValidationResult result =
            new ThemePackageValidator()
                .validate(zipFile);

        assertTrue(
            result.valid(),
            () -> result.issues().toString()
        );

        assertTrue(result.hasWarnings());

        assertHasIssue(
            result,
            ThemePackageIssueCode
                .UNRECOMMENDED_FILE
        );
    }

    /**
     * 验证返回的 SHA-256 与真实 ZIP 文件一致。
     *
     * @throws Exception ZIP 创建或哈希失败
     */
    @Test
    void shouldCalculateStableSha256()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "hash-theme.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "hash-theme/theme.yaml",
                    validManifest("hash-theme")
                ),
                entry(
                    "hash-theme/templates/index.html",
                    "<html>hash</html>"
                )
            )
        );

        ThemePackageValidationResult result =
            new ThemePackageValidator()
                .validate(zipFile);

        assertTrue(
            result.valid(),
            () -> result.issues().toString()
        );

        assertEquals(
            calculateSha256(zipFile),
            result.sha256()
        );

        assertTrue(
            result.sha256().matches(
                "^[0-9a-f]{64}$"
            )
        );
    }

    /**
     * 创建自定义安全策略。
     */
    private ThemePackageValidationPolicy policy(
        long maxSingleFileBytes,
        long maxTotalBytes,
        double maxCompressionRatio
    ) {
        return new ThemePackageValidationPolicy(
            10L * 1024L * 1024L,
            100,
            maxSingleFileBytes,
            maxTotalBytes,
            maxCompressionRatio,
            240,
            32,
            512L
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
            title: 内容安全测试主题
            version: 1.0.0
            engine: thymeleaf
            author:
              name: Aquafish Test
            apiVersion: 1
            description: 主题包内容安全测试。
            """.formatted(themeId);
    }

    /**
     * 断言包含指定问题代码。
     */
    private void assertHasIssue(
        ThemePackageValidationResult result,
        ThemePackageIssueCode expectedCode
    ) {
        assertTrue(
            result.issues()
                .stream()
                .anyMatch(
                    issue ->
                        issue.code()
                            == expectedCode
                ),
            () -> "没有找到问题代码："
                + expectedCode
                + "，实际问题："
                + result.issues()
        );
    }

    /**
     * 创建测试条目。
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
     * 独立计算测试文件 SHA-256。
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
