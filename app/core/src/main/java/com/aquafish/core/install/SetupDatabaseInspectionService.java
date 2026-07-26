package com.aquafish.core.install;

import com.aquafish.core.database.AquafishDatabaseTableCatalog;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.migration.r2dbc.R2dbcMigrationDatabaseState;
import com.aquafish.core.database.migration.r2dbc.R2dbcMigrationStateInspector;
import com.aquafish.core.installation.InstallationStateReadStatus;
import com.aquafish.core.installation.InstallationStateService;
import com.aquafish.core.installation.InstallationStateSnapshot;
import com.aquafish.core.installation.SystemInstallationRecord;
import java.util.Objects;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 安装向导数据库四状态只读识别服务。
 *
 * <p>该服务不创建、不删除、不覆盖任何数据库对象。</p>
 */
@Service
public final class SetupDatabaseInspectionService {

    private final R2dbcMigrationStateInspector
        migrationInspector;

    private final InstallationStateService
        installationStateService;

    public SetupDatabaseInspectionService(
        R2dbcMigrationStateInspector
            migrationInspector,
        InstallationStateService
            installationStateService
    ) {
        this.migrationInspector =
            Objects.requireNonNull(
                migrationInspector,
                "迁移状态检查器不能为空。"
            );

        this.installationStateService =
            Objects.requireNonNull(
                installationStateService,
                "安装状态服务不能为空。"
            );
    }

    /**
     * 使用用户填写的数据库配置执行只读识别。
     */
    public Mono<SetupDatabaseInspection> inspect(
        DatabaseSettings settings
    ) {
        return Mono.defer(
            () -> {
                DatabaseSettings safe =
                    requireSettings(
                        settings
                    );

                return migrationInspector
                    .inspect(safe)
                    .flatMap(
                        migration ->
                            installationStateService
                                .current(safe)
                                .map(
                                    state ->
                                        classify(
                                            migration,
                                            state
                                        )
                                )
                    )
                    .onErrorReturn(
                        unavailable()
                    );
            }
        );
    }

    /**
     * 合并迁移历史与 system_instances 状态。
     *
     * <p>包级可见只用于同包测试。</p>
     */
    SetupDatabaseInspection classify(
        R2dbcMigrationDatabaseState migration,
        InstallationStateSnapshot installation
    ) {
        Objects.requireNonNull(
            migration,
            "迁移状态不能为空。"
        );

        Objects.requireNonNull(
            installation,
            "安装状态不能为空。"
        );

        /*
         * 查询失败时绝不能解释为空数据库。
         */
        if (
            installation.status()
                == InstallationStateReadStatus
                    .DATABASE_UNAVAILABLE
        ) {
            return unavailable();
        }

        /*
         * 版本超前、迁移历史缺口或记录损坏：
         * 禁止安装器自动修改。
         */
        if (
            installation.status()
                == InstallationStateReadStatus
                    .INVALID_RECORD
            || migration.databaseAhead()
            || !migration.historyConsistent()
        ) {
            return result(
                SetupDatabaseMode
                    .INCOMPATIBLE_DATABASE,
                migration,
                installation,
                "数据库版本超前、迁移历史存在缺口/未知版本，"
                    + "或安装记录损坏。安装器不会自动修改该数据库。"
            );
        }

        /*
         * 完整 INSTALLED：
         * 禁止首次安装，后续只能恢复本机配置。
         */
        if (installation.installed()) {
            if (
                !migration
                    .migrationsTableExists()
            ) {
                return result(
                    SetupDatabaseMode
                        .INCOMPATIBLE_DATABASE,
                    migration,
                    installation,
                    "检测到 INSTALLED 记录，"
                        + "但缺少迁移历史表。"
                );
            }

            /*
             * 数据库已经是最新迁移版本时，
             * 正式业务表数量必须完整。
             *
             * 旧版本数据库存在 pending migrations 时，
             * 新表尚未建立属于正常升级状态。
             */
            if (
                migration.pendingMigrations()
                    == 0
                && migration.totalTables()
                    != AquafishDatabaseTableCatalog
                        .expectedTableCount()
            ) {
                return result(
                    SetupDatabaseMode
                        .INCOMPATIBLE_DATABASE,
                    migration,
                    installation,
                    "数据库版本最新，"
                        + "但 Aquafish 正式业务表数量不完整。"
                );
            }

            return result(
                SetupDatabaseMode
                    .EXISTING_INSTALLED,
                migration,
                installation,
                "检测到已经安装的 Aquafish。"
                    + "禁止再次执行首次安装；"
                    + "下一步将恢复当前电脑的 "
                    + "application.yaml 和 install.lock。"
            );
        }

        /*
         * 当前前缀下：
         * 1. 没有任何 Aquafish 正式业务表；
         * 2. 没有 migrations；
         * 3. 没有 migrations_lock；
         * 4. 没有 system_instances。
         *
         * 即可作为全新安装。
         *
         * 数据库中的其他程序表或其他表前缀不会被计入。
         */
        if (
            migration.totalTables() == 0
            && !migration
                .migrationsTableExists()
            && !migration
                .migrationsLockTableExists()
            && installation.status()
                == InstallationStateReadStatus
                    .TABLE_MISSING
        ) {
            return result(
                SetupDatabaseMode
                    .NEW_INSTALL,
                migration,
                installation,
                "当前表前缀下没有 Aquafish 表，"
                    + "可以安全执行首次安装。"
                    + "数据库中的其他程序表不会被删除。"
            );
        }

        /*
         * 其他情况均属于残留：
         * 部分表、迁移表、FAILED、INITIALIZING、
         * UNINITIALIZED 或空的 system_instances。
         */
        return result(
            SetupDatabaseMode
                .INCOMPLETE_INSTALLATION,
            migration,
            installation,
            "检测到部分 Aquafish 表、迁移历史"
                + "或未完成安装状态。"
                + "默认禁止继续，必须走明确确认的"
                + "残留清理流程。"
        );
    }

