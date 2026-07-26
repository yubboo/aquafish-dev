package com.aquafish.theme.install;

import java.util.List;

/**
 * 过期主题安装工作目录清理结果。
 *
 * @param scannedDirectories 扫描到的工作目录数量
 * @param deletedDirectories 成功删除的目录数量
 * @param failures 删除失败信息
 */
public record ThemeWorkspaceCleanupResult(
    int scannedDirectories,
    int deletedDirectories,
    List<String> failures
) {

    /**
     * 标准化清理结果。
     */
    public ThemeWorkspaceCleanupResult {
        if (scannedDirectories < 0) {
            throw new IllegalArgumentException(
                "扫描目录数量不能小于 0。"
            );
        }

        if (deletedDirectories < 0) {
            throw new IllegalArgumentException(
                "删除目录数量不能小于 0。"
            );
        }

        if (
            deletedDirectories
                > scannedDirectories
        ) {
            throw new IllegalArgumentException(
                "删除目录数量不能超过扫描数量。"
            );
        }

        failures = failures == null
            ? List.of()
            : List.copyOf(failures);
    }

    /**
     * 判断过期目录清理是否全部成功。
     *
     * @return 没有失败项时返回 true
     */
    public boolean success() {
        return failures.isEmpty();
    }
}
