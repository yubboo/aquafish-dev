package com.aquafish.admin.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.config.AquafishProperties;
import com.aquafish.core.config.WorkDirResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 系统设置各子菜单的真实只读状态接口。
 *
 * <p>第一版先把基础、邮件、存储、安全和日志入口接到当前运行配置；不返回密码、
 * 数据库地址或服务器绝对路径。可写表单将在对应设置模型和审计落库完成后接入。</p>
 */
@RestController
@RequestMapping("/api/admin/system/status")
public class AdminSystemSettingsController {

    private static final Set<String> SECTIONS =
        Set.of("basic", "mail", "storage", "security", "logs");

    private final AquafishProperties properties;
    private final WorkDirResolver workDirResolver;
    private final Environment environment;

    public AdminSystemSettingsController(
        AquafishProperties properties,
        WorkDirResolver workDirResolver,
        Environment environment
    ) {
        this.properties = properties;
        this.workDirResolver = workDirResolver;
        this.environment = environment;
    }

    @GetMapping("/{section}")
    public Mono<ResponseEntity<ApiResult<Map<String, Object>>>> status(
        @PathVariable("section") String section
    ) {
        if (!SECTIONS.contains(section)) {
            return Mono.just(
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResult.fail(
                        "SYSTEM_SETTINGS_SECTION_NOT_FOUND",
                        "未知系统设置分区。"
                    ))
            );
        }
        return Mono.fromCallable(() -> sectionData(section))
            .subscribeOn(Schedulers.boundedElastic())
            .map(data -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResult.ok(data, "系统设置状态读取成功")))
            .onErrorResume(error -> Mono.just(
                ResponseEntity.internalServerError()
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResult.fail(
                        "SYSTEM_SETTINGS_STATUS_FAILED",
                        safeMessage(error)
                    ))
            ));
    }

    private Map<String, Object> sectionData(String section) throws IOException {
        workDirResolver.ensureBaseDirectories();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("section", section);
        data.put("editable", false);
        data.put("title", title(section));
        data.put("description", description(section));
        data.put("facts", switch (section) {
            case "basic" -> basicFacts();
            case "mail" -> mailFacts();
            case "storage" -> storageFacts();
            case "security" -> securityFacts();
            case "logs" -> logFacts();
            default -> List.of();
        });
        return data;
    }

    private List<Map<String, String>> basicFacts() {
        return List.of(
            fact("站点外部地址", properties.externalUrl()),
            fact("当前主题", properties.activeTheme()),
            fact("数据库表前缀", properties.tablePrefix())
        );
    }

    private List<Map<String, String>> mailFacts() {
        String host = environment.getProperty("spring.mail.host", "");
        return List.of(
            fact("邮件服务", host.isBlank() ? "未配置" : "已配置"),
            fact("SMTP 端口", environment.getProperty("spring.mail.port", "未配置")),
            fact(
                "TLS",
                environment.getProperty(
                    "spring.mail.properties.mail.smtp.starttls.enable",
                    "false"
                )
            )
        );
    }

    private List<Map<String, String>> storageFacts() throws IOException {
        return List.of(
            fact("运行存储目录", ready(workDirResolver.storageDir())),
            fact("上传目录", ready(workDirResolver.uploadsDir())),
            fact("缓存目录", ready(workDirResolver.cacheDir())),
            fact("上传文件数", String.valueOf(countFiles(workDirResolver.uploadsDir())))
        );
    }

    private List<Map<String, String>> securityFacts() {
        String profiles = String.join(", ", environment.getActiveProfiles());
        return List.of(
            fact("活动环境", profiles.isBlank() ? "default" : profiles),
            fact(
                "授权强制校验",
                environment.getProperty(
                    "aquafish.license.enforcement-enabled",
                    "true"
                )
            ),
            fact(
                "部署类型",
                environment.getProperty("aquafish.deployment.type", "archive")
            )
        );
    }

    private List<Map<String, String>> logFacts() throws IOException {
        Path logs = workDirResolver.logsDir();
        List<String> recent;
        try (var paths = Files.list(logs)) {
            recent = paths
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparingLong(this::lastModified).reversed())
                .limit(6)
                .map(path -> path.getFileName().toString())
                .toList();
        }
        return List.of(
            fact("日志目录", ready(logs)),
            fact("日志文件数", String.valueOf(countFiles(logs))),
            fact("最近文件", recent.isEmpty() ? "暂无日志文件" : String.join("、", recent))
        );
    }

    private Map<String, String> fact(String label, String value) {
        return Map.of(
            "label",
            label,
            "value",
            value == null || value.isBlank() ? "—" : value
        );
    }

    private String ready(Path path) {
        return Files.isDirectory(path) ? "可用" : "不可用";
    }

    private long countFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0L;
        }
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException error) {
            return 0L;
        }
    }

    private String title(String section) {
        return switch (section) {
            case "basic" -> "基础设置";
            case "mail" -> "邮件设置";
            case "storage" -> "存储设置";
            case "security" -> "安全设置";
            case "logs" -> "系统日志";
            default -> "系统设置";
        };
    }

    private String description(String section) {
        return switch (section) {
            case "basic" -> "读取当前站点地址、主题和数据库表前缀。";
            case "mail" -> "检查 SMTP 与 TLS 是否已经由运行配置提供。";
            case "storage" -> "检查实例存储、上传和缓存目录的真实状态。";
            case "security" -> "显示运行环境、授权强制校验和部署来源。";
            case "logs" -> "读取实例日志目录中的文件数量和最近文件名。";
            default -> "";
        };
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank()
            ? "系统设置状态读取失败。"
            : message;
    }
}
