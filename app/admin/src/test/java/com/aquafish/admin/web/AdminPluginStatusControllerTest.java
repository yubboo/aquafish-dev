package com.aquafish.admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquafish.core.admin.auth.AdminAuthUser;
import com.aquafish.plugin.runtime.PluginManagementSnapshot;
import com.aquafish.plugin.runtime.PluginRuntimeLifecycleService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AdminPluginStatusControllerTest {

    private PluginRuntimeLifecycleService lifecycleService;
    private AdminPluginStatusController controller;

    @BeforeEach
    void setUp() {
        lifecycleService = mock(PluginRuntimeLifecycleService.class);
        controller = new AdminPluginStatusController(lifecycleService);
    }

    @Test
    void shouldStartPluginForSuperAdmin() {
        PluginManagementSnapshot snapshot = emptySnapshot();
        when(lifecycleService.start("demo", 7L))
            .thenReturn(Mono.just(snapshot));

        StepVerifier.create(
                controller.start(authentication(true), "demo")
            )
            .assertNext(response -> {
                assertThat(response.getStatusCode()).isEqualTo(
                    HttpStatus.OK
                );
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().data()).isSameAs(snapshot);
            })
            .verifyComplete();

        verify(lifecycleService).start("demo", 7L);
    }

    @Test
    void shouldRejectLifecycleWriteForNormalAdmin() {
        StepVerifier.create(
                controller.stop(authentication(false), "demo")
            )
            .assertNext(response -> {
                assertThat(response.getStatusCode()).isEqualTo(
                    HttpStatus.BAD_REQUEST
                );
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().success()).isFalse();
                assertThat(response.getBody().message())
                    .contains("超级管理员");
            })
            .verifyComplete();
    }

    private Authentication authentication(boolean superAdmin) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(
            new AdminAuthUser(
                7L,
                "admin",
                "",
                "管理员",
                "",
                "ACTIVE",
                superAdmin
                    ? List.of("super_admin")
                    : List.of("admin"),
                superAdmin
            )
        );
        return authentication;
    }

    private PluginManagementSnapshot emptySnapshot() {
        return new PluginManagementSnapshot(
            true,
            true,
            true,
            0,
            List.of(),
            Set.of(),
            "ready"
        );
    }
}
