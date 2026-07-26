package com.aquafish.admin.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.admin.auth.AdminAuthUser;
import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.template.core.TemplateType;
import com.aquafish.template.core.TemplateTypes;
import com.aquafish.theme.core.ActiveThemeResolver;
import com.aquafish.theme.core.DefaultThemeResolver;
import com.aquafish.theme.core.ThemeDescriptor;
import com.aquafish.theme.core.ThemeScanner;
import com.aquafish.theme.install.ThemeInstallResult;
import com.aquafish.theme.install.ThemeInstallService;
import com.aquafish.theme.lifecycle.ThemeLifecycleException;
import com.aquafish.theme.lifecycle.ThemeLifecycleResult;
import com.aquafish.theme.lifecycle.ThemeLifecycleService;
import com.aquafish.theme.lifecycle.ThemeLifecycleService.UninstalledThemeBackup;
import com.aquafish.theme.lifecycle.ThemeExportService;
import com.aquafish.theme.settings.ThemeSettingsException;
import com.aquafish.theme.settings.ThemeSettingsService;
import com.aquafish.theme.settings.ThemeSettingsService.ThemeSettingsSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 已安装主题与模板完整性诊断 API。
 *
 * <p>接口只返回可公开的主题元数据和相对模板名称，不向浏览器泄露服务器绝对路径。
 * 扫描目录与文件属于阻塞 I/O，因此统一转移到 boundedElastic。</p>
 */
@RestController
@RequestMapping("/api/admin/themes")
public class AdminThemeController {

    private final ThemeScanner themeScanner;
    private final ActiveThemeResolver activeThemeResolver;
    private final ThemeLifecycleService lifecycleService;
    private final ThemeInstallService installService;
    private final WorkDirResolver workDirResolver;
    private final ThemeSettingsService themeSettingsService;
    private final ThemeExportService themeExportService;

    public AdminThemeController(
        ThemeScanner themeScanner,
        ActiveThemeResolver activeThemeResolver,
        ThemeLifecycleService lifecycleService,
        ThemeInstallService installService,
        WorkDirResolver workDirResolver,
        ThemeSettingsService themeSettingsService,
        ThemeExportService themeExportService
    ) {
        this.themeScanner = themeScanner;
        this.activeThemeResolver = activeThemeResolver;
        this.lifecycleService = lifecycleService;
        this.installService = installService;
        this.workDirResolver = workDirResolver;
        this.themeSettingsService = themeSettingsService;
        this.themeExportService = themeExportService;
    }

