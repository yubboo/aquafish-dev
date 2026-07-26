package com.aquafish.admin.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.permalink.PermalinkBuildRequest;
import com.aquafish.core.permalink.PermalinkBuilder;
import com.aquafish.core.permalink.PermalinkPreview;
import com.aquafish.core.permalink.PermalinkSettings;
import com.aquafish.core.permalink.PermalinkSettingsService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台固定链接设置接口。
 *
 * 当前阶段：
 * Step 17-21-5：接入固定链接生成器 PermalinkBuilder。
 *
 * 所属模块：
 * app/admin
 *
 * 接口：
 * GET  /api/admin/settings/permalink
 * PUT  /api/admin/settings/permalink
 * POST /api/admin/settings/permalink/preview
 * GET  /api/admin/settings/permalink/build-demo
 */
@RestController
public class AdminPermalinkSettingsController {

    private final PermalinkSettingsService permalinkSettingsService;

    private final PermalinkBuilder permalinkBuilder;

    public AdminPermalinkSettingsController(
        PermalinkSettingsService permalinkSettingsService,
        PermalinkBuilder permalinkBuilder
    ) {
        this.permalinkSettingsService = permalinkSettingsService;
        this.permalinkBuilder = permalinkBuilder;
    }

    /**
     * 获取当前固定链接设置。
     */
    @GetMapping("/api/admin/settings/permalink")
    public ApiResult<PermalinkSettingsResponse> getSettings() {
        PermalinkSettings settings = permalinkSettingsService.getSettings();
        PermalinkPreview preview = permalinkSettingsService.preview(settings);

        return ApiResult.ok(
            new PermalinkSettingsResponse(
                settings,
                preview,
                permalinkSettingsService.settingsFilePath()
            ),
            "固定链接设置获取成功"
        );
    }

    /**
     * 保存固定链接设置。
     */
    @PutMapping("/api/admin/settings/permalink")
    public ApiResult<PermalinkSettingsResponse> saveSettings(
        @RequestBody PermalinkSettings request
    ) {
        PermalinkSettings settings = permalinkSettingsService.saveSettings(request);
        PermalinkPreview preview = permalinkSettingsService.preview(settings);

        return ApiResult.ok(
            new PermalinkSettingsResponse(
                settings,
                preview,
                permalinkSettingsService.settingsFilePath()
            ),
            "固定链接设置保存成功"
        );
    }

    /**
     * 预览固定链接设置。
     *
     * 注意：
     * 这里只预览，不保存。
     */
    @PostMapping("/api/admin/settings/permalink/preview")
    public ApiResult<PermalinkPreview> preview(
        @RequestBody PermalinkSettings request
    ) {
        return ApiResult.ok(
            permalinkSettingsService.preview(request),
            "固定链接预览生成成功"
        );
    }

    /**
     * 固定链接生成器开发诊断。
     *
     * 作用：
     * 1. 验证 PermalinkBuilder 是否被 Spring 正常加载；
     * 2. 验证当前配置是否真的参与链接生成；
     * 3. 验证 article/page/category/tag/forum/thread/user 七类链接是否能正常生成。
     */
    @GetMapping("/api/admin/settings/permalink/build-demo")
    public ApiResult<PermalinkBuildDemoResponse> buildDemo() {
        PermalinkSettings settings = permalinkSettingsService.getSettings();

        return ApiResult.ok(
            new PermalinkBuildDemoResponse(
                settings,
                permalinkBuilder.buildDemoLinks()
            ),
            "固定链接生成器诊断成功"
        );
    }

    /**
     * 使用当前配置生成单个固定链接。
     *
     * 这个接口当前先作为开发诊断使用。
     * 后续真实业务里，content/forum/user 模块会直接调用 PermalinkBuilder，
     * 不一定需要暴露这个接口给前端。
     */
    @PostMapping("/api/admin/settings/permalink/build")
    public ApiResult<String> build(
        @RequestBody PermalinkBuildRequest request
    ) {
        return ApiResult.ok(
            permalinkBuilder.build(request),
            "固定链接生成成功"
        );
    }

    /**
     * 固定链接设置响应结构。
     *
     * @param settings 当前设置
     * @param preview 当前预览
     * @param storagePath 当前保存路径
     */
    public record PermalinkSettingsResponse(
        PermalinkSettings settings,
        PermalinkPreview preview,
        String storagePath
    ) {
    }

    /**
     * 固定链接生成器诊断响应。
     *
     * @param settings 当前固定链接设置
     * @param links 演示链接
     */
    public record PermalinkBuildDemoResponse(
        PermalinkSettings settings,
        Map<String, String> links
    ) {
    }
}
