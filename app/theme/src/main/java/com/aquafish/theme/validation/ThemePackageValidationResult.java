package com.aquafish.theme.validation;

import com.aquafish.theme.manifest.ThemeManifest;
import java.util.List;

/**
 * 主题压缩包完整校验结果。
 *
 * @param manifest 成功解析的主题清单，可为空
 * @param archiveRoot ZIP 内识别出的主题根目录
 * @param packageSize ZIP 文件本身大小
 * @param entryCount ZIP 条目数量
 * @param totalUncompressedSize 估算或读取到的总解压大小
 * @param sha256 ZIP 文件 SHA-256，可为空
 * @param issues 错误和警告列表
 */
public record ThemePackageValidationResult(
    ThemeManifest manifest,
    String archiveRoot,
    long packageSize,
    int entryCount,
    long totalUncompressedSize,
    String sha256,
    List<ThemePackageIssue> issues
) {

    /**
     * 标准化校验结果。
     */
    public ThemePackageValidationResult {
        archiveRoot = normalizeText(
            archiveRoot
        );

        sha256 = normalizeText(
            sha256
        );

        if (packageSize < 0L) {
            throw new IllegalArgumentException(
                "主题包大小不能小于 0。"
            );
        }

        if (entryCount < 0) {
            throw new IllegalArgumentException(
                "ZIP 条目数量不能小于 0。"
            );
        }

        if (totalUncompressedSize < 0L) {
            throw new IllegalArgumentException(
                "总解压大小不能小于 0。"
            );
        }

        issues = issues == null
            ? List.of()
            : List.copyOf(issues);
    }

    /**
     * 判断主题包是否通过校验。
     *
     * <p>
     * 没有 ERROR 时视为通过；
     * WARNING 不直接阻止安装。
     * </p>
     *
     * @return 通过时返回 true
     */
    public boolean valid() {
        return issues
            .stream()
            .noneMatch(
                ThemePackageIssue::isError
            );
    }

    /**
     * 判断是否存在错误。
     *
     * @return 存在 ERROR 时返回 true
     */
    public boolean hasErrors() {
        return !valid();
    }

    /**
     * 判断是否存在警告。
     *
     * @return 存在 WARNING 时返回 true
     */
    public boolean hasWarnings() {
        return issues
            .stream()
            .anyMatch(
                ThemePackageIssue::isWarning
            );
    }

    /**
     * 返回错误列表。
     *
     * @return 不可修改的错误列表
     */
    public List<ThemePackageIssue> errors() {
        return issues
            .stream()
            .filter(
                ThemePackageIssue::isError
            )
            .toList();
    }

    /**
     * 返回警告列表。
     *
     * @return 不可修改的警告列表
     */
    public List<ThemePackageIssue> warnings() {
        return issues
            .stream()
            .filter(
                ThemePackageIssue::isWarning
            )
            .toList();
    }

    /**
     * 标准化允许为空的文本。
     *
     * @param value 原始值
     * @return 非 null 文本
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
}
