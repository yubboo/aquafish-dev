package com.aquafish.theme.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 主题 ZIP 路径与条目安全自动化测试。
 */
class ThemePackageValidatorPathSecurityTest {

    /**
     * 每个测试独立临时目录。
     */
    @TempDir
    Path temporaryDirectory;

    /**
     * 验证普通主题 ZIP 能够通过当前结构安全扫描。
     *
     * <p>
     * 第 42-2 步暂时不检查 theme.yaml 内容，
     * 这里只确认所有 ZIP 条目本身安全。
     * </p>
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldAcceptStructurallySafeArchive()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "sample-theme.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "sample-theme/theme.yaml",
                    """
                    id: sample-theme
                    title: 示例主题
                    engine: thymeleaf
                    """
                ),
                entry(
                    "sample-theme/templates/index.html",
                    "<html>sample</html>"
                ),
                entry(
                    "sample-theme/assets/css/style.css",
                    "body {}"
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
            3,
            result.entryCount()
        );

        assertTrue(
            result.totalUncompressedSize()
                > 0L
        );
    }

    /**
     * 验证父目录路径穿越会被拒绝。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectPathTraversal()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "path-traversal.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "sample-theme/theme.yaml",
                    "id: sample-theme"
                ),
                entry(
                    "sample-theme/../outside.txt",
                    "danger"
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
                .ENTRY_PATH_TRAVERSAL
        );
    }

    /**
     * 验证绝对路径、盘符和冒号路径会被拒绝。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectAbsoluteAndDrivePaths()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "absolute-path.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "/absolute.txt",
                    "absolute"
                ),
                entry(
                    "C:/windows.txt",
                    "drive"
                ),
                entry(
                    "sample-theme/file.txt:stream",
                    "alternate-data-stream"
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
                .ENTRY_ABSOLUTE_PATH
        );

        assertHasIssue(
            result,
            ThemePackageIssueCode
                .ENTRY_DRIVE_PREFIX
        );
    }

    /**
     * 验证重复条目会被拒绝。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectDuplicateEntries()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "duplicate.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "sample-theme/file.txt",
                    "first"
                ),
                entry(
                    "sample-theme/file.txt",
                    "second"
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
                .ENTRY_DUPLICATE
        );
    }

    /**
     * 验证 Unix 符号链接会被拒绝。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectUnixSymbolicLink()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "symbolic-link.zip"
            );

        createZip(
            zipFile,
            List.of(
                unixEntry(
                    "sample-theme/theme.yaml",
                    "id: sample-theme",
                    0100644
                ),
                unixEntry(
                    "sample-theme/templates/link.html",
                    "../outside.html",
                    0120777
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
                .ENTRY_SYMBOLIC_LINK
        );
    }

    /**
     * 验证 FIFO 等 Unix 特殊文件会被拒绝。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectUnixSpecialFile()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "special-file.zip"
            );

        createZip(
            zipFile,
            List.of(
                unixEntry(
                    "sample-theme/theme.yaml",
                    "id: sample-theme",
                    0100644
                ),
                unixEntry(
                    "sample-theme/special.pipe",
                    "",
                    0010644
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
                .ENTRY_SPECIAL_FILE
        );
    }

    /**
     * 验证条目数量超过策略限制时会停止扫描。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectTooManyEntries()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "too-many-entries.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "sample-theme/file-1.txt",
                    "1"
                ),
                entry(
                    "sample-theme/file-2.txt",
                    "2"
                ),
                entry(
                    "sample-theme/file-3.txt",
                    "3"
                )
            )
        );

        ThemePackageValidationPolicy policy =
            new ThemePackageValidationPolicy(
                1024L * 1024L,
                2,
                1024L,
                4096L,
                100.0D,
                240,
                32,
                512L
            );

        ThemePackageValidationResult result =
            new ThemePackageValidator(
                policy
            ).validate(zipFile);

        assertFalse(result.valid());

        assertHasIssue(
            result,
            ThemePackageIssueCode
                .ENTRY_COUNT_EXCEEDED
        );

        assertEquals(
            3,
            result.entryCount()
        );
    }

    /**
     * 验证空 ZIP 会被拒绝。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectEmptyArchive()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "empty.zip"
            );

        createZip(
            zipFile,
            List.of()
        );

        ThemePackageValidationResult result =
            new ThemePackageValidator()
                .validate(zipFile);

        assertFalse(result.valid());

        assertHasIssue(
            result,
            ThemePackageIssueCode
                .ARCHIVE_EMPTY
        );
    }

    /**
     * 验证非 ZIP 扩展名会在打开压缩包前被拒绝。
     *
     * @throws Exception 文件创建失败
     */
    @Test
    void shouldRejectNonZipExtension()
        throws Exception {

        Path file =
            temporaryDirectory.resolve(
                "theme.txt"
            );

        Files.writeString(
            file,
            "not a zip",
            StandardCharsets.UTF_8
        );

        ThemePackageValidationResult result =
            new ThemePackageValidator()
                .validate(file);

        assertFalse(result.valid());

        assertHasIssue(
            result,
            ThemePackageIssueCode
                .PACKAGE_EXTENSION_INVALID
        );
    }

    /**
     * 断言结果中包含指定问题代码。
     *
     * @param result 校验结果
     * @param expectedCode 预期代码
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
     * 创建普通 ZIP 条目定义。
     *
     * @param name 条目名
     * @param content 内容
     * @return 测试条目
     */
    private TestZipEntry entry(
        String name,
        String content
    ) {
        return new TestZipEntry(
            name,
            content,
            0
        );
    }

    /**
     * 创建带 Unix 模式的 ZIP 条目定义。
     *
     * @param name 条目名
     * @param content 内容
     * @param unixMode Unix 模式
     * @return 测试条目
     */
    private TestZipEntry unixEntry(
        String name,
        String content,
        int unixMode
    ) {
        return new TestZipEntry(
            name,
            content,
            unixMode
        );
    }

    /**
     * 创建真实 ZIP 文件。
     *
     * @param zipFile ZIP 路径
     * @param entries 条目定义
     * @throws Exception ZIP 创建失败
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
            output.setUseZip64(
                org.apache.commons.compress
                    .archivers.zip
                    .Zip64Mode.AsNeeded
            );

            for (TestZipEntry definition : entries) {
                ZipArchiveEntry entry =
                    new ZipArchiveEntry(
                        definition.name()
                    );

                if (definition.unixMode() != 0) {
                    entry.setUnixMode(
                        definition.unixMode()
                    );
                }

                output.putArchiveEntry(entry);

                byte[] content =
                    definition.content()
                        .getBytes(
                            StandardCharsets.UTF_8
                        );

                if (content.length > 0) {
                    output.write(content);
                }

                output.closeArchiveEntry();
            }

            output.finish();
        }
    }

    /**
     * 测试 ZIP 条目定义。
     *
     * @param name 条目名称
     * @param content UTF-8 内容
     * @param unixMode Unix 文件模式
     */
    private record TestZipEntry(
        String name,
        String content,
        int unixMode
    ) {
    }
}
