package com.aquafish.theme.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 主题 ZIP 清单读取与根目录识别测试。
 */
class ThemePackageValidatorManifestTest {

    /**
     * 每个测试使用独立临时目录。
     */
    @TempDir
    Path temporaryDirectory;

    /**
     * 验证单层主题文件夹中的 theme.yaml。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldReadManifestFromSingleThemeRoot()
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
                    validManifest(
                        "sample-theme"
                    )
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

        assertNotNull(
            result.manifest()
        );

        assertEquals(
            "sample-theme",
            result.manifest().id()
        );

        assertEquals(
            "sample-theme",
            result.archiveRoot()
        );
    }

    /**
     * 验证 theme.yaml 可以直接位于 ZIP 根目录。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldReadManifestFromArchiveRoot()
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
                ),
                entry(
                    "assets/style.css",
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
            "flat-theme",
            result.manifest().id()
        );

        assertEquals(
            "",
            result.archiveRoot()
        );
    }

    /**
     * 验证缺少 theme.yaml 时拒绝主题包。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectMissingManifest()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "missing-manifest.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "sample-theme/templates/index.html",
                    "<html></html>"
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
                .MANIFEST_MISSING
        );
    }

    /**
     * 验证多份 theme.yaml 会被拒绝。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectDuplicateManifests()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "duplicate-manifest.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "theme-one/theme.yaml",
                    validManifest(
                        "theme-one"
                    )
                ),
                entry(
                    "theme-two/theme.yaml",
                    validManifest(
                        "theme-two"
                    )
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
                .MANIFEST_DUPLICATE
        );
    }

    /**
     * 验证不允许双层包装目录。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectNestedWrapperDirectory()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "nested-root.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "outer/sample-theme/theme.yaml",
                    validManifest(
                        "sample-theme"
                    )
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
                .ROOT_STRUCTURE_INVALID
        );
    }

    /**
     * 验证单层主题文件夹之外不能存在杂散文件。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectFileOutsideThemeRoot()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "outside-root.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "sample-theme/theme.yaml",
                    validManifest(
                        "sample-theme"
                    )
                ),
                entry(
                    "sample-theme/templates/index.html",
                    "<html></html>"
                ),
                entry(
                    "outside.txt",
                    "outside"
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
                .ROOT_STRUCTURE_INVALID
        );
    }

    /**
     * 验证非法 YAML 或非法字段会被拒绝。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectInvalidManifest()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "invalid-manifest.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "invalid-theme/theme.yaml",
                    """
                    title: 没有主题标识
                    engine: freemarker
                    """
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
                .MANIFEST_INVALID
        );
    }

    /**
     * 验证 Theme.yaml 等错误大小写会被明确拒绝。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectIncorrectManifestFileCase()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "incorrect-case.zip"
            );

        createZip(
            zipFile,
            List.of(
                entry(
                    "sample-theme/Theme.yaml",
                    validManifest(
                        "sample-theme"
                    )
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
                .MANIFEST_INVALID
        );
    }

    /**
     * 验证 theme.yaml 实际内容大小受到硬限制。
     *
     * @throws Exception ZIP 创建失败
     */
    @Test
    void shouldRejectOversizedManifest()
        throws Exception {

        Path zipFile =
            temporaryDirectory.resolve(
                "oversized-manifest.zip"
            );

        String oversizedManifest =
            validManifest(
                "large-theme"
            )
                + "description: "
                + "x".repeat(512)
                + "\n";

        createZip(
            zipFile,
            List.of(
                entry(
                    "large-theme/theme.yaml",
                    oversizedManifest
                )
            )
        );

        ThemePackageValidationPolicy policy =
            new ThemePackageValidationPolicy(
                1024L * 1024L,
                100,
                1024L,
                4096L,
                100.0D,
                240,
                32,
                128L
            );

        ThemePackageValidationResult result =
            new ThemePackageValidator(
                policy
            ).validate(zipFile);

        assertFalse(result.valid());

        assertHasIssue(
            result,
            ThemePackageIssueCode
                .MANIFEST_SIZE_EXCEEDED
        );
    }

    /**
     * 创建合法主题清单。
     */
    private String validManifest(
        String themeId
    ) {
        return """
            id: %s
            title: 测试主题
            version: 1.0.0
            engine: thymeleaf
            author:
              name: Aquafish Test
            apiVersion: 1
            description: 主题包清单读取测试。
            """.formatted(themeId);
    }

    /**
     * 断言结果包含指定问题代码。
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
     * 测试 ZIP 条目。
     */
    private record TestZipEntry(
        String name,
        String content
    ) {
    }
}
