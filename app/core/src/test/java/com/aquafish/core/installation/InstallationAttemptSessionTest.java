package com.aquafish.core.installation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquafish.core.database.DatabaseSettings;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 当前响应式安装尝试会话测试。
 */
class InstallationAttemptSessionTest {

    @Test
    void shouldRemainLazyBeforeSubscription() {
        InstallationStateService stateService =
            mock(
                InstallationStateService.class
            );

        InstallationAttemptSession session =
            new InstallationAttemptSession(
                stateService
            );

        Mono<InitializationClaim> result =
            session.claimAfterMigration(
                DatabaseSettings.defaultMysql()
            );

        org.junit.jupiter.api.Assertions
            .assertNotNull(result);

        verifyNoInteractions(stateService);
    }

    @Test
    void shouldReadCurrentAttemptLazily() {
        InstallationStateService stateService =
            mock(
                InstallationStateService.class
            );

        InstallationAttemptSession session =
            new InstallationAttemptSession(
                stateService
            );

        Mono<UUID> result =
            session.requireCurrentAttemptId();

        org.junit.jupiter.api.Assertions
            .assertNotNull(result);

        verifyNoInteractions(stateService);
    }

    @Test
    void shouldRememberAcquiredAttempt() {
        InstallationStateService stateService =
            mock(
                InstallationStateService.class
            );

        when(
            stateService.tryStartInitialization(
                any(DatabaseSettings.class),
                any(UUID.class),
                any(Instant.class)
            )
        ).thenAnswer(
            invocation -> {
                UUID attemptId =
                    invocation.getArgument(1);

                Instant startedAt =
                    invocation.getArgument(2);

                return Mono.just(
                    InitializationClaim.acquired(
                        attemptId,
                        initializingRecord(
                            attemptId,
                            startedAt
                        )
                    )
                );
            }
        );

        InstallationAttemptSession session =
            new InstallationAttemptSession(
                stateService
            );

        StepVerifier
            .create(
                session.claimAfterMigration(
                    DatabaseSettings.defaultMysql()
                )
            )
            .assertNext(
                claim -> {
                    org.junit.jupiter.api.Assertions
                        .assertTrue(
                            claim.acquired()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            claim.attemptId(),
                            session
                                .cachedAttemptId()
                                .orElseThrow()
                        );
                }
            )
            .verifyComplete();
    }

    @Test
    void shouldAdoptExistingInitializingAttempt() {
        InstallationStateService stateService =
            mock(
                InstallationStateService.class
            );

        UUID existingAttemptId =
            UUID.randomUUID();

        Instant startedAt =
            Instant.parse(
                "2026-07-15T00:00:00Z"
            );

        when(
            stateService.tryStartInitialization(
                any(DatabaseSettings.class),
                any(UUID.class),
                any(Instant.class)
            )
        ).thenReturn(
            Mono.just(
                InitializationClaim
                    .alreadyInitializing(
                        initializingRecord(
                            existingAttemptId,
                            startedAt
                        )
                    )
            )
        );

        InstallationAttemptSession session =
            new InstallationAttemptSession(
                stateService
            );

        StepVerifier
            .create(
                session.claimAfterMigration(
                    DatabaseSettings.defaultMysql()
                )
            )
            .assertNext(
                claim -> {
                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            InitializationClaimStatus
                                .ALREADY_INITIALIZING,
                            claim.status()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            existingAttemptId,
                            session
                                .cachedAttemptId()
                                .orElseThrow()
                        );
                }
            )
            .verifyComplete();
    }

    @Test
    void shouldRecoverAttemptFromDatabase() {
        InstallationStateService stateService =
            mock(
                InstallationStateService.class
            );

        UUID attemptId =
            UUID.randomUUID();

        Instant startedAt =
            Instant.parse(
                "2026-07-15T00:00:00Z"
            );

        when(stateService.current())
            .thenReturn(
                Mono.just(
                    InstallationStateSnapshot
                        .found(
                            initializingRecord(
                                attemptId,
                                startedAt
                            )
                        )
                )
            );

        InstallationAttemptSession session =
            new InstallationAttemptSession(
                stateService
            );

        StepVerifier
            .create(
                session.requireCurrentAttemptId()
            )
            .expectNext(attemptId)
            .verifyComplete();

        org.junit.jupiter.api.Assertions
            .assertEquals(
                attemptId,
                session
                    .cachedAttemptId()
                    .orElseThrow()
            );

        verify(stateService).current();
    }

    @Test
    void shouldRejectWhenDatabaseIsUnavailable() {
        InstallationStateService stateService =
            mock(
                InstallationStateService.class
            );

        when(stateService.current())
            .thenReturn(
                Mono.just(
                    InstallationStateSnapshot
                        .databaseUnavailable(
                            "数据库操作失败。"
                        )
                )
            );

        InstallationAttemptSession session =
            new InstallationAttemptSession(
                stateService
            );

        StepVerifier
            .create(
                session.requireCurrentAttemptId()
            )
            .expectError(
                IllegalStateException.class
            )
            .verify();
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
}
