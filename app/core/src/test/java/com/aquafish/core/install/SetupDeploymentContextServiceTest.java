package com.aquafish.core.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.redis.RedisRuntimeSettingsService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 部署上下文回归测试，确保托管来源由服务端配置决定，并且公开摘要不包含密码。
 */
class SetupDeploymentContextServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void onePanelUsesManagedSourcesWithoutExposingPassword() {
        DatabaseRuntimeSettingsService database = database("database-secret");
        RedisRuntimeSettingsService redis = redis(true, "redis-secret");
        SetupDeploymentContextService service = new SetupDeploymentContextService(
            "onepanel",
            "environment",
            "environment",
            true,
            "1.0",
            database,
            redis,
            new InstallLockService(tempDir.resolve("workdir").toString())
        );

        SetupDeploymentContext context = service.current();

        assertEquals("onepanel", context.deploymentType());
        assertTrue(context.databaseManaged());
        assertTrue(context.redisManaged());
        assertTrue(context.redisConfigured());
        assertTrue(context.database().passwordConfigured());
        assertFalse(context.database().toString().contains("database-secret"));
    }

    @Test
    void archiveKeepsDatabaseAndRedisEditable() {
        SetupDeploymentContextService service = new SetupDeploymentContextService(
            "archive",
            "installer",
            "installer",
            true,
            "1.0",
            database(""),
            redis(false, ""),
            new InstallLockService(tempDir.resolve("archive-workdir").toString())
        );

        SetupDeploymentContext context = service.current();

        assertFalse(context.databaseManaged());
        assertFalse(context.redisManaged());
        assertEquals("分发包部署", context.deploymentLabel());
    }

    /**
     * 环境页必须来自真实探针，而不是只根据 Files.isWritable 猜测目录可用。
     * 临时目录和磁盘检查也必须作为独立结果返回，供安装页逐项展示。
     */
    @Test
    void environmentIncludesRealWriteDiskAndMemoryChecks() {
        Path workDir = tempDir.resolve("real-probe-workdir");
        SetupDeploymentContext context = service(workDir).current();

        assertTrue(check(context, "workdir-write").passed());
        assertTrue(check(context, "workdir-write").detail().contains("实际创建"));
        assertTrue(check(context, "temp-write").passed());
        assertTrue(check(context, "disk-space").detail().contains("当前可用"));
        assertTrue(check(context, "jvm-memory").detail().contains("最大堆内存"));
        assertTrue(Files.isDirectory(workDir));
    }

    /**
     * 当 workdir 实际是普通文件时，创建探针必然失败，安装环境必须明确阻断。
     */
    @Test
    void environmentRejectsWorkDirThatIsARegularFile() throws IOException {
        Path invalidWorkDir = tempDir.resolve("workdir-file");
        Files.writeString(invalidWorkDir, "not-a-directory");

        SetupDeploymentContext context = service(invalidWorkDir).current();

        assertFalse(check(context, "workdir-write").passed());
        assertFalse(context.environmentReady());
    }

    private SetupDeploymentContextService service(Path workDir) {
        return new SetupDeploymentContextService(
            "archive",
            "installer",
            "installer",
            true,
            "1.0",
            database(""),
            redis(false, ""),
            new InstallLockService(workDir.toString())
        );
    }

    private SetupEnvironmentCheck check(SetupDeploymentContext context, String key) {
        return context.checks().stream()
            .filter(item -> key.equals(item.key()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("缺少环境检查项：" + key));
    }

    private DatabaseRuntimeSettingsService database(String password) {
        return new DatabaseRuntimeSettingsService(
            "mysql", "mysql", 3306, "aquafish", "aquafish", password, "aq_"
        );
    }

    private RedisRuntimeSettingsService redis(boolean enabled, String password) {
        return new RedisRuntimeSettingsService(
            enabled, "redis", 6379, 0, "", password, false
        );
    }
}
