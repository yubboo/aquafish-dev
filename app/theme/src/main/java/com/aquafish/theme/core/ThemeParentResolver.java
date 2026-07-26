package com.aquafish.theme.core;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Aquafish 父主题解析器。
 *
 * <p>
 * 本组件负责根据子主题 theme.yaml 中声明的 parent 字段，
 * 在当前已经安装的主题中找到对应父主题。
 * </p>
 *
 * <p>主题清单示例：</p>
 *
 * <pre>
 * id: modern-child
 * engine: thymeleaf
 * parent: modern-parent
 * </pre>
 *
 * <p>
 * 父主题解析是后续主题模板回退链的重要基础。
 * 当子主题缺少某个模板时，模板系统可以继续到父主题中查找。
 * </p>
 *
 * <p>Aquafish 明确禁止跨模板引擎继承：</p>
 *
 * <pre>
 * Thymeleaf 子主题 -> Thymeleaf 父主题：允许
 * Pebble 子主题    -> Pebble 父主题：允许
 * Thymeleaf 子主题 -> Pebble 父主题：拒绝
 * Pebble 子主题    -> Thymeleaf 父主题：拒绝
 * </pre>
 *
 * <p>
 * 禁止跨引擎继承的原因是 Thymeleaf 与 Pebble
 * 使用不同模板语法。
 * 子主题模板中的 include、extends、表达式和布局语法
 * 无法安全继承另一种模板引擎的父主题文件。
 * </p>
 *
 * <p>
 * 本类当前只解析直接父主题，
 * 暂时不递归构建完整祖先链，也不负责检测多层循环继承。
 * 循环继承检查会在后续主题层级规则中单独实现。
 * </p>
 *
 * <p>本类不负责：</p>
 *
 * <ul>
 *     <li>不负责安装或删除主题；</li>
 *     <li>不负责修改当前启用主题；</li>
 *     <li>不负责执行模板渲染；</li>
 *     <li>不负责 default 和核心 fallback；</li>
 *     <li>不负责应用中心主题授权。</li>
 * </ul>
 */
@Component
public class ThemeParentResolver {

    /**
     * 已安装主题扫描器。
     *
     * <p>
     * 父主题必须来自当前 Aquafish 实例已经安装的主题目录。
     * 本解析器不允许使用不存在或尚未安装的父主题。
     * </p>
     */
    private final ThemeScanner themeScanner;

