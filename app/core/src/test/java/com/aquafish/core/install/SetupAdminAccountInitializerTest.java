package com.aquafish.core.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 安装管理员响应式策略测试。
 */
class SetupAdminAccountInitializerTest {

    @TempDir
    Path workDir;

    private DatabaseRuntimeSettingsService
        settingsService;

    private ReactiveSetupAdminAccountStore
        accountStore;

    private AuthoritativeInstallStatusService
        installStatusService;

    private SetupAdminAccountInitializer
        initializer;

    private DatabaseSettings settings;

    @BeforeEach
    void setUp() {
        settingsService =
            mock(
                DatabaseRuntimeSettingsService.class
            );
        accountStore =
            mock(
                ReactiveSetupAdminAccountStore.class
            );
        installStatusService =
            mock(
                AuthoritativeInstallStatusService.class
            );
        settings =
            DatabaseSettings
                .defaultMysql();

        when(settingsService.current())
            .thenReturn(settings);
        when(installStatusService.current())
            .thenReturn(
                Mono.just(
                    new AuthoritativeInstallStatus(
                        false,
                        false,
                        true,
                        true,
                        "INITIALIZING",
                        true,
                        null,
                        null
                    )
                )
            );

        initializer =
            new SetupAdminAccountInitializer(
                settingsService,
                accountStore,
                installStatusService
            );
    }

    @Test
    void previewShouldRemainLazyBeforeSubscription() {
        Mono<SetupAdminPreview> preview =
            initializer.preview(validRequest());

        verifyNoInteractions(
            settingsService,
            accountStore
        );

        when(accountStore.inspect(settings))
            .thenReturn(
                Mono.just(
                    new SetupAdminDatabaseState(
                        true,
                        true,
                        false
                    )
                )
            );

        StepVerifier.create(preview)
            .assertNext(result -> {
                assertTrue(result.canCreate());
                assertTrue(result.connected());
                assertEquals(
                    "aq_users",
                    result.usersTable()
                );
            })
            .verifyComplete();
    }

    @Test
    void invalidRequestShouldNotTouchDatabase() {
        StepVerifier.create(
            initializer.preview(
                new SetupAdminAccountRequest(
                    "x y",
                    "invalid",
                    "short",
                    "管理员"
                )
            )
        ).assertNext(result -> {
            assertFalse(result.canCreate());
            assertFalse(result.connected());
            assertTrue(
                result.note()
                    .contains("用户名")
            );
        }).verifyComplete();

        verifyNoInteractions(
            settingsService,
            accountStore
        );
    }

    @Test
    void createShouldStoreOnlyBcryptHash() {
        ArgumentCaptor<String> hashCaptor =
            ArgumentCaptor.forClass(
                String.class
            );

        SetupAdminAccountRequest request =
            validRequest();

        when(
            accountStore.create(
                eq(settings),
                eq(request),
                any(String.class)
            )
        ).thenReturn(
            Mono.just(27L)
        );

        StepVerifier.create(
            initializer.create(request)
        ).assertNext(result -> {
            assertTrue(result.created());
            assertEquals(27L, result.userId());
            assertEquals(
                "super_admin",
                result.roleKey()
            );
        }).verifyComplete();

        verify(accountStore).create(
            eq(settings),
            eq(request),
            hashCaptor.capture()
        );

        String passwordHash =
            hashCaptor.getValue();

        assertNotEquals(
            request.password(),
            passwordHash
        );
        assertTrue(
            passwordHash.startsWith("$2")
        );
        assertTrue(
            new BCryptPasswordEncoder().matches(
                request.password(),
                passwordHash
            )
        );
    }

    @Test
    void previewShouldReturnSanitizedDatabaseFailure() {
        when(accountStore.inspect(settings))
            .thenReturn(
                Mono.error(
                    new IllegalStateException(
                        "password=secret"
                    )
                )
            );

        StepVerifier.create(
            initializer.preview(validRequest())
        ).assertNext(result -> {
            assertFalse(result.canCreate());
            assertFalse(result.connected());
            assertFalse(
                result.errorMessage()
                    .contains("secret")
            );
            assertTrue(
                result.errorMessage()
                    .contains("数据库暂时不可用")
            );
        }).verifyComplete();
    }

    private SetupAdminAccountRequest validRequest() {
        return new SetupAdminAccountRequest(
            "admin",
            "admin@example.com",
            "AquaFish-2026!",
            "超级管理员"
        );
    }
}
