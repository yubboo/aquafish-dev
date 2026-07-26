package com.aquafish.boot.database;

import com.aquafish.core.database.migration.DatabaseMigrationResult;
import com.aquafish.core.database.migration.r2dbc.R2dbcDatabaseMigrationService;
import com.aquafish.core.install.AuthoritativeInstallStatusService;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 已安装实例启动时的数据库升级入口。
 *
 * <p>纯净分发包尚未配置数据库或仍在首次安装时会直接跳过；
 * 只有 system_instances 明确为 INSTALLED 才执行版本升级。
 * 迁移失败会阻止应用继续启动，避免新代码在旧结构上运行。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(
    prefix = "aquafish.database",
    name = "auto-upgrade",
    havingValue = "true",
    matchIfMissing = true
)
public final class InstalledDatabaseUpgradeRunner
    implements ApplicationRunner {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            InstalledDatabaseUpgradeRunner.class
        );

    private static final Duration UPGRADE_TIMEOUT =
        Duration.ofMinutes(5);

    private final AuthoritativeInstallStatusService
        installStatusService;

    private final R2dbcDatabaseMigrationService
        migrationService;

    public InstalledDatabaseUpgradeRunner(
        AuthoritativeInstallStatusService installStatusService,
        R2dbcDatabaseMigrationService migrationService
    ) {
        this.installStatusService =
            Objects.requireNonNull(
                installStatusService,
                "权威安装状态服务不能为空。"
            );

        this.migrationService =
            Objects.requireNonNull(
                migrationService,
                "数据库迁移服务不能为空。"
            );
    }

    @Override
    public void run(
        ApplicationArguments arguments
    ) {
        Boolean installed =
            installStatusService
                .current()
                .map(status -> status.installed())
                .block(UPGRADE_TIMEOUT);

        if (!Boolean.TRUE.equals(installed)) {
            LOGGER.info(
                "数据库尚未完成首次安装，跳过已安装实例自动升级。"
            );
            return;
        }

        DatabaseMigrationResult result =
            migrationService
                .upgradeInstalledDatabase()
                .block(UPGRADE_TIMEOUT);

        if (result == null) {
            throw new IllegalStateException(
                "已安装实例数据库升级没有返回结果。"
            );
        }

        LOGGER.info(
            "已安装实例数据库版本检查完成：{} -> {}，实际执行迁移：{}。",
            result.previousVersion(),
            result.currentVersion(),
            result.migrated()
        );
    }
}
