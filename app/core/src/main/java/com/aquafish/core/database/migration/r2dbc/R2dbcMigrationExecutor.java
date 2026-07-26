package com.aquafish.core.database.migration.r2dbc;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.r2dbc.RuntimeR2dbcConnectionFactory;
import java.util.Objects;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Aquafish 响应式数据库迁移执行器。
 *
 * <p>
 * 本执行器负责把安装向导提交的数据库配置
 * 与运行时动态 ConnectionFactory、迁移计划和
 * r2dbc-migrate 正式执行入口连接起来。
 * </p>
 *
 * <p>
 * 本类不会：
 * </p>
 *
 * <ul>
 *     <li>使用 JDBC；</li>
 *     <li>调用 block；</li>
 *     <li>自行提交或回滚阻塞式数据库事务；</li>
 *     <li>把数据库密码写入日志或返回值。</li>
 * </ul>
 */
@Service
public final class R2dbcMigrationExecutor {

    private final DatabaseRuntimeSettingsService
        settingsService;

    private final RuntimeR2dbcConnectionFactory
        connectionFactory;

    private final R2dbcMigrationFactory
        migrationFactory;

    private final R2dbcMigrationRunner
        migrationRunner;

    public R2dbcMigrationExecutor(
        DatabaseRuntimeSettingsService settingsService,
        RuntimeR2dbcConnectionFactory connectionFactory,
        R2dbcMigrationFactory migrationFactory,
        R2dbcMigrationRunner migrationRunner
    ) {
        this.settingsService =
            Objects.requireNonNull(
                settingsService,
                "数据库运行配置服务不能为空。"
            );

        this.connectionFactory =
            Objects.requireNonNull(
                connectionFactory,
                "运行时 R2DBC 连接工厂不能为空。"
            );

        this.migrationFactory =
            Objects.requireNonNull(
                migrationFactory,
                "R2DBC 迁移计划工厂不能为空。"
            );

        this.migrationRunner =
            Objects.requireNonNull(
                migrationRunner,
                "R2DBC 迁移运行器不能为空。"
            );
    }

    /**
     * 使用当前运行时数据库配置执行迁移。
     */
    public Mono<Void> migrate() {
        return Mono.defer(
            () ->
                migrate(
                    settingsService.current()
                )
        );
    }

    /**
     * 使用安装流程提交的明确配置执行迁移。
     *
     * <p>
     * 所有准备动作都放在 Mono.defer 中，
     * 在真正订阅前不会：
     * </p>
     *
     * <ul>
     *     <li>覆盖当前数据库配置；</li>
     *     <li>创建或切换连接池；</li>
     *     <li>扫描 SQL 资源；</li>
     *     <li>连接数据库。</li>
     * </ul>
     */
    public Mono<Void> migrate(
        DatabaseSettings settings
    ) {
        return Mono.defer(
            () -> {
                DatabaseSettings safeSettings =
                    requireSettings(
                        settings
                    );

                /*
                 * 首次安装时 application.yaml
                 * 虽然已经写入，但当前 Spring 进程不会重新加载。
                 *
                 * useForInstallation 确保当前进程马上采用
                 * 安装向导提交的完全相同配置。
                 */
                settingsService.useForInstallation(
                    safeSettings
                );

                /*
                 * RuntimeR2dbcConnectionFactory 会在真正创建连接时
                 * 比较数据库配置指纹。配置未变化时复用连接池，
                 * 配置变化时才切换并关闭旧连接池。
                 *
                 * 这里禁止无条件 refresh，避免并发迁移、状态读取
                 * 或事务仍在使用共享连接池时被提前关闭。
                 */

                R2dbcMigrationPlan plan =
                    migrationFactory.create(
                        safeSettings
                    );

                return migrationRunner.migrate(
                    connectionFactory,
                    plan
                );
            }
        );
    }

    private DatabaseSettings requireSettings(
        DatabaseSettings settings
    ) {
        if (settings == null) {
            throw new IllegalStateException(
                "数据库迁移配置不能为空。"
            );
        }

        DatabaseSettings normalized =
            settings.normalized();

        if (!normalized.hasRequiredFields()) {
            throw new IllegalStateException(
                "数据库迁移配置不完整。"
            );
        }

        return normalized;
    }
}