    private SetupDatabaseInspection result(
        SetupDatabaseMode mode,
        R2dbcMigrationDatabaseState migration,
        InstallationStateSnapshot installation,
        String note
    ) {
        SystemInstallationRecord record =
            installation
                .recordOptional()
                .orElse(null);

        return new SetupDatabaseInspection(
            mode,
            mode
                == SetupDatabaseMode
                    .NEW_INSTALL,
            mode
                == SetupDatabaseMode
                    .EXISTING_INSTALLED,
            mode
                == SetupDatabaseMode
                    .INCOMPLETE_INSTALLATION,
            mode
                == SetupDatabaseMode
                    .EXISTING_INSTALLED,
            record == null
                ? installation
                    .status()
                    .name()
                : record
                    .state()
                    .name(),
            version(
                migration.currentVersion()
            ),
            version(
                migration.latestVersion()
            ),
            migration.pendingMigrations(),
            migration.totalTables(),
            AquafishDatabaseTableCatalog
                .expectedTableCount(),
            migration
                .migrationsTableExists(),
            migration
                .historyConsistent(),
            record == null
                || record.installedAt()
                    == null
                ? ""
                : record
                    .installedAt()
                    .toString(),
            record == null
                || record.installedVersion()
                    == null
                ? ""
                : record
                    .installedVersion(),
            note
        );
    }

    private SetupDatabaseInspection unavailable() {
        return new SetupDatabaseInspection(
            SetupDatabaseMode
                .STATE_UNAVAILABLE,
            false,
            false,
            false,
            false,
            InstallationStateReadStatus
                .DATABASE_UNAVAILABLE
                .name(),
            "",
            "",
            0,
            0,
            AquafishDatabaseTableCatalog
                .expectedTableCount(),
            false,
            false,
            "",
            "",
            "数据库状态无法可靠读取，"
                + "安装器已安全停止。"
        );
    }

    private DatabaseSettings requireSettings(
        DatabaseSettings settings
    ) {
        if (settings == null) {
            throw new IllegalStateException(
                "数据库配置不能为空。"
            );
        }

        DatabaseSettings safe =
            settings.normalized();

        if (!safe.hasRequiredFields()) {
            throw new IllegalStateException(
                "数据库连接配置不完整。"
            );
        }

        return safe;
    }

    private String version(
        long value
    ) {
        return value <= 0
            ? ""
            : "V" + value;
    }
}
