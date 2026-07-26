package com.aquafish.theme.install;

import com.aquafish.theme.manifest.ThemeManifest;
import com.aquafish.theme.validation.ThemePackageValidationResult;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 主题安装最终结果。
 *
 * @param status 安装最终状态
 * @param stage 安装结束或失败所在阶段
 * @param errorCode 失败或拒绝代码；成功时为空
 * @param message 面向管理员的结果说明
 * @param manifest 主题清单，可为空
 * @param installedDirectory 正式安装目录；成功时必填
 * @param sha256 原始主题 ZIP 的 SHA-256
 * @param atomicMoveUsed 是否使用了原子移动
 * @param temporaryDirectoryCleaned 临时目录是否已经清理
 * @param validationResult 原始主题包校验结果，可为空
 */
public record ThemeInstallResult(
    ThemeInstallStatus status,
    ThemeInstallStage stage,
    ThemeInstallErrorCode errorCode,
    String message,
    ThemeManifest manifest,
    String installedDirectory,
    String sha256,
    boolean atomicMoveUsed,
    boolean temporaryDirectoryCleaned,
    ThemePackageValidationResult validationResult
) {

    /**
     * SHA-256 小写十六进制格式。
     */
    private static final Pattern
        SHA256_PATTERN =
            Pattern.compile(
                "^[0-9a-f]{64}$"
            );

    /**
     * 标准化并验证安装结果。
     */
    public ThemeInstallResult {
        if (status == null) {
            throw new IllegalArgumentException(
                "主题安装状态不能为空。"
            );
        }

        if (stage == null) {
            throw new IllegalArgumentException(
                "主题安装阶段不能为空。"
            );
        }

        if (
            message == null
                || message.isBlank()
        ) {
            throw new IllegalArgumentException(
                "主题安装结果说明不能为空。"
            );
        }

        message = message.trim();

        installedDirectory =
            normalizeText(
                installedDirectory
            );

        sha256 = normalizeHash(
            sha256
        );

        if (
            status
                == ThemeInstallStatus.INSTALLED
        ) {
            validateInstalledResult(
                stage,
                errorCode,
                manifest,
                installedDirectory,
                sha256,
                temporaryDirectoryCleaned,
                validationResult
            );
        } else {
            validateNonInstalledResult(
                stage,
                errorCode
            );
        }

        validateManifestConsistency(
            manifest,
            sha256,
            validationResult
        );
    }

    /**
     * 创建成功安装结果。
     *
     * @param manifest 已安装主题清单
     * @param installedDirectory 正式主题目录
     * @param sha256 原始 ZIP SHA-256
     * @param atomicMoveUsed 是否使用原子移动
     * @param validationResult 原始校验结果
     * @return 成功结果
     */
    public static ThemeInstallResult installed(
        ThemeManifest manifest,
        String installedDirectory,
        String sha256,
        boolean atomicMoveUsed,
        ThemePackageValidationResult
            validationResult
    ) {
        return new ThemeInstallResult(
            ThemeInstallStatus.INSTALLED,
            ThemeInstallStage.COMPLETE,
            null,
            "主题安装成功。",
            manifest,
            installedDirectory,
            sha256,
            atomicMoveUsed,
            true,
            validationResult
        );
    }

    /**
     * 创建按规则拒绝的结果。
     *
     * @param stage 拒绝发生阶段
     * @param errorCode 拒绝代码
     * @param message 拒绝说明
     * @param manifest 已解析主题清单，可为空
     * @param validationResult 校验结果，可为空
     * @return 拒绝结果
     */
    public static ThemeInstallResult rejected(
        ThemeInstallStage stage,
        ThemeInstallErrorCode errorCode,
        String message,
        ThemeManifest manifest,
        ThemePackageValidationResult
            validationResult
    ) {
        return new ThemeInstallResult(
            ThemeInstallStatus.REJECTED,
            stage,
            errorCode,
            message,
            manifest,
            "",
            validationHash(
                validationResult
            ),
            false,
            true,
            validationResult
        );
    }

    /**
     * 创建运行时失败结果。
     *
     * @param stage 失败阶段
     * @param errorCode 错误代码
     * @param message 错误说明
     * @param manifest 已解析主题清单，可为空
     * @param validationResult 校验结果，可为空
     * @param temporaryDirectoryCleaned 临时目录是否清理
     * @return 失败结果
     */
    public static ThemeInstallResult failed(
        ThemeInstallStage stage,
        ThemeInstallErrorCode errorCode,
        String message,
        ThemeManifest manifest,
        ThemePackageValidationResult
            validationResult,
        boolean temporaryDirectoryCleaned
    ) {
        return new ThemeInstallResult(
            ThemeInstallStatus.FAILED,
            stage,
            errorCode,
            message,
            manifest,
            "",
            validationHash(
                validationResult
            ),
            false,
            temporaryDirectoryCleaned,
            validationResult
        );
    }

    /**
     * 判断安装是否成功。
     *
     * @return 成功时返回 true
     */
    public boolean success() {
        return status
            == ThemeInstallStatus.INSTALLED;
    }

    /**
     * 判断安装是否被正常拒绝。
     *
     * @return REJECTED 时返回 true
     */
    public boolean rejected() {
        return status
            == ThemeInstallStatus.REJECTED;
    }

    /**
     * 判断安装是否因运行时故障失败。
     *
     * @return FAILED 时返回 true
     */
    public boolean failed() {
        return status
            == ThemeInstallStatus.FAILED;
    }

    /**
     * 获取当前结果对应的主题 ID。
     *
     * <p>
     * 优先使用 result.manifest；
     * 如果为空，则尝试从校验结果中获取。
     * </p>
     *
     * @return 主题 ID，未知时返回空字符串
     */
    public String themeId() {
        if (manifest != null) {
            return manifest.id();
        }

        if (
            validationResult != null
                && validationResult.manifest()
                    != null
        ) {
            return validationResult
                .manifest()
                .id();
        }

        return "";
    }

    /**
     * 判断原始主题包是否通过安全校验。
     *
     * @return 校验存在且通过时返回 true
     */
    public boolean packageValidated() {
        return validationResult != null
            && validationResult.valid();
    }

    /**
     * 判断原始主题包是否包含警告。
     *
     * @return 存在警告时返回 true
     */
    public boolean hasPackageWarnings() {
        return validationResult != null
            && validationResult.hasWarnings();
    }

    /**
     * 验证成功安装结果。
     */
    private static void validateInstalledResult(
        ThemeInstallStage stage,
        ThemeInstallErrorCode errorCode,
        ThemeManifest manifest,
        String installedDirectory,
        String sha256,
        boolean temporaryDirectoryCleaned,
        ThemePackageValidationResult
            validationResult
    ) {
        if (
            stage
                != ThemeInstallStage.COMPLETE
        ) {
            throw new IllegalArgumentException(
                "成功安装结果必须位于 COMPLETE 阶段。"
            );
        }

        if (errorCode != null) {
            throw new IllegalArgumentException(
                "成功安装结果不能包含错误代码。"
            );
        }

        if (manifest == null) {
            throw new IllegalArgumentException(
                "成功安装结果必须包含主题清单。"
            );
        }

        if (installedDirectory.isBlank()) {
            throw new IllegalArgumentException(
                "成功安装结果必须包含正式安装目录。"
            );
        }

        if (
            !SHA256_PATTERN
                .matcher(sha256)
                .matches()
        ) {
            throw new IllegalArgumentException(
                "成功安装结果必须包含有效的 SHA-256。"
            );
        }

        if (!temporaryDirectoryCleaned) {
            throw new IllegalArgumentException(
                "成功安装后临时目录必须已经清理。"
            );
        }

        if (
            validationResult == null
                || !validationResult.valid()
        ) {
            throw new IllegalArgumentException(
                "成功安装结果必须包含已通过的主题包校验结果。"
            );
        }
    }

    /**
     * 验证拒绝或失败结果。
     */
    private static void
        validateNonInstalledResult(
            ThemeInstallStage stage,
            ThemeInstallErrorCode errorCode
        ) {

        if (errorCode == null) {
            throw new IllegalArgumentException(
                "未安装结果必须包含错误代码。"
            );
        }

        if (
            stage
                == ThemeInstallStage.COMPLETE
        ) {
            throw new IllegalArgumentException(
                "拒绝或失败结果不能位于 COMPLETE 阶段。"
            );
        }
    }

    /**
     * 验证结果清单、哈希和校验结果一致。
     */
    private static void
        validateManifestConsistency(
            ThemeManifest manifest,
            String sha256,
            ThemePackageValidationResult
                validationResult
        ) {

        if (validationResult == null) {
            return;
        }

        ThemeManifest validationManifest =
            validationResult.manifest();

        if (
            manifest != null
                && validationManifest != null
                && !manifest.id().equals(
                    validationManifest.id()
                )
        ) {
            throw new IllegalArgumentException(
                "安装结果主题 ID 与校验结果不一致。"
            );
        }

        String validationSha256 =
            normalizeHash(
                validationResult.sha256()
            );

        if (
            !sha256.isBlank()
                && !validationSha256.isBlank()
                && !sha256.equals(
                    validationSha256
                )
        ) {
            throw new IllegalArgumentException(
                "安装结果 SHA-256 与校验结果不一致。"
            );
        }
    }

    /**
     * 从校验结果读取 SHA-256。
     */
    private static String validationHash(
        ThemePackageValidationResult
            validationResult
    ) {
        if (validationResult == null) {
            return "";
        }

        return validationResult.sha256();
    }

    /**
     * 标准化普通文本。
     */
    private static String normalizeText(
        String value
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            return "";
        }

        return value.trim();
    }

    /**
     * 标准化 SHA-256。
     */
    private static String normalizeHash(
        String value
    ) {
        return normalizeText(value)
            .toLowerCase(Locale.ROOT);
    }
}
