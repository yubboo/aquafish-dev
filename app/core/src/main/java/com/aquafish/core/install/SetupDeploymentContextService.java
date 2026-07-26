package com.aquafish.core.install;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.redis.RedisRuntimeSettingsService;
import com.aquafish.core.redis.RedisSettings;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 解析 Aquafish 的部署来源，并生成首次安装页面使用的可信上下文。
 *
 * <p>关联 application.yml、Docker Compose 和 1Panel 应用包。部署平台通过
 * AQUAFISH_DEPLOYMENT_TYPE、AQUAFISH_SETUP_DATABASE_SOURCE 等服务端变量声明
 * 配置来源；浏览器只能读取脱敏后的结果，不能通过查询参数切换安装模式。</p>
 *
 * <p>本服务同时执行安装前的真实环境探测。写入权限不是只读取文件属性，而是
 * 创建、写入并删除临时探针文件；磁盘、JVM 内存和 R2DBC 驱动也读取当前进程的
 * 实际状态。探针只留下检测结果，不会保留测试文件或返回服务器绝对路径。</p>
 */
@Service
public class SetupDeploymentContextService {

    private static final long MIN_USABLE_DISK_BYTES = 512L * 1024L * 1024L;
    private static final long MIN_MAX_HEAP_BYTES = 256L * 1024L * 1024L;

    private final DeploymentType deploymentType;
    private final SetupConfigurationSource databaseSource;
    private final SetupConfigurationSource redisSource;
    private final boolean licenseRequired;
    private final String licenseVersion;
    private final DatabaseRuntimeSettingsService databaseSettingsService;
    private final RedisRuntimeSettingsService redisSettingsService;
    private final InstallLockService installLockService;

    public SetupDeploymentContextService(
        @Value("${aquafish.deployment.type:archive}") String deploymentType,
        @Value("${aquafish.setup.database-source:installer}") String databaseSource,
        @Value("${aquafish.setup.redis-source:installer}") String redisSource,
        @Value("${aquafish.setup.license-required:true}") boolean licenseRequired,
        @Value("${aquafish.setup.license-version:1.0}") String licenseVersion,
        DatabaseRuntimeSettingsService databaseSettingsService,
        RedisRuntimeSettingsService redisSettingsService,
        InstallLockService installLockService
    ) {
        this.deploymentType = DeploymentType.fromValue(deploymentType);
        this.databaseSource = SetupConfigurationSource.fromValue(databaseSource);
        this.redisSource = SetupConfigurationSource.fromValue(redisSource);
        this.licenseRequired = licenseRequired;
        this.licenseVersion = textOrDefault(licenseVersion, "1.0");
        this.databaseSettingsService = Objects.requireNonNull(databaseSettingsService);
        this.redisSettingsService = Objects.requireNonNull(redisSettingsService);
        this.installLockService = Objects.requireNonNull(installLockService);
    }

    /**
     * 构建一次安全、脱敏且基于当前服务器真实状态的安装上下文快照。
     */
    public SetupDeploymentContext current() {
        DatabaseSettings database = databaseSettingsService.current().normalized();
        RedisSettings redis = redisSettingsService.current().normalized();
        List<SetupEnvironmentCheck> checks = environmentChecks(database);
        boolean environmentReady = checks.stream()
            .filter(SetupEnvironmentCheck::required)
            .allMatch(SetupEnvironmentCheck::passed);

        return new SetupDeploymentContext(
            deploymentType.value(),
            deploymentType.label(),
            databaseSource.value(),
            redisSource.value(),
            managedDatabase(),
            managedRedis(),
            redis.enabled(),
            licenseRequired,
            licenseVersion,
            environmentReady,
            new SetupDatabaseSummary(
                database.type().value(),
                database.host(),
                database.port(),
                database.name(),
                database.username(),
                database.tablePrefix(),
                database.password() != null && !database.password().isBlank()
            ),
            checks
        );
    }

    public boolean managedDatabase() {
        return databaseSource == SetupConfigurationSource.ENVIRONMENT;
    }

    public boolean managedRedis() {
        return redisSource == SetupConfigurationSource.ENVIRONMENT;
    }

