package com.aquafish.core.install;

/**
 * 安装向导数据库只读识别结果。
 *
 * <p>该响应不返回密码、完整连接串或数据库内部错误。</p>
 */
public record SetupDatabaseInspection(
    SetupDatabaseMode mode,
    boolean newInstallAllowed,
    boolean recoveryAllowed,
    boolean residueCleanupAllowed,
    boolean fullReinstallAllowed,
    String installationState,
    String currentVersion,
    String latestVersion,
    int pendingMigrations,
    long existingAquafishTables,
    int expectedAquafishTables,
    boolean migrationsTableExists,
    boolean migrationHistoryConsistent,
    String installedAt,
    String installedVersion,
    String note
) {

    /**
     * 清理可空字段。
     */
    public SetupDatabaseInspection {
        if (mode == null) {
            throw new IllegalArgumentException(
                "数据库识别状态不能为空。"
            );
        }

        installationState =
            text(installationState);

        currentVersion =
            text(currentVersion);

        latestVersion =
            text(latestVersion);

        installedAt =
            text(installedAt);

        installedVersion =
            text(installedVersion);

        note =
            text(note);
    }

    private static String text(
        String value
    ) {
        return value == null
            ? ""
            : value.trim();
    }
}
