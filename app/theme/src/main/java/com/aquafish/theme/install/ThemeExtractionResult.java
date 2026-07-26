package com.aquafish.theme.install;

import com.aquafish.theme.manifest.ThemeManifest;
import com.aquafish.theme.validation.ThemePackageValidationResult;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 主题包临时解压成功结果。
 *
 * @param manifest 解压后再次解析的主题清单
 * @param workspaceDirectory 独立安装工作目录
 * @param extractedThemeDirectory 已去掉 ZIP 包装层的主题根目录
 * @param extractedEntryCount 实际处理的 ZIP 条目数量
 * @param extractedFileCount 实际写入的普通文件数量
 * @param extractedBytes 实际写入的总字节数
 * @param sha256 原始 ZIP SHA-256
 * @param wrappedArchiveRoot ZIP 是否使用单层主题目录包装
 * @param validationResult 原始安全校验结果
 */
public record ThemeExtractionResult(
    ThemeManifest manifest,
    String workspaceDirectory,
    String extractedThemeDirectory,
    int extractedEntryCount,
    int extractedFileCount,
    long extractedBytes,
    String sha256,
    boolean wrappedArchiveRoot,
    ThemePackageValidationResult
        validationResult
) {

    /**
     * SHA-256 格式。
     */
    private static final Pattern
        SHA256_PATTERN =
            Pattern.compile(
                "^[0-9a-f]{64}$"
            );

    /**
     * 标准化并验证结果。
     */
    public ThemeExtractionResult {
        if (manifest == null) {
            throw new IllegalArgumentException(
                "主题解压结果必须包含主题清单。"
            );
        }

        workspaceDirectory =
            requirePathText(
                workspaceDirectory,
                "主题安装工作目录"
            );

        extractedThemeDirectory =
            requirePathText(
                extractedThemeDirectory,
                "临时主题根目录"
            );

        if (extractedEntryCount < 0) {
            throw new IllegalArgumentException(
                "解压条目数量不能小于 0。"
            );
        }

        if (extractedFileCount < 0) {
            throw new IllegalArgumentException(
                "解压文件数量不能小于 0。"
            );
        }

        if (extractedBytes < 0L) {
            throw new IllegalArgumentException(
                "解压字节数不能小于 0。"
            );
        }

        sha256 = normalizeHash(sha256);

        if (
            !SHA256_PATTERN
                .matcher(sha256)
                .matches()
        ) {
            throw new IllegalArgumentException(
                "主题解压结果必须包含有效 SHA-256。"
            );
        }

        if (
            validationResult == null
                || !validationResult.valid()
                || validationResult.manifest()
                    == null
        ) {
            throw new IllegalArgumentException(
                "主题解压结果必须包含已通过的校验结果。"
            );
        }

        if (
            !manifest.equals(
                validationResult.manifest()
            )
        ) {
            throw new IllegalArgumentException(
                "解压后主题清单与原始校验清单不一致。"
            );
        }

        if (
            !sha256.equals(
                normalizeHash(
                    validationResult.sha256()
                )
            )
        ) {
            throw new IllegalArgumentException(
                "解压结果 SHA-256 与校验结果不一致。"
            );
        }

        Path workspacePath =
            Path.of(workspaceDirectory)
                .toAbsolutePath()
                .normalize();

        Path themePath =
            Path.of(extractedThemeDirectory)
                .toAbsolutePath()
                .normalize();

        if (!themePath.startsWith(workspacePath)) {
            throw new IllegalArgumentException(
                "临时主题目录必须位于安装工作目录内。"
            );
        }

        workspaceDirectory =
            workspacePath.toString();

        extractedThemeDirectory =
            themePath.toString();
    }

    /**
     * 获取工作目录 Path。
     *
     * @return 工作目录
     */
    public Path workspacePath() {
        return Path.of(
            workspaceDirectory
        );
    }

    /**
     * 获取临时主题根目录 Path。
     *
     * @return 临时主题根目录
     */
    public Path extractedThemePath() {
        return Path.of(
            extractedThemeDirectory
        );
    }

    /**
     * 获取主题 ID。
     *
     * @return 主题 ID
     */
    public String themeId() {
        return manifest.id();
    }

    /**
     * 标准化路径文字。
     */
    private static String requirePathText(
        String value,
        String fieldName
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                fieldName + "不能为空。"
            );
        }

        return value.trim();
    }

    /**
     * 标准化 SHA-256。
     */
    private static String normalizeHash(
        String value
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            return "";
        }

        return value
            .trim()
            .toLowerCase(Locale.ROOT);
    }
}