    /** 上传并安装一个尚未存在的主题 ZIP。 */
    @PostMapping(
        value = "/install",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Mono<ResponseEntity<ApiResult<Map<String, Object>>>> install(
        @RequestPart("file") FilePart file,
        Authentication authentication
    ) {
        return withUploadedZip(authentication, file, packagePath ->
            installResponse(installService.install(packagePath))
        );
    }

    /** 立即启用已安装主题，并持久化到当前实例 application.yaml。 */
    @PostMapping("/{themeId}/activate")
    public Mono<ResponseEntity<ApiResult<Map<String, Object>>>> activate(
        @PathVariable("themeId") String themeId,
        Authentication authentication
    ) {
        return authorizedOperation(
            authentication,
            () -> lifecycleResponse(lifecycleService.activate(themeId))
        );
    }

    /** 上传同 ID 的新主题包并执行带回滚的安全升级。 */
    @PostMapping(
        value = "/{themeId}/upgrade",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Mono<ResponseEntity<ApiResult<Map<String, Object>>>> upgrade(
        @PathVariable("themeId") String themeId,
        @RequestPart("file") FilePart file,
        Authentication authentication
    ) {
        return withUploadedZip(authentication, file, packagePath ->
            lifecycleResponse(lifecycleService.upgrade(themeId, packagePath))
        );
    }

    /** 从运行目录安全卸载主题，服务端保留独立备份。 */
    @DeleteMapping("/{themeId}")
    public Mono<ResponseEntity<ApiResult<Map<String, Object>>>> uninstall(
        @PathVariable("themeId") String themeId,
        Authentication authentication
    ) {
        return authorizedOperation(
            authentication,
            () -> lifecycleResponse(lifecycleService.uninstall(themeId))
        );
    }

    /*
     * BEGIN：主题动态设置
     * settings.yaml 只声明表单；实例值由 ThemeSettingsService 保存到 workdir，
     * API 不允许浏览器修改主题包本身。
     */

    /** 返回一个已安装主题的设置清单和当前有效值。 */
    @GetMapping("/{themeId}/settings")
    public Mono<ResponseEntity<ApiResult<Map<String, Object>>>> settings(
        @PathVariable("themeId") String themeId,
        Authentication authentication
    ) {
        return authorized(
            authentication,
            () -> themeSettingsMap(themeSettingsService.load(themeId)),
            "主题设置读取成功"
        );
    }

    /** 校验并保存一个主题的完整实例设置。 */
    @PutMapping("/{themeId}/settings")
    public Mono<ResponseEntity<ApiResult<Map<String, Object>>>> saveSettings(
        @PathVariable("themeId") String themeId,
        @RequestBody ThemeSettingsUpdateRequest request,
        Authentication authentication
    ) {
        return authorized(
            authentication,
            () -> themeSettingsMap(themeSettingsService.save(
                themeId,
                request == null ? Map.of() : request.safeValues()
            )),
            "主题设置保存成功"
        );
    }

    /** 删除实例覆盖值，让主题恢复 settings.yaml 默认值。 */
    @DeleteMapping("/{themeId}/settings")
    public Mono<ResponseEntity<ApiResult<Map<String, Object>>>> resetSettings(
        @PathVariable("themeId") String themeId,
        Authentication authentication
    ) {
        return authorized(
            authentication,
            () -> themeSettingsMap(themeSettingsService.reset(themeId)),
            "主题设置已恢复默认值"
        );
    }

    /* END：主题动态设置 */

    /**
     * 导出一个可重新安装的标准主题 ZIP。
     *
     * <p>返回二进制文件而不是 ApiResult；失败时仍返回统一 JSON 错误。</p>
     */
    @GetMapping("/{themeId}/export")
    public Mono<ResponseEntity<Object>> export(
        @PathVariable("themeId") String themeId,
        Authentication authentication
    ) {
        return Mono.fromCallable(() -> {
                requireAdmin(authentication);
                byte[] archive = themeExportService.export(themeId);
                String safeFileName = themeId == null
                    ? "theme"
                    : themeId.replaceAll("[^a-zA-Z0-9-]", "");
                return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .header(
                        "Content-Disposition",
                        ContentDisposition.attachment()
                            .filename(
                                safeFileName + ".zip",
                                StandardCharsets.UTF_8
                            )
                            .build()
                            .toString()
                    )
                    .body((Object) archive);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(error -> operationError(error).map(response ->
                ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body((Object) response.getBody())
            ));
    }

    /** 返回当前主题和全部已安装主题的真实扫描结果。 */
    @GetMapping
    public Mono<ResponseEntity<ApiResult<Map<String, Object>>>> themes(
        Authentication authentication
    ) {
        return authorized(authentication, () -> {
            String activeName = activeThemeResolver.activeThemeName();
            List<Map<String, Object>> themes = themeScanner.scanInstalledThemes()
                .stream()
                .map(theme -> themeMap(theme, activeName))
                .toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("activeTheme", activeName);
            data.put("activeThemeInstalled", themes.stream()
                .anyMatch(item -> Boolean.TRUE.equals(item.get("active"))));
            data.put("count", themes.size());
            data.put("items", themes);
            return data;
        }, "主题扫描成功");
    }

    /** 对当前主题检查 16 个内置模板和目录状态。 */
    @GetMapping("/diagnosis")
    public Mono<ResponseEntity<ApiResult<Map<String, Object>>>> diagnosis(
        Authentication authentication
    ) {
        return authorized(authentication, () -> {
            ThemeDescriptor active = activeThemeResolver.activeTheme()
                .orElseThrow(() -> new IllegalStateException(
                    "当前主题未安装：" + activeThemeResolver.activeThemeName()
                ));
            Path templates = Path.of(active.templatesDir()).toAbsolutePath().normalize();
            List<String> expected = TemplateTypes.all().stream()
                .map(TemplateType::defaultTemplatePath)
                .toList();
            List<String> missing = expected.stream()
                .filter(relative -> !Files.isRegularFile(templates.resolve(relative).normalize()))
                .toList();
            long actualCount = countTemplates(templates);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("theme", active.name());
            data.put("title", active.title());
            data.put("engine", active.engine());
            data.put("healthy", missing.isEmpty() && active.templatesDirExists());
            data.put("settingsAvailable", active.settingsYamlExists());
            data.put("assetsAvailable", active.assetsDirExists());
            data.put("expectedTemplateCount", expected.size());
            data.put("actualTemplateCount", actualCount);
            data.put("missingTemplates", missing);
            data.put("expectedTemplates", expected);
            return data;
        }, "主题诊断完成");
    }

    private Map<String, Object> themeMap(ThemeDescriptor theme, String activeName) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", theme.name());
        item.put("title", theme.title());
        item.put("version", theme.version());
        item.put("engine", theme.engine());
        item.put("authorName", theme.authorName());
        item.put("parent", theme.parent() == null ? "" : theme.parent());
        item.put("description", theme.description());
        item.put("active", theme.name().equals(activeName));
        item.put("builtin", DefaultThemeResolver.DEFAULT_THEME_NAME.equals(theme.name()));
        /*
         * 默认主题不能卸载；当前活跃主题必须先切换到其他主题才能卸载。
         * 前端对活跃主题只展示"先用其他主题激活后再卸载"的提示，不展示卸载按钮。
         */
        item.put(
            "canUninstall",
            !DefaultThemeResolver.DEFAULT_THEME_NAME.equals(theme.name())
                && !theme.name().equals(activeName)
        );
        item.put("settingsAvailable", theme.settingsYamlExists());
        item.put("templatesAvailable", theme.templatesDirExists());
        item.put("assetsAvailable", theme.assetsDirExists());
        return item;
    }

    /**
     * 把主题设置快照转换为稳定 API 数据。
     *
     * <p>字段定义本身不包含服务器路径，ObjectMapper 可以安全序列化嵌套 record。</p>
     */
    private Map<String, Object> themeSettingsMap(
        ThemeSettingsSnapshot snapshot
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("themeId", snapshot.themeId());
        data.put("title", snapshot.title());
        data.put("available", snapshot.available());
        data.put("customized", snapshot.customized());
        data.put("fields", snapshot.fields());
        data.put("values", snapshot.values());
        return data;
    }

    private Mono<ResponseEntity<ApiResult<Map<String, Object>>>> withUploadedZip(
        Authentication authentication,
        FilePart file,
        UploadedThemeTask task
    ) {
        return Mono.usingWhen(
                createUploadFile(authentication, file),
                packagePath -> Mono.fromCallable(() -> task.run(packagePath))
                    .subscribeOn(Schedulers.boundedElastic()),
                this::deleteUploadFile
            )
            .onErrorResume(this::operationError);
    }

    private Mono<Path> createUploadFile(
        Authentication authentication,
        FilePart file
    ) {
        return Mono.fromCallable(() -> {
                requireAdmin(authentication);
                if (file == null || !file.filename().toLowerCase().endsWith(".zip")) {
                    throw new ThemeLifecycleException(
                        "THEME_PACKAGE_EXTENSION_INVALID",
                        "主题包必须是 .zip 文件。"
                    );
                }
                workDirResolver.ensureBaseDirectories();
                Path uploadDirectory = workDirResolver.tempDir()
                    .resolve("theme-uploads")
                    .toAbsolutePath()
                    .normalize();
                Files.createDirectories(uploadDirectory);
                return uploadDirectory.resolve(
                    "theme-" + UUID.randomUUID() + ".zip"
                );
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(path -> DataBufferUtils.write(
                    file.content(),
                    path,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
                )
                .thenReturn(path));
    }

    private Mono<Void> deleteUploadFile(Path path) {
        return Mono.fromRunnable(() -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时上传文件位于 workdir/storage/temp，失败不覆盖原始业务结果。
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    private Mono<ResponseEntity<ApiResult<Map<String, Object>>>> authorizedOperation(
        Authentication authentication,
        ThemeOperationTask task
    ) {
        return Mono.fromCallable(() -> {
                requireAdmin(authentication);
                return task.run();
            })
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(this::operationError);
    }

    private ResponseEntity<ApiResult<Map<String, Object>>> installResponse(
        ThemeInstallResult result
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", "install");
        data.put("success", result.success());
        data.put("themeId", result.themeId());
        data.put(
            "version",
            result.manifest() == null ? "" : result.manifest().version()
        );
        data.put("message", result.message());
        data.put(
            "warnings",
            result.validationResult() == null
                ? List.of()
                : result.validationResult().warnings().stream()
                    .map(issue -> issue.message())
                    .toList()
        );

        if (result.success()) {
            return noStore(ApiResult.ok(data, result.message()));
        }

        String code = result.errorCode() == null
            ? "THEME_INSTALL_FAILED"
            : result.errorCode().name();
        HttpStatus status = code.contains("ALREADY")
            || code.contains("BUSY")
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore())
            .body(ApiResult.fail(code, result.message()));
    }

    private ResponseEntity<ApiResult<Map<String, Object>>> lifecycleResponse(
        ThemeLifecycleResult result
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", result.action());
        data.put("themeId", result.themeId());
        data.put("version", result.version());
        data.put("active", result.active());
        data.put("message", result.message());
        return noStore(ApiResult.ok(data, result.message()));
    }

    private Mono<ResponseEntity<ApiResult<Map<String, Object>>>> operationError(
        Throwable error
    ) {
        if (message(error).contains("未登录")) {
            return Mono.just(
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResult.fail("ADMIN_AUTH_REQUIRED", message(error)))
            );
        }
        if (error instanceof ThemeLifecycleException lifecycleError) {
            HttpStatus status = lifecycleError.code().contains("NOT_FOUND")
                ? HttpStatus.NOT_FOUND
                : lifecycleError.code().contains("BUSY")
                    || lifecycleError.code().contains("IN_USE")
                    || lifecycleError.code().contains("FORBIDDEN")
                    ? HttpStatus.CONFLICT
                    : HttpStatus.BAD_REQUEST;
            return Mono.just(
                ResponseEntity.status(status)
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResult.fail(lifecycleError.code(), lifecycleError.getMessage()))
            );
        }
        if (error instanceof ThemeSettingsException settingsError) {
            HttpStatus status = settingsError.code().contains("NOT_FOUND")
                ? HttpStatus.NOT_FOUND
                : settingsError.code().contains("SAVE_FAILED")
                    || settingsError.code().contains("RESET_FAILED")
                    ? HttpStatus.INTERNAL_SERVER_ERROR
                    : HttpStatus.BAD_REQUEST;
            return Mono.just(
                ResponseEntity.status(status)
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResult.fail(
                        settingsError.code(),
                        settingsError.getMessage()
                    ))
            );
        }
        return Mono.just(
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .cacheControl(CacheControl.noStore())
                .body(ApiResult.fail("THEME_MANAGEMENT_FAILED", message(error)))
        );
    }

    private ResponseEntity<ApiResult<Map<String, Object>>> noStore(
        ApiResult<Map<String, Object>> body
    ) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(body);
    }

    private long countTemplates(Path templates) {
        if (!Files.isDirectory(templates)) {
            return 0L;
        }
        try (var files = Files.walk(templates)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    return name.endsWith(".html") || name.endsWith(".peb");
                })
                .count();
        } catch (IOException error) {
            throw new IllegalStateException("读取当前主题模板目录失败。", error);
        }
    }

