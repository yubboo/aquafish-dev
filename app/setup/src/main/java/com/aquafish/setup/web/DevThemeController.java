package com.aquafish.setup.web;

import com.aquafish.common.web.ApiResult;
import com.aquafish.core.config.WorkDirResolver;
import com.aquafish.theme.core.ActiveThemeResolver;
import com.aquafish.theme.core.ThemeDescriptor;
import com.aquafish.theme.core.ThemeScanner;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发阶段主题诊断接口。
 *
 * 当前阶段：
 * Step 17-20-3：验证 theme 模块基础类是否可用。
 *
 * 当前作用：
 * 1. 查看 workdir/themes 实际目录；
 * 2. 扫描当前已安装主题；
 * 3. 查看当前启用主题名称；
 * 4. 判断当前启用主题是否存在；
 * 5. 为后续 ThemeTemplateResolver 做准备。
 *
 * 注意：
 * 这个接口是开发阶段诊断接口。
 * 正式上线前可以删除，或者移动到后台系统诊断页面。
 */
@RestController
@Profile("dev")
public class DevThemeController {

    /**
     * 工作目录解析器。
     */
    private final WorkDirResolver workDirResolver;

    /**
     * 主题扫描器。
     */
    private final ThemeScanner themeScanner;

    /**
     * 当前启用主题解析器。
     */
    private final ActiveThemeResolver activeThemeResolver;

    public DevThemeController(
        WorkDirResolver workDirResolver,
        ThemeScanner themeScanner,
        ActiveThemeResolver activeThemeResolver
    ) {
        this.workDirResolver = workDirResolver;
        this.themeScanner = themeScanner;
        this.activeThemeResolver = activeThemeResolver;
    }

    /**
     * 主题诊断接口。
     *
     * 访问地址：
     * GET /api/dev/theme
     *
     * @return 当前主题系统诊断信息
     */
    @GetMapping("/api/dev/theme")
    public ApiResult<ThemeDevResponse> theme() {
        List<ThemeDescriptor> themes = themeScanner.scanInstalledThemes();

        String activeThemeName = activeThemeResolver.activeThemeName();

        boolean activeThemeExists = themes
            .stream()
            .anyMatch(theme -> theme.name().equals(activeThemeName));

        ThemeDevResponse data = new ThemeDevResponse(
            workDirResolver.themesDir().toString(),
            activeThemeName,
            activeThemeExists,
            themes.size(),
            themes,
            "当前接口用于验证 workdir/themes 主题扫描和当前启用主题解析。"
        );

        return ApiResult.ok(data, "主题诊断成功");
    }

    /**
     * 主题诊断响应结构。
     *
     * @param themesDir workdir/themes 目录
     * @param activeThemeName 当前启用主题名称
     * @param activeThemeExists 当前启用主题是否存在
     * @param total 已安装主题数量
     * @param themes 已安装主题列表
     * @param note 诊断说明
     */
    public record ThemeDevResponse(
        String themesDir,
        String activeThemeName,
        boolean activeThemeExists,
        int total,
        List<ThemeDescriptor> themes,
        String note
    ) {
    }
}
