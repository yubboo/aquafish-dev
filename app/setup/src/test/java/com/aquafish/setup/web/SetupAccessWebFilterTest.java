package com.aquafish.setup.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquafish.core.install.AuthoritativeInstallStatus;
import com.aquafish.core.install.AuthoritativeInstallStatusService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SetupAccessWebFilterTest {

    @Test
    void staleCompatibilityLockDoesNotBlockInstallation() {
        AuthoritativeInstallStatusService statusService = statusService(
            status(false, true, true, true, "RECORD_ABSENT")
        );
        SetupAccessWebFilter filter = new SetupAccessWebFilter(statusService);
        AtomicInteger calls = new AtomicInteger();

        StepVerifier.create(filter.filter(exchange("/api/setup/config/write"), chain(calls)))
            .verifyComplete();

        assertEquals(1, calls.get());
    }

    @Test
    void databaseInstalledBlocksInstallationEvenWhenLockIsMissing() {
        AuthoritativeInstallStatusService statusService = statusService(
            status(true, false, false, true, "INSTALLED")
        );
        SetupAccessWebFilter filter = new SetupAccessWebFilter(statusService);
        AtomicInteger blockedCalls = new AtomicInteger();

        MockServerWebExchange blocked = exchange("/api/setup/config/write");
        StepVerifier.create(filter.filter(blocked, chain(blockedCalls))).verifyComplete();

        assertEquals(HttpStatus.CONFLICT, blocked.getResponse().getStatusCode());
        assertEquals(0, blockedCalls.get());

        AtomicInteger allowedCalls = new AtomicInteger();
        StepVerifier.create(filter.filter(exchange("/api/setup/status"), chain(allowedCalls)))
            .verifyComplete();
        assertEquals(1, allowedCalls.get());
    }

    @Test
    void databaseResetUsesItsOwnTargetDatabaseVerification() {
        AuthoritativeInstallStatusService statusService =
            statusService(
                status(
                    false,
                    false,
                    false,
                    false,
                    "DATABASE_UNAVAILABLE"
                )
            );
        SetupAccessWebFilter filter =
            new SetupAccessWebFilter(statusService);
        AtomicInteger calls = new AtomicInteger();

        StepVerifier.create(
            filter.filter(
                exchange("/api/setup/database/reset"),
                chain(calls)
            )
        ).verifyComplete();

        assertEquals(1, calls.get());
    }

    @Test
    void existingRecoveryUsesItsOwnInstalledVerification() {
        AuthoritativeInstallStatusService statusService =
            statusService(
                status(
                    true,
                    true,
                    false,
                    true,
                    "INSTALLED"
                )
            );
        SetupAccessWebFilter filter =
            new SetupAccessWebFilter(statusService);
        AtomicInteger calls = new AtomicInteger();

        StepVerifier.create(
            filter.filter(
                exchange("/api/setup/recovery/existing"),
                chain(calls)
            )
        ).verifyComplete();

        assertEquals(1, calls.get());
    }

    @Test
    void databaseStateFailureClosesInstallationEndpoint() {
        AuthoritativeInstallStatusService statusService = statusService(
            status(false, false, false, false, "DATABASE_UNAVAILABLE")
        );
        SetupAccessWebFilter filter = new SetupAccessWebFilter(statusService);
        AtomicInteger calls = new AtomicInteger();
        MockServerWebExchange exchange = exchange("/api/setup/config/write");

        StepVerifier.create(filter.filter(exchange, chain(calls))).verifyComplete();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertEquals(0, calls.get());
    }

    private AuthoritativeInstallStatusService statusService(
        AuthoritativeInstallStatus status
    ) {
        AuthoritativeInstallStatusService service = mock(
            AuthoritativeInstallStatusService.class
        );
        when(service.current()).thenReturn(Mono.just(status));
        return service;
    }

    private AuthoritativeInstallStatus status(
        boolean installed,
        boolean locked,
        boolean canInstall,
        boolean stateAvailable,
        String databaseState
    ) {
        return new AuthoritativeInstallStatus(
            installed,
            locked,
            canInstall,
            stateAvailable,
            databaseState,
            true,
            null,
            null
        );
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
    }

    private WebFilterChain chain(AtomicInteger calls) {
        return exchange -> {
            calls.incrementAndGet();
            return Mono.empty();
        };
    }
}
