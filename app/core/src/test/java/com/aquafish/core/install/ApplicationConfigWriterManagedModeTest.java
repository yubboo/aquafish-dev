package com.aquafish.core.install;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.redis.RedisRuntimeSettingsService;
import com.aquafish.core.redis.RedisSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 托管模式配置写入测试：平台数据库/Redis密码只能留在环境变量，不能复制到 workdir。
 */
class ApplicationConfigWriterManagedModeTest {

    @TempDir
    Path tempDir;

    @Test
    void managedSecretsAreNotWrittenToApplicationYaml() {
        Path workDir = tempDir.resolve("workdir");
        DatabaseRuntimeSettingsService database = new DatabaseRuntimeSettingsService(
            "mysql", "mysql", 3306, "aquafish", "aquafish", "database-secret", "aq_"
        );
        RedisRuntimeSettingsService redis = new RedisRuntimeSettingsService(
            true, "redis", 6379, 0, "", "redis-secret", false
        );
        InstallLockService lockService = new InstallLockService(workDir.toString());
        SetupDeploymentContextService contextService = new SetupDeploymentContextService(
            "onepanel", "environment", "environment", true, "1.0",
            database, redis, lockService
        );
        AuthoritativeInstallStatusService statusService = mock(AuthoritativeInstallStatusService.class);
        when(statusService.current()).thenReturn(Mono.just(
            new AuthoritativeInstallStatus(false, false, true, true, "NOT_CONFIGURED", false, null, null)
        ));
        ApplicationConfigWriterService writer = new ApplicationConfigWriterService(
            workDir.toString(), statusService, database, redis, contextService
        );
        SetupApplicationConfigRequest request = new SetupApplicationConfigRequest(
            8520,
            null,
            RedisSettings.disabled(),
            SiteSettings.defaultSettings(),
            "default"
        );

        StepVerifier.create(writer.write(request))
            .assertNext(result -> {
                String yaml;
                try {
                    yaml = Files.readString(workDir.resolve("application.yaml"));
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
                assertTrue(result.written());
                assertTrue(yaml.contains("port: 8520"));
                assertTrue(yaml.contains("连接参数由部署平台环境变量管理"));
                assertFalse(yaml.contains("database-secret"));
                assertFalse(yaml.contains("redis-secret"));
            })
            .verifyComplete();
    }
}
