package com.aquafish.core.installation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 数据库安装状态快照测试。
 */
class InstallationStateSnapshotTest {

    /**
     * 表存在但没有记录时允许创建初始化记录。
     */
    @Test
    void shouldAllowInitializationWhenRecordIsAbsent() {
        InstallationStateSnapshot snapshot =
            InstallationStateSnapshot
                .absent();

        assertTrue(snapshot.schemaReady());
        assertTrue(snapshot.authoritative());
        assertTrue(
            snapshot.canStartInitialization()
        );
        assertFalse(snapshot.installed());
    }

    /**
     * 数据库不可用时不能当作尚未安装。
     */
    @Test
    void shouldNotTreatDatabaseFailureAsUninstalled() {
        InstallationStateSnapshot snapshot =
            InstallationStateSnapshot
                .databaseUnavailable(
                    "数据库操作失败。"
                );

        assertFalse(snapshot.authoritative());
        assertFalse(snapshot.schemaReady());
        assertFalse(
            snapshot.canStartInitialization()
        );
        assertFalse(
            snapshot.databaseReachable()
        );
    }

    /**
     * 已安装记录必须关闭初始化入口。
     */
    @Test
    void shouldCloseInitializationForInstalledRecord() {
        Instant now = Instant.now();

        SystemInstallationRecord record =
            new SystemInstallationRecord(
                (short) 1,
                UUID.randomUUID(),
                InstallationState.INSTALLED,
                2,
                UUID.randomUUID(),
                now,
                now,
                "0.0.1-dev",
                null,
                null,
                now,
                now
            );

        InstallationStateSnapshot snapshot =
            InstallationStateSnapshot
                .found(record);

        assertTrue(snapshot.installed());
        assertFalse(
            snapshot.canStartInitialization()
        );
        assertTrue(snapshot.authoritative());
    }

    /**
     * RECORD_FOUND 必须包含记录。
     */
    @Test
    void shouldRejectInconsistentSnapshot() {
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InstallationStateSnapshot(
                    InstallationStateReadStatus
                        .RECORD_FOUND,
                    null,
                    null,
                    null
                )
        );
    }
}
