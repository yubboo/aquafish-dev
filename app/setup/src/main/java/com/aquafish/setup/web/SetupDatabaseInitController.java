package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.database.migration.DatabaseMigrationPreview;
import com.aquafish.core.database.migration.DatabaseMigrationResult;
import com.aquafish.core.database.migration.r2dbc.R2dbcDatabaseMigrationService;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Aquafish 安装向导数据库初始化接口。
 *
 * <p>
 * 正式接口已经切换到：
 * </p>
 *
 * <ul>
 *     <li>Spring WebFlux；</li>
 *     <li>R2DBC；</li>
 *     <li>r2dbc-migrate；</li>
 *     <li>数据库安装状态机。</li>
 * </ul>
 *
 * <p>
 * 本 Controller 不调用旧 Flyway 迁移服务，
 * 也不在请求线程中调用 block。
 * </p>
 */
@RestController
public final class SetupDatabaseInitController {

    private final R2dbcDatabaseMigrationService
        migrationService;

    public SetupDatabaseInitController(
        R2dbcDatabaseMigrationService
            migrationService
    ) {
        this.migrationService =
            Objects.requireNonNull(
                migrationService,
                "R2DBC 数据库迁移服务不能为空。"
            );
    }

    /**
     * 响应式预览数据库初始化状态。
     */
    @PostMapping(
        "/api/setup/database/init/preview"
    )
    public Mono<ApiResult<DatabaseMigrationPreview>>
        preview() {

        return Mono
            .defer(
                migrationService::preview
            )
            .map(
                preview -> {
                    if (
                        !preview.canMigrate()
                        && !preview.upToDate()
                    ) {
                        return ApiResult.fail(
                            "DATABASE_INIT_PREVIEW_FAILED",
                            preview.note(),
                            preview
                        );
                    }

                    return ApiResult.ok(
                        preview,
                        "数据库初始化预览成功"
                    );
                }
            )
            .onErrorResume(
                error ->
                    Mono.just(
                        ApiResult
                            .<DatabaseMigrationPreview>fail(
                                "DATABASE_INIT_PREVIEW_FAILED",
                                safeMessage(
                                    error,
                                    "数据库初始化预览失败。"
                                )
                            )
                    )
            );
    }

    /**
     * 正式执行 R2DBC 数据库初始化。
     */
    @PostMapping(
        "/api/setup/database/init"
    )
    public Mono<ApiResult<DatabaseMigrationResult>>
        initialize() {

        return Mono
            .defer(
                migrationService::migrate
            )
            .map(
                result ->
                    ApiResult.ok(
                        result,
                        "数据库迁移完成"
                    )
            )
            .onErrorResume(
                error ->
                    Mono.just(
                        ApiResult
                            .<DatabaseMigrationResult>fail(
                                "DATABASE_INIT_FAILED",
                                safeMessage(
                                    error,
                                    "数据库初始化失败。"
                                )
                            )
                    )
            );
    }

    private static String safeMessage(
        Throwable error,
        String fallback
    ) {
        if (error == null) {
            return fallback;
        }

        String message =
            error.getMessage();

        return message == null
            || message.isBlank()
                ? fallback
                : message;
    }
}
