package com.aquafish.core.install;

import java.util.List;

/**
 * 首次安装向导的可信服务端上下文。
 *
 * <p>前端据此决定是否展示数据库/Redis编辑步骤。上下文由启动配置生成，
 * 不接受 URL 查询参数覆盖，避免用户通过修改地址绕过托管配置约束。</p>
 */
public record SetupDeploymentContext(
    String deploymentType,
    String deploymentLabel,
    String databaseSource,
    String redisSource,
    boolean databaseManaged,
    boolean redisManaged,
    boolean redisConfigured,
    boolean licenseRequired,
    String licenseVersion,
    boolean environmentReady,
    SetupDatabaseSummary database,
    List<SetupEnvironmentCheck> checks
) {
}
