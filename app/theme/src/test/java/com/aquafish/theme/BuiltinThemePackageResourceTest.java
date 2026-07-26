package com.aquafish.theme;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Aquafish 内置 default 主题包构建验证。
 *
 * <p>
 * 本测试只检查随 theme 模块发布的只读 ZIP，不读取开发者实例目录，也不会向
 * workdir/themes 写入文件。安装器后续可以复用同一资源完成首次安装或恢复。
 * </p>
 */
class BuiltinThemePackageResourceTest {

    /**
     * 验证内置主题 ZIP 已进入 classpath，并包含主题清单与关键模板。
     *
     * @throws Exception 当资源无法读取或 ZIP 损坏时抛出
     */
    @Test
    void shouldPackageBuiltinDefaultThemeIntoClasspath() throws Exception {
        Set<String> entries = loadBuiltinThemeEntries().keySet();

        assertTrue(entries.contains("theme.yaml"), "主题 ZIP 必须包含 theme.yaml。");
        assertTrue(entries.contains("settings.yaml"), "主题 ZIP 必须包含 settings.yaml。");
        assertTrue(
            entries.contains("templates/index.html"),
            "主题 ZIP 必须包含首页模板。"
        );
        assertNotNull(
            entries.stream()
                .filter(name -> name.startsWith("templates/forum/"))
                .findFirst()
                .orElse(null),
            "主题 ZIP 必须包含论坛模板。"
        );
        assertTrue(
            entries.contains(
                "assets/images/backgrounds/home/aquafish-portal-hero.jpg"
            ),
            "首页背景必须放入 backgrounds/home。"
        );
        assertTrue(
            entries.contains(
                "assets/images/carousel/home/aquafish-story-01.jpg"
            ),
            "首页内容图必须放入 carousel/home。"
        );
        assertTrue(
            entries.contains(
                "assets/images/login/aquafish-keeper-v1.webp"
            ),
            "登录插图必须放入 login。"
        );
        assertFalse(
            entries.contains("assets/images/aquafish-portal-hero.jpg"),
            "images 根目录不得继续散放首页背景。"
        );
    }

    /**
     * 登录和注册主题必须使用后端返回的 CSRF 请求头名称。
     *
     * <p>该断言专门防止主题再次写死 Spring 默认的 X-XSRF-TOKEN，
     * 导致 Aquafish 自定义安全头 X-AQUAFISH-CSRF 无法通过校验。</p>
     *
     * @throws Exception 当内置主题 ZIP 无法读取时抛出
     */
    @Test
    void memberFormsShouldUseCsrfHeaderNameReturnedByBackend() throws Exception {
        Map<String, byte[]> entries = loadBuiltinThemeEntries();

        for (String template : Set.of(
            "templates/member/login.html",
            "templates/member/register.html"
        )) {
            assertTrue(entries.containsKey(template), "缺少会员模板：" + template);
            String html = new String(
                entries.get(template),
                StandardCharsets.UTF_8
            );

            assertTrue(
                html.contains("partial/member-auth :: auth"),
                template + " 必须复用统一会员认证组件。"
            );
            assertFalse(
                html.contains("'X-XSRF-TOKEN'"),
                template + " 不得写死框架默认 CSRF 请求头。"
            );
        }

        String script = new String(
            entries.get("assets/js/member-auth.js"),
            StandardCharsets.UTF_8
        );
        assertTrue(
            script.contains("[csrf.headerName]: csrf.token"),
            "统一认证脚本必须使用后端返回的 CSRF 请求头名称。"
        );
    }

    /**
     * 表单值必须在禁用输入框之前保存。
     *
     * <p>{@code FormData} 不包含 disabled 控件；如果先调用 {@code setBusy(true)}，
     * 登录名、密码和注册资料都会被错误发送为空字符串。</p>
     */
    @Test
    void memberFormsShouldCaptureValuesBeforeDisablingInputs() throws Exception {
        Map<String, byte[]> entries = loadBuiltinThemeEntries();
        String script = new String(
            entries.get("assets/js/member-auth.js"),
            StandardCharsets.UTF_8
        );

        int captureIndex = script.indexOf("const values = new FormData(form)");
        int disableIndex = script.indexOf("setBusy(true)");

        assertTrue(captureIndex >= 0, "认证脚本必须在提交时保存 FormData 快照。");
        assertTrue(disableIndex >= 0, "认证脚本必须保留防重复提交状态。");
        assertTrue(
            captureIndex < disableIndex,
            "必须先保存表单值，再禁用输入框。"
        );
        assertTrue(
            script.contains("submitLogin(values)"),
            "登录请求必须使用禁用控件前保存的表单值。"
        );
        assertTrue(
            script.contains("submitRegister(values)"),
            "注册请求必须使用禁用控件前保存的表单值。"
        );
    }

    /**
     * 公共头部必须循环渲染服务端导航，不能向匿名用户硬编码后台入口。
     */
    @Test
    void headerShouldRenderDatabaseNavigationInsteadOfHardcodedAdminLink()
        throws Exception {
        Map<String, byte[]> entries = loadBuiltinThemeEntries();
        String template = "templates/partial/header.html";
        assertTrue(entries.containsKey(template), "缺少公共头部模板。");
        String html = new String(entries.get(template), StandardCharsets.UTF_8);

        assertTrue(
            html.contains("th:each=\"item : ${navigation.primary}\""),
            "主导航必须使用 Thymeleaf 循环。"
        );
        assertTrue(
            html.contains("th:each=\"item : ${navigation.account}\""),
            "账号导航必须使用 Thymeleaf 循环。"
        );
        assertFalse(
            html.contains("<a class=\"af-header-admin\" href=\"/admin\">"),
            "主题不得硬编码始终可见的管理后台入口。"
        );
    }

