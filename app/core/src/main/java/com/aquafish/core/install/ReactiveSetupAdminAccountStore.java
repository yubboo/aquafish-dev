package com.aquafish.core.install;

import com.aquafish.core.database.DatabaseSettings;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * 安装阶段超级管理员的响应式数据访问边界。
 */
public interface ReactiveSetupAdminAccountStore {

    /**
     * 只读检查创建管理员所需的数据库状态。
     */
    Mono<SetupAdminDatabaseState> inspect(
        DatabaseSettings settings
    );

    /**
     * 在一个响应式事务中创建并绑定超级管理员。
     */
    Mono<Long> create(
        DatabaseSettings settings,
        SetupAdminAccountRequest request,
        String passwordHash
    );

    /**
     * 在一个响应式事务中创建或恢复超级管理员、保存站点设置，
     * 并把匹配的初始化尝试提交为 INSTALLED。
     */
    Mono<SetupFinishDatabaseResult> finishInstallation(
        DatabaseSettings settings,
        SetupAdminAccountRequest request,
        String passwordHash,
        SiteSettings site,
        UUID attemptId,
        Instant installedAt,
        String installedVersion
    );
}
