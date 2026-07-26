package com.aquafish.theme.validation;

/**
 * 主题压缩包安全校验策略。
 *
 * <p>
 * 本对象集中保存所有安全限制，
 * 避免 ThemePackageValidator 中散落魔法数字。
 * </p>
 *
 * @param maxPackageBytes ZIP 文件本身最大字节数
 * @param maxEntryCount 最大 ZIP 条目数量
 * @param maxSingleFileBytes 单文件最大解压字节数
 * @param maxTotalUncompressedBytes 最大总解压字节数
 * @param maxCompressionRatio 最大允许压缩比
 * @param maxPathLength 最大条目路径长度
 * @param maxPathDepth 最大目录层级
 * @param maxManifestBytes theme.yaml 最大字节数
 */
public record ThemePackageValidationPolicy(
    long maxPackageBytes,
    int maxEntryCount,
    long maxSingleFileBytes,
    long maxTotalUncompressedBytes,
    double maxCompressionRatio,
    int maxPathLength,
    int maxPathDepth,
    long maxManifestBytes
) {

    /**
     * 1 MiB。
     */
    private static final long MIB =
        1024L * 1024L;

    /**
     * Aquafish 默认主题包安全策略。
     *
     * <p>默认限制：</p>
     *
     * <ul>
     *     <li>ZIP 本身最大 50 MiB；</li>
     *     <li>最多 5000 个条目；</li>
     *     <li>单文件解压后最大 20 MiB；</li>
     *     <li>总解压大小最大 200 MiB；</li>
     *     <li>单文件最大压缩比 100 倍；</li>
     *     <li>条目路径最长 240 个字符；</li>
     *     <li>目录层级最大 32 层；</li>
     *     <li>theme.yaml 最大 1 MiB。</li>
     * </ul>
     *
     * @return 默认策略
     */
    public static ThemePackageValidationPolicy
        defaults() {

        return new ThemePackageValidationPolicy(
            50L * MIB,
            5000,
            20L * MIB,
            200L * MIB,
            100.0D,
            240,
            32,
            1L * MIB
        );
    }

    /**
     * 验证策略数值。
     */
    public ThemePackageValidationPolicy {
        requirePositive(
            maxPackageBytes,
            "主题包最大字节数"
        );

        requirePositive(
            maxEntryCount,
            "ZIP 最大条目数量"
        );

        requirePositive(
            maxSingleFileBytes,
            "单文件最大字节数"
        );

        requirePositive(
            maxTotalUncompressedBytes,
            "总解压最大字节数"
        );

        if (
            !Double.isFinite(maxCompressionRatio)
                || maxCompressionRatio <= 1.0D
        ) {
            throw new IllegalArgumentException(
                "最大压缩比必须是大于 1 的有限数值。"
            );
        }

        requirePositive(
            maxPathLength,
            "最大路径长度"
        );

        requirePositive(
            maxPathDepth,
            "最大路径层级"
        );

        requirePositive(
            maxManifestBytes,
            "theme.yaml 最大字节数"
        );

        if (
            maxSingleFileBytes
                > maxTotalUncompressedBytes
        ) {
            throw new IllegalArgumentException(
                "单文件最大字节数不能大于总解压最大字节数。"
            );
        }

        if (
            maxManifestBytes
                > maxSingleFileBytes
        ) {
            throw new IllegalArgumentException(
                "theme.yaml 最大字节数不能大于单文件最大字节数。"
            );
        }
    }

    /**
     * 验证 long 数值必须大于零。
     *
     * @param value 数值
     * @param fieldName 字段名称
     */
    private static void requirePositive(
        long value,
        String fieldName
    ) {
        if (value <= 0L) {
            throw new IllegalArgumentException(
                fieldName + "必须大于 0。"
            );
        }
    }

    /**
     * 验证 int 数值必须大于零。
     *
     * @param value 数值
     * @param fieldName 字段名称
     */
    private static void requirePositive(
        int value,
        String fieldName
    ) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                fieldName + "必须大于 0。"
            );
        }
    }
}
