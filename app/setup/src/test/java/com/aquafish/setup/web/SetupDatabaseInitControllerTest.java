package com.aquafish.setup.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.migration.DatabaseMigrationPreview;
import com.aquafish.core.database.migration.DatabaseMigrationResult;
import com.aquafish.core.database.migration.r2dbc.R2dbcDatabaseMigrationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Setup 数据库初始化响应式接口测试。
 */
class SetupDatabaseInitControllerTest {

    private R2dbcDatabaseMigrationService
        migrationService;

    private SetupDatabaseInitController
        controller;

    @BeforeEach
    void setUp() {
        migrationService =
            mock(
                R2dbcDatabaseMigrationService.class
            );

        controller =
            new SetupDatabaseInitController(
                migrationService
            );
    }

    @Test
    void shouldRemainLazyBeforeSubscription() {
        Mono<?> response =
            controller.initialize();

        assertNotNull(response);

        verifyNoInteractions(
            migrationService
        );
    }

    @Test
    void shouldReturnSuccessfulR2dbcPreview() {
        DatabaseMigrationPreview preview =
            preview(
                true,
                false,
                4,
                "可以执行 R2DBC 迁移。"
            );

        when(
            migrationService.preview()
        ).thenReturn(
            Mono.just(preview)
        );

        StepVerifier
            .create(
                controller.preview()
            )
            .assertNext(
                result -> {
                    assertTrue(
                        result.success()
                    );

                    assertSame(
                        preview,
                        result.data()
                    );
                }
            )
            .verifyComplete();
    }

    @Test
    void shouldReturnSuccessfulCurrentPreview() {
        DatabaseMigrationPreview preview =
            preview(
                false,
                true,
                0,
                "数据库已经是最新版本。"
            );

        when(
            migrationService.preview()
        ).thenReturn(
            Mono.just(preview)
        );

        StepVerifier
            .create(
                controller.preview()
            )
            .assertNext(
                result ->
                    assertTrue(
                        result.success()
                    )
            )
            .verifyComplete();
    }

    @Test
    void shouldRejectUnsafePreviewState() {
        DatabaseMigrationPreview preview =
            preview(
                false,
                false,
                4,
                "检测到未受管数据库。"
            );

        when(
            migrationService.preview()
        ).thenReturn(
            Mono.just(preview)
        );

        StepVerifier
            .create(
                controller.preview()
            )
            .assertNext(
                result -> {
                    org.junit.jupiter.api.Assertions
                        .assertFalse(
                            result.success()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            "DATABASE_INIT_PREVIEW_FAILED",
                            result.code()
                        );

                    assertSame(
                        preview,
                        result.data()
                    );
                }
            )
            .verifyComplete();
    }

    @Test
    void shouldConvertPreviewErrorToApiResult() {
        when(
            migrationService.preview()
        ).thenReturn(
            Mono.error(
                new IllegalStateException(
                    "数据库连接失败"
                )
            )
        );

        StepVerifier
            .create(
                controller.preview()
            )
            .assertNext(
                result -> {
                    org.junit.jupiter.api.Assertions
                        .assertFalse(
                            result.success()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            "DATABASE_INIT_PREVIEW_FAILED",
                            result.code()
                        );

                    assertTrue(
                        result.message()
                            .contains(
                                "数据库连接失败"
                            )
                    );
                }
            )
            .verifyComplete();
    }

    @Test
    void shouldRunR2dbcMigration() {
        DatabaseMigrationResult migrationResult =
            new DatabaseMigrationResult(
                DatabaseType.POSTGRESQL,
                "",
                "4",
                4,
                0,
                true,
                "数据库迁移完成。"
            );

        when(
            migrationService.migrate()
        ).thenReturn(
            Mono.just(
                migrationResult
            )
        );

        StepVerifier
            .create(
                controller.initialize()
            )
            .assertNext(
                result -> {
                    assertTrue(
                        result.success()
                    );

                    assertSame(
                        migrationResult,
                        result.data()
                    );
                }
            )
            .verifyComplete();
    }

    @Test
    void shouldConvertMigrationFailureToApiResult() {
        when(
            migrationService.migrate()
        ).thenReturn(
            Mono.error(
                new IllegalStateException(
                    "迁移历史存在版本缺口"
                )
            )
        );

        StepVerifier
            .create(
                controller.initialize()
            )
            .assertNext(
                result -> {
                    org.junit.jupiter.api.Assertions
                        .assertFalse(
                            result.success()
                        );

                    org.junit.jupiter.api.Assertions
                        .assertEquals(
                            "DATABASE_INIT_FAILED",
                            result.code()
                        );

                    assertTrue(
                        result.message()
                            .contains(
                                "版本缺口"
                            )
                    );
                }
            )
            .verifyComplete();
    }

    private DatabaseMigrationPreview preview(
        boolean canMigrate,
        boolean historyExists,
        int pendingMigrations,
        String note
    ) {
        return new DatabaseMigrationPreview(
            true,
            canMigrate,
            historyExists,
            false,
            "aq_migrations",
            historyExists
                ? "4"
                : "",
            pendingMigrations,
            List.of(),
            note,
            ""
        );
    }
}
