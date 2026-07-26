package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.database.DatabaseConnectionTestResult;
import com.aquafish.core.database.DatabaseConnectionTester;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.DatabaseType;
import com.aquafish.core.database.r2dbc.R2dbcConnectionFactoryBuilder;
import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.install.SetupDeploymentContextService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 安装向导数据库接口。
 *
 * 当前阶段：
 * Step 17-22-1：数据库安装配置与连接测试底层。
 *
 * 接口：
 * GET  /api/setup/database/types
 * POST /api/setup/database/test
 *
 * 注意：
 * 这里目前只做数据库连接测试。
 * 不写入配置。
 * 不初始化表。
 * 不创建管理员。
 */
@RestController
public class SetupDatabaseController {

    private final DatabaseConnectionTester databaseConnectionTester;

    private final DatabaseRuntimeSettingsService databaseSettingsService;

    private final SetupDeploymentContextService deploymentContextService;

    public SetupDatabaseController(
        DatabaseConnectionTester databaseConnectionTester,
        DatabaseRuntimeSettingsService databaseSettingsService,
        SetupDeploymentContextService deploymentContextService
    ) {
        this.databaseConnectionTester = databaseConnectionTester;
        this.databaseSettingsService = databaseSettingsService;
        this.deploymentContextService = deploymentContextService;
    }

    /**
     * 检测 1Panel/Docker 在服务器端注入的数据库连接。
     *
     * <p>接口不接收浏览器数据库参数，避免托管模式被请求体覆盖；响应也不会
     * 返回密码。分发安装必须继续使用 /api/setup/database/test。</p>
     */
    @PostMapping("/api/setup/database/managed/test")
    public Mono<ApiResult<DatabaseConnectionTestResult>> testManaged() {
        if (!deploymentContextService.managedDatabase()) {
            return Mono.just(
                ApiResult.fail(
                    "DATABASE_NOT_MANAGED",
                    "当前是分发安装模式，请在安装器中填写数据库。"
                )
            );
        }

        return databaseConnectionTester.test(databaseSettingsService.current())
            .map(result -> result.connected()
                ? ApiResult.ok(result, "平台数据库连接成功")
                : ApiResult.fail(
                    "MANAGED_DATABASE_CONNECTION_FAILED",
                    "平台提供的数据库连接失败，请返回部署平台检查数据库服务："
                        + result.errorMessage(),
                    result
                ));
    }

    /**
     * 获取安装器支持的数据库类型。
     */
    @GetMapping("/api/setup/database/types")
    public ApiResult<DatabaseTypesResponse> types() {
        DatabaseSettings mysql = DatabaseSettings.defaultMysql();
        DatabaseSettings mariadb = DatabaseSettings.defaultMariadb();
        DatabaseSettings postgresql = DatabaseSettings.defaultPostgresql();

        return ApiResult.ok(
            new DatabaseTypesResponse(
                List.of(
                    new DatabaseTypeOption(
                        DatabaseType.MYSQL.value(),
                        "MySQL",
                        3306,
                        "推荐 MySQL 8.x。",
                        R2dbcConnectionFactoryBuilder.displayUrl(mysql)
                    ),
                    new DatabaseTypeOption(
                        DatabaseType.MARIADB.value(),
                        "MariaDB",
                        3306,
                        "适合已经使用 MariaDB 的服务器和面板环境。",
                        R2dbcConnectionFactoryBuilder.displayUrl(mariadb)
                    ),
                    new DatabaseTypeOption(
                        DatabaseType.POSTGRESQL.value(),
                        "PostgreSQL",
                        5432,
                        "适合更偏工程化、强一致性和长期扩展的部署。",
                        R2dbcConnectionFactoryBuilder.displayUrl(postgresql)
                    )
                )
            ),
            "数据库类型获取成功"
        );
    }

    /**
     * 测试数据库连接。
     */
    @PostMapping("/api/setup/database/test")
    public Mono<ApiResult<DatabaseConnectionTestResult>> test(
        @RequestBody DatabaseSettings request
    ) {
        return databaseConnectionTester.test(request)
            .map(result -> result.connected()
                ? ApiResult.ok(result, "数据库连接成功")
                : ApiResult.fail(
                    "DATABASE_CONNECTION_FAILED",
                    "数据库连接失败：" + result.errorMessage(),
                    result
                ));
    }

    public record DatabaseTypesResponse(
        List<DatabaseTypeOption> types
    ) {
    }

    public record DatabaseTypeOption(
        String value,
        String label,
        int defaultPort,
        String description,
        String exampleConnectionUrl
    ) {
    }
}
