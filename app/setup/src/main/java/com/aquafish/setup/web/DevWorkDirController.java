package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import java.nio.file.Files;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发阶段 workdir 诊断接口。
 *
 * 当前阶段：
 * Step 17-19-2：验证 AquafishProperties / WorkDirResolver 是否正常。
 *
 * 当前作用：
 * 1. 查看 aquafish.work-dir 是否读取成功。
 * 2. 查看 workdir 最终解析路径是否正确。
 * 3. 查看 storage / themes / plugins / licenses 等目录是否存在。
 *
 * 注意：
 * 这个接口是开发阶段调试接口。
 * 正式发布前可以删除，或者移入后台系统诊断页面。
 */
@RestController
@Profile("dev")
public class DevWorkDirController {

    private final AquafishProperties properties;
    private final WorkDirResolver workDirResolver;

    public DevWorkDirController(
        AquafishProperties properties,
        WorkDirResolver workDirResolver
    ) {
        this.properties = properties;
        this.workDirResolver = workDirResolver;
    }

    /**
     * workdir 诊断接口。
     *
     * 访问地址：
     * GET /api/dev/workdir
     *
     * @return 当前 workdir 相关配置和目录状态。
     */
    @GetMapping("/api/dev/workdir")
    public ApiResult<WorkDirResponse> workdir() {
        /*
         * 确保基础目录存在。
         *
         * 这样即使用户忘记手动创建某个目录，访问诊断接口时也能自动补齐。
         */
        workDirResolver.ensureBaseDirectories();

        WorkDirResponse data = new WorkDirResponse(
            properties.workDir(),
            properties.externalUrl(),
            properties.tablePrefix(),
            properties.activeTheme(),

            workDirResolver.workDir().toString(),
            Files.exists(workDirResolver.workDir()),

            workDirResolver.applicationYamlFile().toString(),
            Files.exists(workDirResolver.applicationYamlFile()),

            workDirResolver.storageDir().toString(),
            Files.exists(workDirResolver.storageDir()),

            workDirResolver.cacheDir().toString(),
            Files.exists(workDirResolver.cacheDir()),

            workDirResolver.logsDir().toString(),
            Files.exists(workDirResolver.logsDir()),

            workDirResolver.uploadsDir().toString(),
            Files.exists(workDirResolver.uploadsDir()),

            workDirResolver.tempDir().toString(),
            Files.exists(workDirResolver.tempDir()),

            workDirResolver.themesDir().toString(),
            Files.exists(workDirResolver.themesDir()),

            workDirResolver.pluginsDir().toString(),
            Files.exists(workDirResolver.pluginsDir()),

            workDirResolver.licensesDir().toString(),
            Files.exists(workDirResolver.licensesDir()),

            workDirResolver.backupsDir().toString(),
            Files.exists(workDirResolver.backupsDir()),

            "当前接口用于验证 Halo 式 workdir/application.yaml 外置配置是否接入成功。"
        );

        return ApiResult.ok(data, "workdir 诊断成功");
    }

    /**
     * workdir 诊断响应结构。
     */
    public record WorkDirResponse(
        String configuredWorkDir,
        String externalUrl,
        String tablePrefix,
        String activeTheme,

        String resolvedWorkDir,
        boolean workDirExists,

        String applicationYamlFile,
        boolean applicationYamlExists,

        String storageDir,
        boolean storageDirExists,

        String cacheDir,
        boolean cacheDirExists,

        String logsDir,
        boolean logsDirExists,

        String uploadsDir,
        boolean uploadsDirExists,

        String tempDir,
        boolean tempDirExists,

        String themesDir,
        boolean themesDirExists,

        String pluginsDir,
        boolean pluginsDirExists,

        String licensesDir,
        boolean licensesDirExists,

        String backupsDir,
        boolean backupsDirExists,

        String note
    ) {
    }
}
