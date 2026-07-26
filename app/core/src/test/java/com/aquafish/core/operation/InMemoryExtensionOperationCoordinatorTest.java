package com.aquafish.core.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * JVM 内存扩展操作协调器测试。
 */
class InMemoryExtensionOperationCoordinatorTest {

    /**
     * 同一个操作键不能被两个线程同时持有。
     *
     * @throws Exception 并发测试失败
     */
    @Test
    void shouldRejectSecondThreadForSameKey()
        throws Exception {

        ExtensionOperationCoordinator
            firstCoordinator =
                new InMemoryExtensionOperationCoordinator();

        ExtensionOperationCoordinator
            secondCoordinator =
                new InMemoryExtensionOperationCoordinator();

        Optional<ExtensionOperationHandle>
            firstAttempt =
                firstCoordinator.tryAcquire(
                    ExtensionOperationKeys
                        .THEME_GLOBAL
                );

        assertTrue(firstAttempt.isPresent());

        ExecutorService executor =
            Executors.newSingleThreadExecutor();

        try (
            ExtensionOperationHandle ignored =
                firstAttempt.orElseThrow()
        ) {
            Future<Boolean> secondResult =
                executor.submit(
                    () ->
                        secondCoordinator
                            .tryAcquire(
                                ExtensionOperationKeys
                                    .THEME_GLOBAL
                            )
                            .isPresent()
                );

            assertFalse(
                secondResult.get()
            );
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 释放操作键后可以再次取得。
     */
    @Test
    void shouldAcquireAgainAfterRelease() {
        ExtensionOperationCoordinator
            coordinator =
                new InMemoryExtensionOperationCoordinator();

        Optional<ExtensionOperationHandle>
            firstAttempt =
                coordinator.tryAcquire(
                    ExtensionOperationKeys
                        .THEME_GLOBAL
                );

        assertTrue(firstAttempt.isPresent());

        firstAttempt
            .orElseThrow()
            .close();

        Optional<ExtensionOperationHandle>
            secondAttempt =
                coordinator.tryAcquire(
                    ExtensionOperationKeys
                        .THEME_GLOBAL
                );

        assertTrue(secondAttempt.isPresent());

        secondAttempt
            .orElseThrow()
            .close();
    }

    /**
     * 不同操作键可以并行存在。
     */
    @Test
    void shouldAllowDifferentOperationKeys() {
        ExtensionOperationCoordinator
            coordinator =
                new InMemoryExtensionOperationCoordinator();

        Optional<ExtensionOperationHandle>
            themeAttempt =
                coordinator.tryAcquire(
                    ExtensionOperationKeys
                        .THEME_GLOBAL
                );

        Optional<ExtensionOperationHandle>
            pluginAttempt =
                coordinator.tryAcquire(
                    ExtensionOperationKeys
                        .PLUGIN_GLOBAL
                );

        assertTrue(themeAttempt.isPresent());
        assertTrue(pluginAttempt.isPresent());

        themeAttempt.orElseThrow().close();
        pluginAttempt.orElseThrow().close();
    }

    /**
     * 当前线程不能重复取得同一操作键。
     */
    @Test
    void shouldRejectReentrantBusinessOperation() {
        ExtensionOperationCoordinator
            coordinator =
                new InMemoryExtensionOperationCoordinator();

        Optional<ExtensionOperationHandle>
            firstAttempt =
                coordinator.tryAcquire(
                    ExtensionOperationKeys
                        .THEME_GLOBAL
                );

        assertTrue(firstAttempt.isPresent());

        try (
            ExtensionOperationHandle ignored =
                firstAttempt.orElseThrow()
        ) {
            assertTrue(
                coordinator
                    .tryAcquire(
                        ExtensionOperationKeys
                            .THEME_GLOBAL
                    )
                    .isEmpty()
            );
        }
    }

    /**
     * 非法操作键会被拒绝。
     */
    @Test
    void shouldRejectInvalidOperationKey() {
        ExtensionOperationCoordinator
            coordinator =
                new InMemoryExtensionOperationCoordinator();

        assertThrows(
            IllegalArgumentException.class,
            () -> coordinator.tryAcquire(
                "../theme"
            )
        );

        assertEquals(
            "theme:sample-theme",
            ExtensionOperationKeys.theme(
                "Sample-Theme"
            )
        );
    }
}