    /**
     * 首页、论坛空状态和页脚也必须遵循公共身份模型。
     *
     * <p>只修复头部仍不够：匿名用户如果能在页脚或首页功能卡看到后台入口，
     * 仍然会造成错误的权限暗示。该测试防止后续主题调整再次引入常驻入口。</p>
     */
    @Test
    void publicTemplatesShouldNotExposeAdminEntryToAnonymousViewer()
        throws Exception {
        Map<String, byte[]> entries = loadBuiltinThemeEntries();

        String footer = new String(
            entries.get("templates/partial/footer.html"),
            StandardCharsets.UTF_8
        );
        assertTrue(
            footer.contains("th:each=\"item : ${navigation.primary}\""),
            "页脚主导航必须循环数据库导航模型。"
        );
        assertTrue(
            footer.contains("th:each=\"item : ${navigation.account}\""),
            "页脚账号导航必须循环数据库导航模型。"
        );
        assertFalse(
            footer.contains("href=\"/admin\""),
            "页脚不得硬编码管理后台入口。"
        );

        for (String template : Set.of(
            "templates/index.html",
            "templates/forum/index.html"
        )) {
            String html = new String(
                entries.get(template),
                StandardCharsets.UTF_8
            );
            assertTrue(
                html.contains("th:if=\"${viewer != null && viewer.admin}\""),
                template + " 的管理入口必须由管理员身份条件保护。"
            );
        }
    }

    /**
     * 已登录用户必须获得退出入口，首页注册操作则只能向匿名用户展示。
     */
    @Test
    void authenticatedThemeShouldExposeLogoutAndHideRegistrationActions()
        throws Exception {
        Map<String, byte[]> entries = loadBuiltinThemeEntries();

        assertTrue(
            entries.containsKey("assets/js/member-session.js"),
            "内置主题必须打包全站会员退出脚本。"
        );

        for (String template : Set.of(
            "templates/partial/header.html",
            "templates/partial/footer.html"
        )) {
            String html = new String(entries.get(template), StandardCharsets.UTF_8);
            assertTrue(
                html.contains("data-member-logout"),
                template + " 必须提供退出登录按钮。"
            );
            assertTrue(
                html.contains("viewer != null && viewer.authenticated"),
                template + " 的退出按钮只能向已登录用户展示。"
            );
        }

        String home = new String(
            entries.get("templates/index.html"),
            StandardCharsets.UTF_8
        );
        assertTrue(
            home.contains("viewer == null || !viewer.authenticated"),
            "首页注册卡片和按钮必须由匿名状态保护。"
        );

        String layout = new String(
            entries.get("templates/layout/main.html"),
            StandardCharsets.UTF_8
        );
        assertTrue(
            layout.contains("/theme-assets/js/member-session.js"),
            "公共布局必须加载退出脚本。"
        );
    }

    /**
     * 官方主题的设置清单必须真实控制模板，而不是后台只展示一张空表单。
     */
    @Test
    void defaultThemeSettingsShouldBeConsumedByThemeTemplates()
        throws Exception {
        Map<String, byte[]> entries = loadBuiltinThemeEntries();
        String settings = new String(
            entries.get("settings.yaml"),
            StandardCharsets.UTF_8
        );
        assertTrue(settings.contains("primaryColor:"));
        assertTrue(settings.contains("heroImage:"));
        assertTrue(settings.contains("loginImage:"));
        assertTrue(settings.contains("showHomeSearch:"));

        String layout = new String(
            entries.get("templates/layout/main.html"),
            StandardCharsets.UTF_8
        );
        assertTrue(
            layout.contains("theme.settings.primaryColor"),
            "全局布局必须消费主题主色。"
        );
        assertTrue(
            layout.contains("theme.settings.layout"),
            "全局布局必须消费布局风格。"
        );

        String home = new String(
            entries.get("templates/index.html"),
            StandardCharsets.UTF_8
        );
        assertTrue(home.contains("theme.settings.heroImage"));
        assertTrue(home.contains("theme.settings.showHomeSearch"));
        assertTrue(home.contains("theme.settings.storyImage4"));

        String authPartial = new String(
            entries.get("templates/partial/member-auth.html"),
            StandardCharsets.UTF_8
        );
        assertTrue(authPartial.contains("theme.settings.loginImage"));
    }

    /**
     * 一次性读取内置主题 ZIP 的全部普通文件。
     *
     * @return ZIP 内规范化路径到文件字节的只读快照
     * @throws Exception 当资源不存在或 ZIP 损坏时抛出
     */
    private Map<String, byte[]> loadBuiltinThemeEntries() throws Exception {
        ClassPathResource resource = new ClassPathResource(
            "aquafish/builtin-themes/aquafish-default.zip"
        );
        assertTrue(resource.exists(), "内置 default 主题 ZIP 必须进入 theme 模块资源。");

        Map<String, byte[]> entries = new HashMap<>();
        try (
            InputStream input = resource.getInputStream();
            ZipInputStream zip = new ZipInputStream(input)
        ) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory()) {
                    entries.put(name, new byte[0]);
                    continue;
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                zip.transferTo(output);
                entries.put(name, output.toByteArray());
            }
        }
        return Map.copyOf(entries);
    }
}
