package com.aquafish.theme.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.theme.manifest.ThemeAuthor;
import com.aquafish.theme.manifest.ThemeManifest;
import com.aquafish.theme.manifest.ThemeRequirements;
import com.aquafish.theme.validation.ThemePackageIssue;
import com.aquafish.theme.validation.ThemePackageIssueCode;
import com.aquafish.theme.validation.ThemePackageValidationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 主题安装结果、阶段和策略模型测试。
 */
class ThemeInstallModelTest {

    /**
     * 固定测试 SHA-256。
     */
    private static final String SHA256 =
        "0123456789abcdef"
            + "0123456789abcdef"
            + "0123456789abcdef"
            + "0123456789abcdef";

    /**
     * 验证默认策略不会因为普通警告拒绝安装，
     * 也不会强制要求文件系统支持原子移动。
     */
    @Test
    void shouldExposeDefaultPolicy() {
        ThemeInstallPolicy policy =
            ThemeInstallPolicy.defaults();

        assertFalse(
            policy.rejectPackageWarnings()
        );

        assertFalse(
            policy.requireAtomicMove()
        );
    }

    /**
     * 验证严格策略。
     */
    @Test
    void shouldExposeStrictPolicy() {
        ThemeInstallPolicy policy =
            ThemeInstallPolicy.strict();

        assertTrue(
            policy.rejectPackageWarnings()
        );

        assertTrue(
            policy.requireAtomicMove()
        );
    }

    /**
     * 验证成功安装结果。
     */
    @Test
    void shouldCreateInstalledResult() {
        ThemeManifest manifest =
            createManifest();

        ThemePackageValidationResult
            validationResult =
                validValidationResult(
                    manifest
                );

        ThemeInstallResult result =
            ThemeInstallResult.installed(
                manifest,
                "H:/workdir/themes/sample-theme",
                SHA256,
                true,
                validationResult
            );

        assertTrue(result.success());
        assertFalse(result.rejected());
        assertFalse(result.failed());

        assertEquals(
            ThemeInstallStatus.INSTALLED,
            result.status()
        );

        assertEquals(
            ThemeInstallStage.COMPLETE,
            result.stage()
        );

        assertNull(result.errorCode());

        assertEquals(
            "sample-theme",
            result.themeId()
        );

        assertEquals(
            SHA256,
            result.sha256()
        );

        assertTrue(result.atomicMoveUsed());

        assertTrue(
            result.temporaryDirectoryCleaned()
        );

        assertTrue(result.packageValidated());
    }

    /**
     * 验证带警告但没有错误的校验结果
     * 仍然可以作为成功安装依据。
     */
    @Test
    void shouldKeepValidationWarnings() {
        ThemeManifest manifest =
            createManifest();

        ThemePackageValidationResult
            validationResult =
                new ThemePackageValidationResult(
                    manifest,
                    "sample-theme",
                    1024L,
                    3,
                    4096L,
                    SHA256,
                    List.of(
                        ThemePackageIssue.warning(
                            ThemePackageIssueCode
                                .UNRECOMMENDED_FILE,
                            "sample-theme/.DS_Store",
                            "主题包包含系统文件。"
                        )
                    )
                );

        ThemeInstallResult result =
            ThemeInstallResult.installed(
                manifest,
                "H:/workdir/themes/sample-theme",
                SHA256,
                false,
                validationResult
            );

        assertTrue(result.success());
        assertTrue(
            result.hasPackageWarnings()
        );

        assertEquals(
            1,
            result.validationResult()
                .warnings()
                .size()
        );
    }

    /**
     * 验证主题包校验失败时可以创建拒绝结果。
     */
    @Test
    void shouldCreateRejectedResult() {
        ThemePackageValidationResult
            validationResult =
                invalidValidationResult();

        ThemeInstallResult result =
            ThemeInstallResult.rejected(
                ThemeInstallStage.VALIDATION,
                ThemeInstallErrorCode
                    .PACKAGE_VALIDATION_FAILED,
                "主题包没有通过安全校验。",
                null,
                validationResult
            );

        assertFalse(result.success());
        assertTrue(result.rejected());
        assertFalse(result.failed());

        assertEquals(
            ThemeInstallStatus.REJECTED,
            result.status()
        );

        assertEquals(
            ThemeInstallErrorCode
                .PACKAGE_VALIDATION_FAILED,
            result.errorCode()
        );

        assertFalse(result.packageValidated());

        assertEquals(
            "",
            result.themeId()
        );
    }