    private Mono<ResponseEntity<ApiResult<Map<String, Object>>>> authorized(
        Authentication authentication,
        ThemeTask task,
        String successMessage
    ) {
        return Mono.fromCallable(() -> {
                requireAdmin(authentication);
                return task.run();
            })
            .subscribeOn(Schedulers.boundedElastic())
            .map(data -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResult.ok(data, successMessage)))
            .onErrorResume(this::operationError);
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null
            || !(authentication.getPrincipal() instanceof AdminAuthUser user)
            || !user.hasAdminAccess()) {
            throw new IllegalStateException("未登录或没有主题管理权限。");
        }
    }

    private String message(Throwable error) {
        String value = error == null ? null : error.getMessage();
        return value == null || value.isBlank() ? "主题管理请求失败。" : value;
    }

    private interface ThemeTask {
        Map<String, Object> run();
    }

    private interface ThemeOperationTask {
        ResponseEntity<ApiResult<Map<String, Object>>> run();
    }

    private interface UploadedThemeTask {
        ResponseEntity<ApiResult<Map<String, Object>>> run(Path packagePath);
    }

    /**
     * 后台保存主题设置的请求体。
     */
    public record ThemeSettingsUpdateRequest(Map<String, Object> values) {

        Map<String, Object> safeValues() {
            return values == null ? Map.of() : values;
        }
    }
}
