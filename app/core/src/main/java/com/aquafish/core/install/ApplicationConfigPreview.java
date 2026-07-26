package com.aquafish.core.install;

/**
 * application.yaml 预览结果。
 *
 * 当前阶段：
 * Step 17-22-3：安装配置写入 workdir/application.yaml。
 */
public record ApplicationConfigPreview(
    String applicationConfigFile,
    boolean installed,
    boolean canWrite,
    String yaml,
    String note
) {
}
