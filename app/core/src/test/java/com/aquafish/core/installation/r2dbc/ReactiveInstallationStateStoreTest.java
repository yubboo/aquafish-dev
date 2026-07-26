package com.aquafish.core.installation.r2dbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aquafish.core.database.DatabaseSettings;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 响应式安装状态仓库接口契约测试。
 */
class ReactiveInstallationStateStoreTest {

    @Test
    void shouldExposeOnlyReactiveDatabaseOperations()
        throws Exception {

        assertEquals(
            Mono.class,
            ReactiveInstallationStateStore
                .class
                .getMethod(
                    "read",
                    DatabaseSettings.class
                )
                .getReturnType()
        );

        assertEquals(
            Mono.class,
            ReactiveInstallationStateStore
                .class
                .getMethod(
                    "tryStartInitialization",
                    DatabaseSettings.class,
                    UUID.class,
                    Instant.class
                )
                .getReturnType()
        );

        assertEquals(
            Mono.class,
            ReactiveInstallationStateStore
                .class
                .getMethod(
                    "markInstalled",
                    DatabaseSettings.class,
                    UUID.class,
                    Instant.class,
                    String.class
                )
                .getReturnType()
        );

        assertEquals(
            Mono.class,
            ReactiveInstallationStateStore
                .class
                .getMethod(
                    "markFailed",
                    DatabaseSettings.class,
                    UUID.class,
                    Instant.class,
                    String.class,
                    String.class
                )
                .getReturnType()
        );
    }
}
