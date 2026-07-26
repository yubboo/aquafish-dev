package com.aquafish.user.web;

import com.aquafish.core.database.DatabaseRuntimeSettingsService;
import com.aquafish.core.database.DatabaseSettings;
import com.aquafish.core.database.TableNameResolver;
import com.aquafish.core.database.TableNames;
import com.aquafish.user.auth.MemberAuthService;
import com.aquafish.user.auth.MemberAuthUser;
import com.aquafish.user.security.MemberSessionTokenResolver;
import com.aquafish.theme.settings.ThemeSettingsService;
import com.aquafish.theme.settings.ThemeSettingsService.ThemeSettingsSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 前台主题的公共站点、导航和登录主体模型装配服务。
 *
 * <p>导航的事实来源是 {@code options.site.navigation}，主题只使用
 * {@code th:each} 循环渲染，不写死菜单。会员身份从 HttpOnly 会话 Cookie 解析并再次
 * 查询数据库；匿名、会员和管理员入口在服务端过滤，浏览器隐藏按钮不参与权限判断。</p>
 */
@Service
public class PublicTemplateContextService {

    private static final Set<String> OPTION_KEYS = Set.of(
        "site.name",
        "site.description",
        "site.url",
        "site.locale",
        "site.navigation"
    );

    /**
     * 旧实例尚未执行导航迁移或配置损坏时使用的安全回退。
     *
     * <p>正常安装和升级后，数据库中的 site.navigation 会覆盖此值。</p>
     */
    private static final String DEFAULT_NAVIGATION_JSON = """
        [
          {"key":"home","label":"首页","url":"/site","location":"primary","target":"_self","visibility":"PUBLIC","enabled":true,"sortOrder":10},
          {"key":"content","label":"内容","url":"/content","location":"primary","target":"_self","visibility":"PUBLIC","enabled":true,"sortOrder":20},
          {"key":"forum","label":"论坛","url":"/forum","location":"primary","target":"_self","visibility":"PUBLIC","enabled":true,"sortOrder":30},
          {"key":"login","label":"登录","url":"/login","location":"account","target":"_self","visibility":"ANONYMOUS","enabled":true,"sortOrder":10},
          {"key":"register","label":"注册","url":"/register","location":"account","target":"_self","visibility":"ANONYMOUS","enabled":true,"sortOrder":20},
          {"key":"member","label":"个人中心","url":"/member","location":"account","target":"_self","visibility":"AUTHENTICATED","enabled":true,"sortOrder":30},
          {"key":"admin","label":"管理后台","url":"/admin","location":"account","target":"_self","visibility":"ADMIN","enabled":true,"sortOrder":40}
        ]
        """;

    private final DatabaseRuntimeSettingsService settingsService;
    private final DatabaseClient databaseClient;
    private final MemberAuthService authService;
    private final ThemeSettingsService themeSettingsService;
    private final ObjectMapper objectMapper;

    public PublicTemplateContextService(
        DatabaseRuntimeSettingsService settingsService,
        DatabaseClient databaseClient,
        MemberAuthService authService,
        ThemeSettingsService themeSettingsService
    ) {
        this.settingsService = settingsService;
        this.databaseClient = databaseClient;
        this.authService = authService;
        this.themeSettingsService = themeSettingsService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 构建主题页面公共模型。
     *
     * @param exchange 当前请求，用于解析可信会话 Cookie
     * @param title 当前页面标题
     * @param description 当前页面摘要
     * @return 包含 site、seo、viewer 和 navigation 的不可泄密模型
     */
    public Mono<Map<String, Object>> create(
        ServerWebExchange exchange,
        String title,
        String description
    ) {
        /*
         * 主题扫描和实例 JSON 属于阻塞文件 I/O，必须离开 Netty 事件线程。
         * 三个事实源在合并前彼此独立：数据库站点选项、会员会话、主题设置。
         */
        Mono<ThemeSettingsSnapshot> themeSettings = Mono.fromCallable(
                themeSettingsService::loadActiveSafely
            )
            .subscribeOn(Schedulers.boundedElastic());
        return Mono.zip(loadOptions(), resolveViewer(exchange), themeSettings)
            .map(values -> createModel(
                values.getT1(),
                values.getT2(),
                values.getT3(),
                safeText(title, "Aquafish"),
                safeText(description, "Aquafish 内容社区")
            ));
    }

    private Mono<Map<String, String>> loadOptions() {
        DatabaseSettings settings = settingsService.current().normalized();
        String table = TableNameResolver.tableName(
            settings.tablePrefix(),
            TableNames.OPTIONS
        );
        String sql = "select option_key, coalesce(option_value, '') as option_value "
            + "from " + table + " where option_key in "
            + "('site.name','site.description','site.url','site.locale','site.navigation')";

        return databaseClient.sql(sql)
            .map((row, metadata) -> Map.entry(
                safeText(row.get("option_key", String.class), ""),
                safeText(row.get("option_value", String.class), "")
            ))
            .all()
            .filter(entry -> OPTION_KEYS.contains(entry.getKey()))
            .collectMap(Map.Entry::getKey, Map.Entry::getValue, LinkedHashMap::new);
    }

    private Mono<Optional<MemberAuthUser>> resolveViewer(
        ServerWebExchange exchange
    ) {
        String token = exchange == null
            ? null
            : MemberSessionTokenResolver.resolve(exchange.getRequest());
        if (token == null || token.isBlank()) {
            return Mono.just(Optional.empty());
        }

        /*
         * 公共页面遇到已撤销或过期 Cookie 时按匿名显示；写 API 仍由 Spring Security
         * 返回 401。这里不能让一个失效 Cookie 把首页替换成 JSON 错误。
         */
        return authService.authenticate(token)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty())
            .onErrorReturn(Optional.empty());
    }