    /**
     * 验证文件系统错误可以创建失败结果。
     */
    @Test
    void shouldCreateFailedResult() {
        ThemeManifest manifest =
            createManifest();

        ThemePackageValidationResult
            validationResult =
                validValidationResult(
                    manifest
                );

        ThemeInstallResult result =
            ThemeInstallResult.failed(
                ThemeInstallStage.EXTRACTION,
                ThemeInstallErrorCode
                    .ARCHIVE_EXTRACTION_FAILED,
                "受限解压主题包失败。",
                manifest,
                validationResult,
                true
            );

        assertFalse(result.success());
        assertFalse(result.rejected());
        assertTrue(result.failed());

        assertEquals(
            ThemeInstallStage.EXTRACTION,
            result.stage()
        );

        assertEquals(
            "sample-theme",
            result.themeId()
        );

        assertTrue(
            result.temporaryDirectoryCleaned()
        );
    }

    /**
     * 验证成功结果不能携带错误代码。
     */
    @Test
    void shouldRejectInstalledResultWithErrorCode() {
        ThemeManifest manifest =
            createManifest();

        ThemePackageValidationResult
            validationResult =
                validValidationResult(
                    manifest
                );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ThemeInstallResult(
                ThemeInstallStatus.INSTALLED,
                ThemeInstallStage.COMPLETE,
                ThemeInstallErrorCode
                    .INSTALL_MOVE_FAILED,
                "错误的成功结果。",
                manifest,
                "H:/workdir/themes/sample-theme",
                SHA256,
                true,
                true,
                validationResult
            )
        );
    }

    /**
     * 验证成功结果必须使用有效 SHA-256。
     */
    @Test
    void shouldRejectInstalledResultWithoutValidHash() {
        ThemeManifest manifest =
            createManifest();

        ThemePackageValidationResult
            validationResult =
                validValidationResult(
                    manifest
                );

        assertThrows(
            IllegalArgumentException.class,
            () -> ThemeInstallResult.installed(
                manifest,
                "H:/workdir/themes/sample-theme",
                "invalid-hash",
                false,
                validationResult
            )
        );
    }

    /**
     * 验证成功结果必须来自已通过的校验结果。
     */
    @Test
    void shouldRejectInstalledResultWithInvalidValidation() {
        ThemeManifest manifest =
            createManifest();

        assertThrows(
            IllegalArgumentException.class,
            () -> ThemeInstallResult.installed(
                manifest,
                "H:/workdir/themes/sample-theme",
                SHA256,
                false,
                invalidValidationResult()
            )
        );
    }

    /**
     * 验证未安装结果必须包含稳定错误代码。
     */
    @Test
    void shouldRejectFailureWithoutErrorCode() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ThemeInstallResult(
                ThemeInstallStatus.FAILED,
                ThemeInstallStage.CLEANUP,
                null,
                "清理失败。",
                null,
                "",
                "",
                false,
                false,
                null
            )
        );
    }

    /**
     * 验证主题清单 ID 必须和校验结果一致。
     */
    @Test
    void shouldRejectManifestMismatch() {
        ThemeManifest manifest =
            createManifest();

        ThemeManifest anotherManifest =
            new ThemeManifest(
                "another-theme",
                "另一个主题",
                "1.0.0",
                "thymeleaf",
                ThemeAuthor.empty(),
                null,
                "",
                1,
                ThemeRequirements.empty()
            );

        ThemePackageValidationResult
            validationResult =
                validValidationResult(
                    anotherManifest
                );

        assertThrows(
            IllegalArgumentException.class,
            () -> ThemeInstallResult.installed(
                manifest,
                "H:/workdir/themes/sample-theme",
                SHA256,
                false,
                validationResult
            )
        );
    }

    /**
     * 创建合法测试主题清单。
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
            "主题安装模型测试。",
            1,
            ThemeRequirements.empty()
        );
    }

    /**
     * 创建通过安全校验的结果。
     */
    private ThemePackageValidationResult
        validValidationResult(
            ThemeManifest manifest
        ) {

        return new ThemePackageValidationResult(
            manifest,
            manifest.id(),
            1024L,
            3,
            4096L,
            SHA256,
            List.of()
        );
    }

    /**
     * 创建未通过安全校验的结果。
     */
    private ThemePackageValidationResult
        invalidValidationResult() {

        return new ThemePackageValidationResult(
            null,
            "",
            100L,
            1,
            100L,
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
    }
}