    /**
     * 执行安装器真正依赖的服务器能力检查。
     *
     * <p>分发安装在后续步骤由用户填写数据库，因此这里不拿内置数据库默认值
     * 冒充已配置；只有 1Panel/Docker 托管模式才额外校验平台注入字段是否完整。</p>
     */
    private List<SetupEnvironmentCheck> environmentChecks(DatabaseSettings database) {
        List<SetupEnvironmentCheck> checks = new ArrayList<>();
        int javaFeature = Runtime.version().feature();
        checks.add(new SetupEnvironmentCheck(
            "java",
            "Java 运行环境",
            javaFeature >= 21,
            true,
            "当前 Java " + System.getProperty("java.version", String.valueOf(javaFeature))
                + "，要求 Java 21 或更高版本。"
        ));

        ProbeResult workDirProbe = probeDirectory(installLockService.workDir());
        checks.add(new SetupEnvironmentCheck(
            "workdir-write",
            "运行目录真实写入",
            workDirProbe.passed(),
            true,
            workDirProbe.detail()
        ));

        ProbeResult tempProbe = probeDirectory(systemTempDirectory());
        checks.add(new SetupEnvironmentCheck(
            "temp-write",
            "临时目录真实写入",
            tempProbe.passed(),
            true,
            tempProbe.detail()
        ));

        checks.add(diskCheck(installLockService.workDir()));
        checks.add(memoryCheck());
        checks.add(driverCheck());

        if (managedDatabase()) {
            checks.add(new SetupEnvironmentCheck(
                "managed-database-settings",
                "平台数据库参数",
                database.hasRequiredFields(),
                true,
                database.hasRequiredFields()
                    ? "部署平台已注入数据库类型、地址、端口、数据库名和账号。"
                    : "部署平台注入的数据库参数不完整，请返回 1Panel 或 Docker 配置检查。"
            ));
        }

        checks.add(new SetupEnvironmentCheck(
            "architecture",
            "系统与处理器架构",
            true,
            false,
            System.getProperty("os.name", "未知系统") + " / "
                + System.getProperty("os.arch", "未知架构")
        ));
        return List.copyOf(checks);
    }

    /**
     * 通过创建、写入、读取和删除探针文件确认目录真的可用。
     */
    private ProbeResult probeDirectory(Path directory) {
        if (directory == null) {
            return new ProbeResult(false, "目录未配置，无法执行写入探针。");
        }

        Path probe = null;
        try {
            Files.createDirectories(directory);
            probe = Files.createTempFile(directory, ".aquafish-install-check-", ".tmp");
            Files.writeString(probe, "aquafish-environment-probe");
            String content = Files.readString(probe);
            if (!"aquafish-environment-probe".equals(content)) {
                return new ProbeResult(false, "探针文件写入后内容校验失败。");
            }
            return new ProbeResult(true, "已实际创建、写入、读取并清理探针文件。");
        } catch (Exception error) {
            return new ProbeResult(false, "真实写入探针失败：" + safeMessage(error));
        } finally {
            if (probe != null) {
                try {
                    Files.deleteIfExists(probe);
                } catch (IOException ignored) {
                    // 检测失败信息已经由上方返回；清理异常不能覆盖原始结果。
                }
            }
        }
    }

    private SetupEnvironmentCheck diskCheck(Path workDir) {
        try {
            Files.createDirectories(workDir);
            FileStore store = Files.getFileStore(workDir);
            long usable = store.getUsableSpace();
            return new SetupEnvironmentCheck(
                "disk-space",
                "可用磁盘空间",
                usable >= MIN_USABLE_DISK_BYTES,
                true,
                "当前可用 " + humanBytes(usable) + "，安装至少需要 "
                    + humanBytes(MIN_USABLE_DISK_BYTES) + "。"
            );
        } catch (Exception error) {
            return new SetupEnvironmentCheck(
                "disk-space",
                "可用磁盘空间",
                false,
                true,
                "无法读取运行目录所在磁盘：" + safeMessage(error)
            );
        }
    }

    private SetupEnvironmentCheck memoryCheck() {
        long maxHeap = Runtime.getRuntime().maxMemory();
        return new SetupEnvironmentCheck(
            "jvm-memory",
            "JVM 可用内存上限",
            maxHeap >= MIN_MAX_HEAP_BYTES,
            true,
            "当前最大堆内存 " + humanBytes(maxHeap) + "，建议不少于 "
                + humanBytes(MIN_MAX_HEAP_BYTES) + "。"
        );
    }

    /**
     * 使用 R2DBC SPI 的真实 ServiceLoader 结果确认三种发行版驱动均已打入运行时。
     */
    private SetupEnvironmentCheck driverCheck() {
        List<String> missing = new ArrayList<>();
        if (!supportsDriver("mysql")) {
            missing.add("MySQL");
        }
        if (!supportsDriver("mariadb")) {
            missing.add("MariaDB");
        }
        if (!supportsDriver("postgresql")) {
            missing.add("PostgreSQL");
        }

        boolean passed = missing.isEmpty();
        return new SetupEnvironmentCheck(
            "database-drivers",
            "数据库驱动完整性",
            passed,
            true,
            passed
                ? "已从当前运行时加载 MySQL、MariaDB、PostgreSQL 三种 R2DBC 驱动。"
                : "当前运行时缺少驱动：" + String.join("、", missing) + "。"
        );
    }

    private boolean supportsDriver(String driver) {
        try {
            ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, driver)
                .build();
            for (ConnectionFactoryProvider provider : ServiceLoader.load(ConnectionFactoryProvider.class)) {
                if (provider.supports(options)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable error) {
            return false;
        }
    }

    private Path systemTempDirectory() {
        String value = System.getProperty("java.io.tmpdir", "");
        return value.isBlank() ? null : Path.of(value).toAbsolutePath().normalize();
    }

    private String humanBytes(long bytes) {
        long mebibytes = Math.max(0L, bytes) / (1024L * 1024L);
        if (mebibytes >= 1024L) {
            return String.format("%.1f GiB", mebibytes / 1024.0d);
        }
        return mebibytes + " MiB";
    }

    private String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "未知错误";
        }
        return error.getMessage();
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record ProbeResult(boolean passed, String detail) {
    }
}