    private Map<String, Object> createModel(
        Map<String, String> options,
        Optional<MemberAuthUser> viewer,
        ThemeSettingsSnapshot themeSettings,
        String title,
        String description
    ) {
        Map<String, Object> model = new LinkedHashMap<>();
        Map<String, Object> site = new LinkedHashMap<>();
        site.put("name", option(options, "site.name", "Aquafish"));
        site.put(
            "description",
            option(options, "site.description", "CMS + 强论坛 + AI 内容社区")
        );
        site.put("url", option(options, "site.url", "/"));
        site.put("locale", option(options, "site.locale", "zh-CN"));
        model.put("site", site);
        model.put("seo", Map.of(
            "title", title,
            "description", description
        ));
        model.put("viewer", viewerModel(viewer));
        model.put(
            "navigation",
            navigationModel(options.get("site.navigation"), viewer)
        );
        /*
         * 主题模板只使用这个安全模型，不读取 settings.yaml，也不知道 workdir
         * 的绝对路径。设置不存在或损坏时 loadActiveSafely 返回空 Map。
         */
        model.put("theme", Map.of(
            "id", themeSettings.themeId(),
            "title", themeSettings.title(),
            "settings", themeSettings.values()
        ));
        return model;
    }

    private Map<String, Object> viewerModel(
        Optional<MemberAuthUser> viewer
    ) {
        if (viewer.isEmpty()) {
            return Map.of(
                "authenticated", false,
                "admin", false
            );
        }
        MemberAuthUser user = viewer.orElseThrow();
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("authenticated", true);
        model.put("admin", user.hasAdminAccess());
        model.put("uid", user.uid());
        model.put("publicId", user.publicId());
        model.put("username", user.username());
        model.put("displayName", user.displayName());
        model.put("avatar", user.avatar());
        return model;
    }

    private Map<String, Object> navigationModel(
        String source,
        Optional<MemberAuthUser> viewer
    ) {
        List<NavigationItem> items = parseNavigation(source);
        List<Map<String, Object>> primary = new ArrayList<>();
        List<Map<String, Object>> account = new ArrayList<>();

        items.stream()
            .filter(NavigationItem::enabled)
            .filter(item -> item.visibleFor(viewer))
            .sorted(Comparator
                .comparingInt(NavigationItem::sortOrder)
                .thenComparing(NavigationItem::key))
            .forEach(item -> {
                Map<String, Object> view = item.toModel();
                if ("account".equals(item.location())) {
                    account.add(view);
                } else {
                    primary.add(view);
                }
            });

        return Map.of(
            "primary", List.copyOf(primary),
            "account", List.copyOf(account)
        );
    }

    private List<NavigationItem> parseNavigation(String source) {
        String json = source == null || source.isBlank()
            ? DEFAULT_NAVIGATION_JSON
            : source;
        try {
            List<NavigationItem> items = objectMapper.readValue(
                json,
                new TypeReference<List<NavigationItem>>() {
                }
            );
            List<NavigationItem> normalized = items.stream()
                .map(NavigationItem::normalized)
                .filter(NavigationItem::valid)
                .toList();
            return normalized.isEmpty()
                ? parseFallbackNavigation()
                : normalized;
        } catch (Exception ignored) {
            return parseFallbackNavigation();
        }
    }

    private List<NavigationItem> parseFallbackNavigation() {
        try {
            return objectMapper.readValue(
                DEFAULT_NAVIGATION_JSON,
                new TypeReference<List<NavigationItem>>() {
                }
            ).stream().map(NavigationItem::normalized).toList();
        } catch (Exception impossible) {
            throw new IllegalStateException("内置导航配置无法解析。", impossible);
        }
    }

    private String option(
        Map<String, String> options,
        String key,
        String fallback
    ) {
        return safeText(options == null ? null : options.get(key), fallback);
    }

    private static String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /**
     * 数据库导航配置项；只向模板导出经过校验的字段。
     */
    private record NavigationItem(
        String key,
        String label,
        String url,
        String location,
        String target,
        String visibility,
        boolean enabled,
        int sortOrder
    ) {

        NavigationItem normalized() {
            String safeTarget = "_blank".equals(target) ? "_blank" : "_self";
            return new NavigationItem(
                safe(key),
                safe(label),
                safe(url),
                "account".equalsIgnoreCase(safe(location))
                    ? "account"
                    : "primary",
                safeTarget,
                safe(visibility).toUpperCase(Locale.ROOT),
                enabled,
                sortOrder
            );
        }

        boolean valid() {
            return !key.isBlank()
                && !label.isBlank()
                && url.startsWith("/")
                && !url.startsWith("//");
        }

        boolean visibleFor(Optional<MemberAuthUser> viewer) {
            boolean authenticated = viewer != null && viewer.isPresent();
            return switch (visibility) {
                case "ANONYMOUS" -> !authenticated;
                case "AUTHENTICATED" -> authenticated;
                case "ADMIN" -> authenticated
                    && viewer.orElseThrow().hasAdminAccess();
                default -> true;
            };
        }

        Map<String, Object> toModel() {
            return Map.of(
                "key", key,
                "label", label,
                "url", url,
                "target", target
            );
        }

        private static String safe(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