    /**
     * 创建父主题解析器。
     *
     * @param themeScanner 已安装主题扫描器，不允许为 null
     */
    public ThemeParentResolver(
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
     * 解析指定子主题声明的直接父主题。
     *
     * <p>处理流程：</p>
     *
     * <ol>
     *     <li>检查子主题对象是否为空；</li>
     *     <li>检查子主题是否声明 parent；</li>
     *     <li>扫描当前已经安装的主题；</li>
     *     <li>根据父主题唯一名称查找父主题；</li>
     *     <li>检查子主题与父主题是否使用同一种模板引擎；</li>
     *     <li>返回父主题描述对象。</li>
     * </ol>
     *
     * <p>
     * 没有声明 parent 的独立主题会返回 Optional.empty()，
     * 这属于正常状态，不代表解析失败。
     * </p>
     *
     * <p>
     * 如果已经声明 parent，但对应主题没有安装，
     * 则抛出明确异常。
     * 不能静默把缺失父主题当成独立主题，
     * 否则子主题可能缺少布局、模板或静态资源。
     * </p>
     *
     * @param childTheme 需要解析父主题的子主题
     * @return 声明父主题时返回父主题对象；
     *         没有声明父主题时返回 Optional.empty()
     * @throws IllegalArgumentException 当子主题对象为空时抛出
     * @throws IllegalStateException 当父主题不存在，
     *                               或父子主题引擎不一致时抛出
     */
    public Optional<ThemeDescriptor> resolveParent(
        ThemeDescriptor childTheme
    ) {
        if (childTheme == null) {
            throw new IllegalArgumentException(
                "子主题描述对象不能为空。"
            );
        }

        /*
         * 独立主题没有 parent，属于正常状态。
         *
         * 例如官方 default 主题通常不继承其他主题。
         */
        if (!childTheme.hasParent()) {
            return Optional.empty();
        }

        String parentThemeName = normalizeThemeName(
            childTheme.parent()
        );

        /*
         * 每次解析都读取当前已经安装的主题列表，
         * 避免主题安装或删除后继续使用过期的父主题对象。
         *
         * 后续增加主题目录缓存时，
         * 缓存失效仍然必须与主题安装、更新和删除联动。
         */
        List<ThemeDescriptor> installedThemes =
            themeScanner.scanInstalledThemes();

        ThemeDescriptor parentTheme =
            installedThemes
                .stream()
                .filter(
                    theme -> theme.name()
                        .equals(parentThemeName)
                )
                .findFirst()
                .orElseThrow(
                    () -> new IllegalStateException(
                        "子主题 "
                            + childTheme.name()
                            + " 声明的父主题不存在："
                            + parentThemeName
                    )
                );

        /*
         * 父子主题必须使用相同模板引擎。
         *
         * 例如 Pebble 子主题不能继承 Thymeleaf 父主题，
         * 否则布局、表达式和模板片段语法无法兼容。
         */
        validateSameEngine(
            childTheme,
            parentTheme
        );

        return Optional.of(parentTheme);
    }

    /**
     * 获取指定子主题的父主题。
     *
     * <p>
     * 与 resolveParent 不同，本方法要求子主题必须声明父主题。
     * 适用于模板回退流程已经确认需要进入父主题的场景。
     * </p>
     *
     * @param childTheme 必须声明父主题的子主题
     * @return 已安装且模板引擎兼容的父主题
     * @throws IllegalStateException 当子主题没有声明父主题时抛出
     */
    public ThemeDescriptor requireParent(
        ThemeDescriptor childTheme
    ) {
        return resolveParent(childTheme)
            .orElseThrow(
                () -> new IllegalStateException(
                    "主题 "
                        + childTheme.name()
                        + " 没有声明父主题。"
                )
            );
    }

    /**
     * 检查父主题与子主题是否使用同一种模板引擎。
     *
     * <p>允许示例：</p>
     *
     * <pre>
     * child.engine = thymeleaf
     * parent.engine = thymeleaf
     * </pre>
     *
     * <p>拒绝示例：</p>
     *
     * <pre>
     * child.engine = pebble
     * parent.engine = thymeleaf
     * </pre>
     *
     * @param childTheme 子主题
     * @param parentTheme 父主题
     * @throws IllegalStateException 当两个主题模板引擎不一致时抛出
     */
    private void validateSameEngine(
        ThemeDescriptor childTheme,
        ThemeDescriptor parentTheme
    ) {
        if (
            childTheme.engine().equals(
                parentTheme.engine()
            )
        ) {
            return;
        }

        throw new IllegalStateException(
            "禁止跨模板引擎继承：子主题 "
                + childTheme.name()
                + " 使用 "
                + childTheme.engine()
                + "，父主题 "
                + parentTheme.name()
                + " 使用 "
                + parentTheme.engine()
                + "。父主题和子主题必须使用同一种模板引擎。"
        );
    }

    /**
     * 标准化父主题唯一名称。
     *
     * <p>
     * ThemeDescriptor 的主题名称统一使用小写格式，
     * 因此父主题查找时也要转换为小写，
     * 避免 theme.yaml 中大小写差异导致无法查找。
     * </p>
     *
     * @param themeName theme.yaml 中声明的父主题名称
     * @return 去除首尾空格并转换为小写的主题名称
     */
    private String normalizeThemeName(
        String themeName
    ) {
        if (
            themeName == null
                || themeName.isBlank()
        ) {
            return "";
        }

        return themeName
            .trim()
            .toLowerCase(Locale.ROOT);
    }
}
