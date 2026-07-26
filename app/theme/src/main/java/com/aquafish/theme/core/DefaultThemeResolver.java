package com.aquafish.theme.core;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Aquafish 外置官方 default 主题解析器。
 *
 * <p>
 * 本组件负责在当前已经安装的主题中，
 * 查找主题唯一标识固定为 {@code default} 的主题。
 * </p>
 *
 * <p>外置官方 default 主题位于：</p>
 *
 * <pre>
 * workdir/themes/default
 * ├─ theme.yaml
 * ├─ settings.yaml
 * ├─ templates
 * └─ assets
 * </pre>
 *
 * <p>
 * default 主题不是当前活动主题的父主题，
 * 而是主题模板回退链中的独立安全层。
 * </p>
 *
 * <p>完整目标回退顺序为：</p>
 *
 * <pre>
 * 当前启用主题
 * -> 当前主题的父主题继承链
 * -> 外置官方 default 主题
 * -> 核心内置只读 fallback
 * -> 最小紧急静态页面
 * </pre>
 *
 * <p>
 * 父主题继承与 default 回退存在重要区别：
 * </p>
 *
 * <ul>
 *     <li>
 *         父主题和子主题必须使用相同模板引擎；
 *     </li>
 *     <li>
 *         default 主题可以使用与当前主题不同的模板引擎；
 *     </li>
 *     <li>
 *         default 回退不是模板继承，不合并布局和主题设置；
 *     </li>
 *     <li>
 *         当使用 default 模板时，
 *         应由 default 自己声明的模板引擎完成完整页面渲染。
 *     </li>
 * </ul>
 *
 * <p>例如：</p>
 *
 * <pre>
 * 当前主题：community-pebble
 * 当前引擎：pebble
 * 当前主题缺少 content/view.html
 *
 * 外置 default：
 * engine: thymeleaf
 * 存在 content/view.html
 *
 * 最终由 Thymeleaf 引擎渲染 default 模板。
 * </pre>
 *
 * <p>
 * 这是安全回退，不是把 Pebble 模板转换成 Thymeleaf，
 * 也不是让一个主题混用两种模板语法。
 * </p>
 *
 * <p>
 * 本组件当前只负责按照主题唯一标识寻找 default，
 * 不负责验证该主题包是否由 Aquafish 官方签名。
 * 官方包签名、完整性校验和防篡改规则，
 * 后续由主题安装器和应用中心安全模块负责。
 * </p>
 *
 * <p>本类不负责：</p>
 *
 * <ul>
 *     <li>不负责启用或切换主题；</li>
 *     <li>不负责父主题继承；</li>
 *     <li>不负责查找具体模板文件；</li>
 *     <li>不负责渲染 Thymeleaf 或 Pebble；</li>
 *     <li>不负责核心内置 fallback；</li>
 *     <li>不负责紧急静态页面。</li>
 * </ul>
 */
@Component
public class DefaultThemeResolver {

    /**
     * Aquafish 外置官方默认主题的固定唯一标识。
     *
     * <p>
     * 该值属于平台协议的一部分，
     * 不随后台当前启用主题配置变化。
     * </p>
     *
     * <p>
     * 即使管理员把当前主题切换成其他主题，
     * 回退层仍然会查找名为 default 的官方主题。
     * </p>
     */
    public static final String DEFAULT_THEME_NAME =
        "default";

    /**
     * 已安装主题扫描器。
     *
     * <p>
     * 用于扫描当前 workdir/themes 下
     * 所有具备 theme.yaml 的已安装主题。
     * </p>
     */
    private final ThemeScanner themeScanner;

    /**
     * 创建外置官方 default 主题解析器。
     *
     * @param themeScanner 已安装主题扫描器，
     *                     不允许为 null
     * @throws IllegalArgumentException 当主题扫描器为空时抛出
     */
    public DefaultThemeResolver(
        ThemeScanner themeScanner
    ) {
        if (themeScanner == null) {
            throw new IllegalArgumentException(
                "主题扫描器不能为空。"
            );
        }

        this.themeScanner = themeScanner;
    }

    /**
     * 返回外置官方 default 主题的固定名称。
     *
     * <p>
     * 该方法可供模板诊断、后台主题状态和测试代码复用，
     * 避免在多个位置重复写死字符串。
     * </p>
     *
     * @return 固定返回 default
     */
    public String defaultThemeName() {
        return DEFAULT_THEME_NAME;
    }

    /**
     * 在当前已安装主题中查找外置官方 default 主题。
     *
     * <p>
     * 如果 workdir/themes/default 不存在，
     * 或对应目录没有合法 theme.yaml，
     * 则返回 {@link Optional#empty()}。
     * </p>
     *
     * <p>
     * 返回空结果不代表整个页面回退失败。
     * 上层模板回退链可以继续进入核心内置 fallback。
     * </p>
     *
     * @return 找到时返回 default 主题描述对象；
     *         未找到时返回 Optional.empty()
     */
    public Optional<ThemeDescriptor> defaultTheme() {
        return themeScanner
            .scanInstalledThemes()
            .stream()
            .filter(
                theme -> DEFAULT_THEME_NAME.equals(
                    theme.name()
                )
            )
            .findFirst();
    }

    /**
     * 获取必须存在的外置官方 default 主题。
     *
     * <p>
     * 本方法适用于明确要求外置 default 必须存在的场景，
     * 例如后台默认主题完整性诊断。
     * </p>
     *
     * <p>
     * 正式访客模板回退流程不一定必须调用本方法，
     * 因为外置 default 缺失时还需要继续尝试
     * 核心内置 fallback，而不是立即终止整个请求。
     * </p>
     *
     * @return 已安装的 default 主题
     * @throws IllegalStateException 当 default 主题未安装时抛出
     */
    public ThemeDescriptor requireDefaultTheme() {
        return defaultTheme()
            .orElseThrow(
                () -> new IllegalStateException(
                    "外置官方 default 主题不存在："
                        + DEFAULT_THEME_NAME
                )
            );
    }

    /**
     * 判断指定主题是否为外置官方 default 主题。
     *
     * <p>
     * 判断依据是主题唯一标识，
     * 不是主题目录显示标题，也不是当前启用状态。
     * </p>
     *
     * @param theme 需要判断的主题描述对象
     * @return 主题名称为 default 时返回 true；
     *         参数为空时返回 false
     */
    public boolean isDefaultTheme(
        ThemeDescriptor theme
    ) {
        return theme != null
            && DEFAULT_THEME_NAME.equals(
                theme.name()
            );
    }

    /**
     * 判断外置官方 default 主题当前是否已经安装。
     *
     * <p>
     * 该方法会执行一次真实主题目录扫描。
     * 后续如果加入主题列表缓存，
     * 缓存必须在主题安装、更新和删除时正确失效。
     * </p>
     *
     * @return 已安装返回 true，否则返回 false
     */
    public boolean isDefaultThemeInstalled() {
        return defaultTheme().isPresent();
    }
}
