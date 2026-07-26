package com.aquafish.core.installation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.installation.r2dbc.ReactiveInstallationStateStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 统一响应式数据库安装状态服务测试。
 */
class InstallationStateServiceTest {

    @Test
    void shouldRemainLazyBeforeSubscription() {
        Fixture fixture =
            fixture();

        Mono<InstallationStateSnapshot> result =
            fixture.service.current();

        org.junit.jupiter.api.Assertions
            .assertNotNull(result);

        verifyNoInteractions(
            fixture.settingsService,
            fixture.store
        );
    }

    @Test
    void shouldReadUsingCurrentDatabaseSettings() {
        Fixture fixture =
            fixture();

        InstallationStateSnapshot expected =
            InstallationStateSnapshot.absent();

        when(fixture.store.read(fixture.settings))
            .thenReturn(Mono.just(expected));

        StepVerifier
            .create(fixture.service.current())
            .expectNext(expected)
            .verifyComplete();

        verify(fixture.store).read(
            fixture.settings
        );
    }

    @Test
    void shouldDelegateInitializationClaim() {
        Fixture fixture =
            fixture();

        UUID attemptId =
            UUID.randomUUID();

        Instant startedAt =
            Instant.parse(
                "2026-07-15T00:00:00Z"
            );

        InitializationClaim expected =
            InitializationClaim.acquired(
                attemptId,
                initializingRecord(
                    attemptId,
                    startedAt
                )
            );

        when(
            fixture.store.tryStartInitialization(
                fixture.settings,
                attemptId,
                startedAt
            )
        ).thenReturn(Mono.just(expected));

        StepVerifier
            .create(
                fixture.service
                    .tryStartInitialization(
                        attemptId,
                        startedAt
                    )
            )
            .expectNext(expected)
            .verifyComplete();
    }

    @Test
    void shouldDelegateInstallationCompletion() {
        Fixture fixture =
            fixture();

        UUID attemptId =
            UUID.randomUUID();

        Instant installedAt =
            Instant.parse(
                "2026-07-15T00:10:00Z"
            );

        SystemInstallationRecord expected =
            installedRecord(
                attemptId,
                installedAt
            );

        when(
            fixture.store.markInstalled(
                fixture.settings,
                attemptId,
                installedAt,
                "0.0.1-dev"
            )
        ).thenReturn(Mono.just(expected));

        StepVerifier
            .create(
                fixture.service.completeInstallation(
                    attemptId,
                    installedAt,
                    "0.0.1-dev"
                )
            )
            .expectNext(expected)
            .verifyComplete();
    }

    @Test
    void shouldDelegateInitializationFailure() {
        Fixture fixture =
            fixture();

        UUID attemptId =
            UUID.randomUUID();

        Instant failedAt =
            Instant.parse(
                "2026-07-15T00:05:00Z"
            );

        SystemInstallationRecord expected =
            failedRecord(
                attemptId,
                failedAt
            );

        when(
            fixture.store.markFailed(
                fixture.settings,
                attemptId,
                failedAt,
                "ADMIN_CREATE_FAILED",
                "管理员创建失败。"
            )
        ).thenReturn(Mono.just(expected));

        StepVerifier
            .create(
                fixture.service.failInitialization(
                    attemptId,
                    failedAt,
                    "ADMIN_CREATE_FAILED",
                    "管理员创建失败。"
                )
            )
            .expectNext(expected)
            .verifyComplete();
    }

    private Fixture fixture() {
        DatabaseRuntimeSettingsService settingsService =
            mock(
                DatabaseRuntimeSettingsService.class
            );

        ReactiveInstallationStateStore store =
            mock(
                ReactiveInstallationStateStore.class
            );

        DatabaseSettings settings =
            DatabaseSettings
                .defaultMysql()
                .normalized();

        when(settingsService.current())
            .thenReturn(settings);

        return new Fixture(
            settingsService,
            store,
            settings,
            new InstallationStateService(
                settingsService,
                store
            )
        );
    }

    private static SystemInstallationRecord
        initializingRecord(
            UUID attemptId,
            Instant startedAt
        ) {

        return new SystemInstallationRecord(
            (short) 1,
            UUID.randomUUID(),
            InstallationState.INITIALIZING,
            1,
            attemptId,
            startedAt,
            null,
            null,
            null,
            null,
            startedAt,
            startedAt
        );
    }

    private static SystemInstallationRecord
        installedRecord(
            UUID attemptId,
            Instant installedAt
        ) {

        return new SystemInstallationRecord(
            (short) 1,
            UUID.randomUUID(),
            InstallationState.INSTALLED,
            2,
            attemptId,
            installedAt.minusSeconds(60),
            installedAt,
            "0.0.1-dev",
            null,
            null,
            installedAt.minusSeconds(60),
            installedAt
        );
    }

    private static SystemInstallationRecord failedRecord(
        UUID attemptId,
        Instant failedAt
    ) {
        return new SystemInstallationRecord(
            (short) 1,
            UUID.randomUUID(),
            InstallationState.FAILED,
            2,
            attemptId,
            failedAt.minusSeconds(60),
            null,
            null,
            "ADMIN_CREATE_FAILED",
            "管理员创建失败。",
            failedAt.minusSeconds(60),
            failedAt
        );
    }

    private record Fixture(
        DatabaseRuntimeSettingsService
            settingsService,
        ReactiveInstallationStateStore store,
        DatabaseSettings settings,
        InstallationStateService service
    ) {
    }
}
