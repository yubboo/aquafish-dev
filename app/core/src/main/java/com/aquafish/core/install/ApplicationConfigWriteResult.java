package com.aquafish.core.install;

/**
 * application.yaml 写入结果。
 *
 * 当前阶段：
 * Step 17-22-3：安装配置写入 workdir/application.yaml。
 */
public record ApplicationConfigWriteResult(
    String applicationConfigFile,
    String backupFile,
    boolean written,
    boolean installed,
    String yaml,
    String note
) {
}
