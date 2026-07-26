package com.aquafish.core.install;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.installation.InstallationAttemptSession;
import com.aquafish.core.installation.InstallationState;
import com.aquafish.core.installation.InstallationStateService;
import com.aquafish.core.installation.InstallationStateSnapshot;
import com.aquafish.core.installation.SystemInstallationRecord;
import com.aquafish.core.installation.SystemInstallationSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 安装最终提交的响应式编排与恢复测试。
 */
class SetupFinishServiceTest {

    @TempDir
    Path workDir;

    private DatabaseRuntimeSettingsService settingsService;
    private ReactiveSetupAdminAccountStore accountStore;
    private InstallationStateService stateService;
    private InstallationAttemptSession attemptSession;
    private InstallLockService lockService;
    private SetupFinishService service;
    private DatabaseSettings settings;
    private UUID attemptId;
    private SetupFinishRequest request;

    @BeforeEach
    void setUp() throws Exception {
        settingsService = mock(DatabaseRuntimeSettingsService.class);
        accountStore = mock(ReactiveSetupAdminAccountStore.class);
        stateService = mock(InstallationStateService.class);
        attemptSession = mock(InstallationAttemptSession.class);
        lockService = new InstallLockService(workDir.toString());
        settings = DatabaseSettings.defaultMysql();
        attemptId = UUID.randomUUID();
        request = new SetupFinishRequest(
            new SetupAdminAccountRequest(
                "admin",
                "admin@example.com",
                "AquaFish-2026!",
                "超级管理员"
            ),
            SiteSettings.defaultSettings()
        );

        Files.createDirectories(workDir);
        Files.writeString(
            workDir.resolve("application.yaml"),
            "aquafish:\n  install:\n    locked: false\n"
        );
        when(settingsService.current()).thenReturn(settings);

        service = new SetupFinishService(
            settingsService,
            accountStore,
            stateService,
            attemptSession,
            lockService
        );
    }

    @Test
    void finishShouldRemainLazyAndCommitThroughReactiveStore() {
        Instant completedAt = Instant.parse("2026-07-16T00:00:00Z");
        when(stateService.current(settings))
            .thenReturn(Mono.just(snapshot(InstallationState.INITIALIZING)));
        when(
            accountStore.finishInstallation(
                eq(settings),
                eq(request.admin()),
                anyString(),
                eq(request.site()),
                eq(attemptId),
                any(Instant.class),
                eq(SystemInstallationSchema.CURRENT_INSTALLATION_VERSION)
            )
        ).thenReturn(
            Mono.just(
                new SetupFinishDatabaseResult(
                    1L,
                    "admin",
                    completedAt,
                    SystemInstallationSchema.CURRENT_INSTALLATION_VERSION,
                    false
                )
            )
        );

        Mono<SetupFinishResult> result = service.finish(request);
        verifyNoInteractions(stateService, accountStore, attemptSession);

        StepVerifier.create(result)
            .assertNext(finished -> {
                assertTrue(finished.installed());
                assertTrue(finished.locked());
                assertTrue(Files.exists(lockService.lockFile()));
            })
            .verifyComplete();

        ArgumentCaptor<String> hash =
            ArgumentCaptor.forClass(String.class);
        verify(accountStore).finishInstallation(
            eq(settings),
            eq(request.admin()),
            hash.capture(),
            eq(request.site()),
            eq(attemptId),
            any(Instant.class),
            eq(SystemInstallationSchema.CURRENT_INSTALLATION_VERSION)
        );
        assertTrue(hash.getValue().startsWith("$2"));
        verify(attemptSession).clear(attemptId);
    }

    @Test
    void retryShouldRestoreCompatibilityLockFromInstalledDatabase() {
        Instant completedAt = Instant.parse("2026-07-16T00:00:00Z");
        when(stateService.current(settings))
            .thenReturn(Mono.just(snapshot(InstallationState.INSTALLED)));
        when(
            accountStore.finishInstallation(
                eq(settings),
                eq(request.admin()),
                anyString(),
                eq(request.site()),
                eq(attemptId),
                any(Instant.class),
                eq(SystemInstallationSchema.CURRENT_INSTALLATION_VERSION)
            )
        ).thenReturn(
            Mono.just(
                new SetupFinishDatabaseResult(
                    1L,
                    "admin",
                    completedAt,
                    SystemInstallationSchema.CURRENT_INSTALLATION_VERSION,
                    true
                )
            )
        );

        assertFalse(Files.exists(lockService.lockFile()));

        StepVerifier.create(service.finish(request))
            .assertNext(result -> {
                assertTrue(result.installed());
                assertTrue(result.note().contains("恢复"));
                assertTrue(Files.exists(lockService.lockFile()));
            })
            .verifyComplete();
    }

    @Test
    void databaseFailureShouldNotWriteCompatibilityLock() {
        when(stateService.current(settings))
            .thenReturn(Mono.just(snapshot(InstallationState.INITIALIZING)));
        when(
            accountStore.finishInstallation(
                eq(settings),
                eq(request.admin()),
                anyString(),
                eq(request.site()),
                eq(attemptId),
                any(Instant.class),
                eq(SystemInstallationSchema.CURRENT_INSTALLATION_VERSION)
            )
        ).thenReturn(
            Mono.error(
                new IllegalStateException("事务回滚")
            )
        );

        StepVerifier.create(service.finish(request))
            .expectErrorMessage("事务回滚")
            .verify();

        assertFalse(Files.exists(lockService.lockFile()));
        verifyNoInteractions(attemptSession);
    }

    private InstallationStateSnapshot snapshot(
        InstallationState state
    ) {
        Instant startedAt = Instant.parse("2026-07-15T23:00:00Z");
        Instant installedAt =
            state == InstallationState.INSTALLED
                ? Instant.parse("2026-07-16T00:00:00Z")
                : null;

        return InstallationStateSnapshot.found(
            new SystemInstallationRecord(
                SystemInstallationSchema.PRIMARY_SINGLETON_ID,
                UUID.randomUUID(),
                state,
                state == InstallationState.INSTALLED ? 2L : 1L,
                attemptId,
                startedAt,
                installedAt,
                state == InstallationState.INSTALLED
                    ? SystemInstallationSchema.CURRENT_INSTALLATION_VERSION
                    : null,
                null,
                null,
                startedAt,
                installedAt == null ? startedAt : installedAt
            )
        );
    }
}
