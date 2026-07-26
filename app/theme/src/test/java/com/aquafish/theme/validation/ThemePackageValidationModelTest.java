package com.aquafish.theme.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.theme.manifest.ThemeAuthor;
import com.aquafish.theme.manifest.ThemeManifest;
import com.aquafish.theme.manifest.ThemeRequirements;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 主题压缩包校验模型自动化测试。
 */
class ThemePackageValidationModelTest {

    /**
     * 验证默认安全策略数值稳定且互相合理。
     */
    @Test
    void shouldExposeSafeDefaultPolicy() {
        ThemePackageValidationPolicy policy =
            ThemePackageValidationPolicy.defaults();

        assertEquals(
            50L * 1024L * 1024L,
            policy.maxPackageBytes()
        );

        assertEquals(
            5000,
            policy.maxEntryCount()
        );

        assertEquals(
            20L * 1024L * 1024L,
            policy.maxSingleFileBytes()
        );

        assertEquals(
            200L * 1024L * 1024L,
            policy.maxTotalUncompressedBytes()
        );

        assertEquals(
            100.0D,
            policy.maxCompressionRatio()
        );

        assertEquals(
            240,
            policy.maxPathLength()
        );

        assertEquals(
            32,
            policy.maxPathDepth()
        );

        assertEquals(
            1024L * 1024L,
            policy.maxManifestBytes()
        );
    }

    /**
     * 验证不合理的策略会在创建阶段被拒绝。
     */
    @Test
    void shouldRejectInvalidPolicy() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ThemePackageValidationPolicy(
                0L,
                5000,
                20L,
                200L,
                100.0D,
                240,
                32,
                1L
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ThemePackageValidationPolicy(
                100L,
                10,
                90L,
                80L,
                100.0D,
                240,
                32,
                1L
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ThemePackageValidationPolicy(
                100L,
                10,
                20L,
                80L,
                1.0D,
                240,
                32,
                1L
            )
        );
    }

    /**
     * 验证错误和警告辅助方法。
     */
    @Test
    void shouldCreateErrorAndWarningIssues() {
        ThemePackageIssue error =
            ThemePackageIssue.error(
                ThemePackageIssueCode
                    .ENTRY_PATH_TRAVERSAL,
                "../outside.txt",
                "主题包包含路径穿越条目。"
            );

        ThemePackageIssue warning =
            ThemePackageIssue.warning(
                ThemePackageIssueCode
                    .UNRECOMMENDED_FILE,
                ".DS_Store",
                "主题包包含无用系统文件。"
            );

        assertTrue(error.isError());
        assertFalse(error.isWarning());

        assertTrue(warning.isWarning());
        assertFalse(warning.isError());

        assertEquals(
            "../outside.txt",
            error.entryName()
        );
    }

    /**
     * 验证没有错误时校验结果通过。
     */
    @Test
    void shouldTreatWarningsAsValidResult() {
        ThemePackageValidationResult result =
            new ThemePackageValidationResult(
                createManifest(),
                "sample-theme",
                1024L,
                12,
                4096L,
                "abcdef",
                List.of(
                    ThemePackageIssue.warning(
                        ThemePackageIssueCode
                            .UNRECOMMENDED_FILE,
                        ".DS_Store",
                        "存在无用系统文件。"
                    )
                )
            );

        assertTrue(result.valid());
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());

        assertEquals(
            0,
            result.errors().size()
        );

        assertEquals(
            1,
            result.warnings().size()
        );
    }

    /**
     * 验证任意一个错误都会阻止主题包通过。
     */
    @Test
    void shouldRejectResultContainingError() {
        ThemePackageValidationResult result =
            new ThemePackageValidationResult(
                null,
                "",
                1024L,
                2,
                2048L,
                "",
                List.of(
                    ThemePackageIssue.error(
                        ThemePackageIssueCode
                            .MANIFEST_MISSING,
                        "",
                        "主题包缺少 theme.yaml。"
                    )
                )
            );

        assertFalse(result.valid());
        assertTrue(result.hasErrors());
        assertFalse(result.hasWarnings());

        assertEquals(
            ThemePackageIssueCode.MANIFEST_MISSING,
            result.errors().get(0).code()
        );
    }

    /**
     * 验证结果对象会复制问题列表，
     * 防止调用方在创建结果后继续修改内部数据。
     */
    @Test
    void shouldCopyIssueList() {
        List<ThemePackageIssue> mutableIssues =
            new ArrayList<>();

        mutableIssues.add(
            ThemePackageIssue.warning(
                ThemePackageIssueCode
                    .UNRECOMMENDED_FILE,
                "desktop.ini",
                "存在 Windows 系统文件。"
            )
        );

        ThemePackageValidationResult result =
            new ThemePackageValidationResult(
                createManifest(),
                "",
                100L,
                1,
                100L,
                "",
                mutableIssues
            );

        mutableIssues.clear();

        assertEquals(
            1,
            result.issues().size()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> result
                .issues()
                .add(
                    ThemePackageIssue.warning(
                        ThemePackageIssueCode
                            .UNRECOMMENDED_FILE,
                        "test",
                        "测试。"
                    )
                )
        );
    }

    /**
     * 创建测试主题清单。
     *
     * @return 合法主题清单
     */
    private ThemeManifest createManifest() {
        return new ThemeManifest(
            "sample-theme",
            "示例主题",
            "1.0.0",
            "thymeleaf",
            new ThemeAuthor(
                "Aquafish Test",
                ""
            ),
            null,
            "主题包校验模型测试。",
            1,
            ThemeRequirements.empty()
        );
    }
}
