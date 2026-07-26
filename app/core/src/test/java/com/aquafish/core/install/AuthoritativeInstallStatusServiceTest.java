package com.aquafish.core.install;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquafish.core.installation.InstallationState;
import com.aquafish.core.installation.InstallationStateService;
import com.aquafish.core.installation.InstallationStateSnapshot;
import com.aquafish.core.installation.SystemInstallationRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthoritativeInstallStatusServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void ignoresStaleLockWhenDatabaseIsNotConfigured() {
        InstallLockService lockService = lockService();
        lockService.writeInstallLock("installed=true");
        InstallationStateService database = database(
            InstallationStateSnapshot.databaseUnavailable("连接失败。")
        );

        StepVerifier.create(service(database, lockService).current())
            .assertNext(status -> {
                assertFalse(status.installed());
                assertTrue(status.locked());
                assertTrue(status.canInstall());
                assertTrue(status.stateAvailable());
                assertTrue("NOT_CONFIGURED".equals(status.databaseState()));
            })
            .verifyComplete();
    }

    @Test
    void databaseInstalledRecoversMissingCompatibilityLock() throws IOException {
        InstallLockService lockService = lockService();
        createApplicationConfig(lockService);
        InstallationStateService database = database(
            InstallationStateSnapshot.found(installedRecord())
        );

        StepVerifier.create(service(database, lockService).current())
            .assertNext(status -> {
                assertTrue(status.installed());
                assertTrue(status.locked());
                assertFalse(status.canInstall());
                assertTrue(status.stateAvailable());
                assertTrue("INSTALLED".equals(status.databaseState()));
            })
            .verifyComplete();

        assertTrue(Files.exists(lockService.lockFile()));
    }

    @Test
    void environmentConfiguredDatabaseDoesNotRequireLocalApplicationFile() {
        InstallLockService lockService = lockService();
        InstallationStateService database = database(
            InstallationStateSnapshot.found(installedRecord())
        );

        StepVerifier.create(service(database, lockService, "environment").current())
            .assertNext(status -> {
                assertTrue(status.installed());
                assertTrue(status.locked());
                assertFalse(status.applicationConfigExists());
                assertTrue("INSTALLED".equals(status.databaseState()));
            })
            .verifyComplete();
    }

    @Test
    void staleLockCannotOverrideDatabaseInitializingState() throws IOException {
        InstallLockService lockService = lockService();
        createApplicationConfig(lockService);
        lockService.writeInstallLock("installed=true");
        InstallationStateService database = database(
            InstallationStateSnapshot.found(initializingRecord())
        );

        StepVerifier.create(service(database, lockService).current())
            .assertNext(status -> {
                assertFalse(status.installed());
                assertTrue(status.locked());
                assertTrue(status.canInstall());
                assertTrue(status.stateAvailable());
                assertTrue("INITIALIZING".equals(status.databaseState()));
            })
            .verifyComplete();
    }

    @Test
    void configuredDatabaseFailureFailsClosed() throws IOException {
        InstallLockService lockService = lockService();
        createApplicationConfig(lockService);
        InstallationStateService database = database(
            InstallationStateSnapshot.databaseUnavailable("连接失败。")
        );

        StepVerifier.create(service(database, lockService).current())
            .assertNext(status -> {
                assertFalse(status.installed());
                assertFalse(status.canInstall());
                assertFalse(status.stateAvailable());
                assertTrue("DATABASE_UNAVAILABLE".equals(status.databaseState()));
            })
            .verifyComplete();
    }

    private AuthoritativeInstallStatusService service(
        InstallationStateService database,
        InstallLockService lockService
    ) {
        return service(database, lockService, "installer");
    }

    private AuthoritativeInstallStatusService service(
        InstallationStateService database,
        InstallLockService lockService,
        String databaseSource
    ) {
        return new AuthoritativeInstallStatusService(
            database,
            lockService,
            databaseSource
        );
    }

    private InstallationStateService database(InstallationStateSnapshot snapshot) {
        InstallationStateService database = mock(InstallationStateService.class);
        when(database.current()).thenReturn(Mono.just(snapshot));
        return database;
    }

    private InstallLockService lockService() {
        return new InstallLockService(tempDir.resolve("workdir").toString());
    }

    private void createApplicationConfig(InstallLockService lockService) throws IOException {
        Files.createDirectories(lockService.workDir());
        Files.writeString(lockService.workDir().resolve("application.yaml"), "aquafish: {}\n");
    }

    private SystemInstallationRecord installedRecord() {
        Instant installedAt = Instant.parse("2026-07-16T00:00:00Z");
        return new SystemInstallationRecord(
            (short) 1,
            UUID.randomUUID(),
            InstallationState.INSTALLED,
            2,
            UUID.randomUUID(),
            installedAt.minusSeconds(60),
            installedAt,
            "0.0.1-dev",
            null,
            null,
            installedAt.minusSeconds(60),
            installedAt
        );
    }

    private SystemInstallationRecord initializingRecord() {
        Instant startedAt = Instant.parse("2026-07-16T00:00:00Z");
        return new SystemInstallationRecord(
            (short) 1,
            UUID.randomUUID(),
            InstallationState.INITIALIZING,
            1,
            UUID.randomUUID(),
            startedAt,
            null,
            null,
            null,
            null,
            startedAt,
            startedAt
        );
    }
}
