package com.aquafish.theme.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Aquafish 完整主题继承链解析器。
 *
 * <p>
 * 本组件建立在 {@link ThemeParentResolver} 之上，
 * 用于从当前主题开始，逐级解析直接父主题，
 * 最终生成完整且有序的主题继承链。
 * </p>
 *
 * <p>例如：</p>
 *
 * <pre>
 * child-theme
 *     parent: business-theme
 *
 * business-theme
 *     parent: base-theme
 *
 * base-theme
 *     没有父主题
 * </pre>
 *
 * <p>最终返回顺序为：</p>
 *
 * <pre>
 * child-theme
 * -> business-theme
 * -> base-theme
 * </pre>
 *
 * <p>
 * 返回顺序从最具体的当前主题开始，
 * 逐渐走向更加通用的父主题。
 * 该顺序与后续模板回退查询顺序保持一致：
 * 系统优先查找子主题模板，缺失时再进入父主题。
 * </p>
 *
 * <p>
 * 本组件同时负责检测异常继承结构，包括：
 * </p>
 *
 * <ul>
 *     <li>主题把自己声明为父主题；</li>
 *     <li>A 继承 B，同时 B 又继承 A；</li>
 *     <li>更多主题组成的间接循环；</li>
 *     <li>继承层级超过平台安全上限；</li>
 *     <li>父主题不存在；</li>
 *     <li>父主题与子主题模板引擎不同。</li>
 * </ul>
 *
 * <p>
 * 父主题不存在和跨模板引擎继承规则，
 * 由 {@link ThemeParentResolver} 负责检查；
 * 本组件在此基础上继续处理多层继承和循环检测。
 * </p>
 *
 * <p>
 * 本组件当前只负责生成继承链，
 * 暂时不负责以下工作：
 * </p>
 *
 * <ul>
 *     <li>不负责实际查找模板文件；</li>
 *     <li>不负责渲染 Thymeleaf 或 Pebble 模板；</li>
 *     <li>不负责 default 主题回退；</li>
 *     <li>不负责核心内置 fallback；</li>
 *     <li>不负责合并父子主题设置；</li>
 *     <li>不负责合并父子主题静态资源。</li>
 * </ul>
 */
@Component
public class ThemeInheritanceResolver {

    /**
     * Aquafish 允许的最大主题继承层数。
     *
     * <p>
     * 当前主题自身也计入继承层数。
     * 例如当前主题加上两个父主题，层数为 3。
     * </p>
     *
     * <p>
     * 正常主题通常只有一到三层继承。
     * 设置 32 层上限可以在不影响合理扩展的情况下，
     * 防止异常主题清单造成无限查找、
     * 过深递归或大量重复磁盘扫描。
     * </p>
     */
    private static final int MAX_INHERITANCE_DEPTH = 32;

    /**
     * 直接父主题解析器。
     *
     * <p>
     * 每次只负责从一个主题找到它声明的直接父主题，
     * 并完成父主题存在性及同模板引擎校验。
     * </p>
     */
    private final ThemeParentResolver themeParentResolver;

    /**
     * 创建完整主题继承链解析器。
     *
     * @param themeParentResolver 直接父主题解析器，
     *                            不允许为 null
     */
    public ThemeInheritanceResolver(
        ThemeParentResolver themeParentResolver
    ) {
        if (themeParentResolver == null) {
            throw new IllegalArgumentException(
                "父主题解析器不能为空。"
            );
        }

        this.themeParentResolver =
            themeParentResolver;
    }

    /**
     * 从指定主题开始解析完整主题继承链。
     *
     * <p>处理流程：</p>
     *
     * <ol>
     *     <li>检查起始主题对象是否为空；</li>
     *     <li>把当前主题加入继承链；</li>
     *     <li>记录已经访问过的主题名称；</li>
     *     <li>通过 ThemeParentResolver 查找直接父主题；</li>
     *     <li>如果存在父主题，则继续向上解析；</li>
     *     <li>如果不存在父主题，则完成继承链；</li>
     *     <li>访问到重复主题时立即判定为循环继承；</li>
     *     <li>超过最大层数时立即停止并报错。</li>
     * </ol>
     *
     * <p>
     * 返回列表为不可修改列表。
     * 调用方不能直接增加、删除或替换其中的主题，
     * 防止模板解析过程中继承顺序被意外改变。
     * </p>
     *
     * <p>独立主题返回：</p>
     *
     * <pre>
     * [当前主题]
     * </pre>
     *
     * <p>子主题返回：</p>
     *
     * <pre>
     * [当前子主题, 父主题, 顶层父主题]
     * </pre>
     *
     * @param activeTheme 当前需要解析继承结构的主题
     * @return 从当前主题到最顶层父主题的不可修改有序列表
     * @throws IllegalArgumentException 当起始主题为空时抛出
     * @throws IllegalStateException 当出现循环继承、
     *                               继承层级过深、
     *                               父主题缺失或跨引擎继承时抛出
     */
    public List<ThemeDescriptor> resolveChain(
        ThemeDescriptor activeTheme
    ) {
        if (activeTheme == null) {
            throw new IllegalArgumentException(
                "起始主题描述对象不能为空。"
            );
        }

        /*
         * ArrayList 保留从子主题到父主题的明确顺序。
         *
         * 后续模板解析器可以按照该顺序逐个查找模板文件。
         */
        List<ThemeDescriptor> inheritanceChain =
            new ArrayList<>();

        /*
         * LinkedHashSet 同时完成两项工作：
         *
         * 1. 快速判断主题是否已经访问过；
         * 2. 保留访问顺序，方便在异常信息中展示循环路径。
         */
        Set<String> visitedThemeNames =
            new LinkedHashSet<>();

        ThemeDescriptor currentTheme =
            activeTheme;

        while (currentTheme != null) {
            /*
             * 在加入新主题之前检查最大层数。
             *
             * 即使因为异常数据没有形成明显循环，
             * 也不能允许无限深的继承结构持续解析。
             */
            if (
                inheritanceChain.size()
                    >= MAX_INHERITANCE_DEPTH
            ) {
                throw new IllegalStateException(
                    "主题继承层级超过安全上限 "
                        + MAX_INHERITANCE_DEPTH
                        + " 层。当前解析路径："
                        + formatThemePath(
                            visitedThemeNames
                        )
                );
            }

            String currentThemeName =
                currentTheme.name();

            /*
             * 如果主题名称已经访问过，
             * 表示继承链重新回到了之前的主题。
             *
             * 例如：
             * A -> B -> C -> A
             */
            if (
                !visitedThemeNames.add(
                    currentThemeName
                )
            ) {
                throw new IllegalStateException(
                    "检测到主题循环继承："
                        + formatCyclePath(
                            visitedThemeNames,
                            currentThemeName
                        )
                );
            }

            inheritanceChain.add(
                currentTheme
            );

            /*
             * 解析当前主题的直接父主题。
             *
             * ThemeParentResolver 会同时检查：
             * 1. 父主题是否已经安装；
             * 2. 父子主题是否使用同一种模板引擎。
             */
            Optional<ThemeDescriptor> parentTheme =
                themeParentResolver.resolveParent(
                    currentTheme
                );

            if (parentTheme.isEmpty()) {
                /*
                 * 当前主题没有父主题，
                 * 说明已经到达继承链最顶层。
                 */
                break;
            }

            currentTheme = parentTheme.orElseThrow();
        }

        /*
         * 返回不可修改副本，
         * 避免外部代码更改解析完成的继承顺序。
         */
        return List.copyOf(
            inheritanceChain
        );
    }

    /**
     * 返回主题继承链的总层数。
     *
     * <p>
     * 当前主题自身计为一层。
     * 该方法内部仍然执行完整继承校验，
     * 不会绕过循环、缺失父主题或跨引擎检查。
     * </p>
     *
     * @param activeTheme 当前主题
     * @return 当前主题及其全部父主题的数量
     */
    public int resolveDepth(
        ThemeDescriptor activeTheme
    ) {
        return resolveChain(
            activeTheme
        ).size();
    }

    /**
     * 返回继承链最顶层的根主题。
     *
     * <p>
     * 独立主题的根主题就是其自身；
     * 子主题的根主题是继承链最后一个元素。
     * </p>
     *
     * @param activeTheme 当前主题
     * @return 最顶层且不再声明父主题的主题
     */
    public ThemeDescriptor resolveRootTheme(
        ThemeDescriptor activeTheme
    ) {
        List<ThemeDescriptor> chain =
            resolveChain(activeTheme);

        return chain.get(
            chain.size() - 1
        );
    }

    /**
     * 把已经访问的主题名称格式化为可读路径。
     *
     * <p>示例：</p>
     *
     * <pre>
     * child-theme -> parent-theme -> base-theme
     * </pre>
     *
     * @param themeNames 按访问顺序保存的主题名称
     * @return 适合日志和异常信息展示的继承路径
     */
    private String formatThemePath(
        Set<String> themeNames
    ) {
        return String.join(
            " -> ",
            themeNames
        );
    }

    /**
     * 格式化循环继承路径。
     *
     * <p>
     * 已访问路径末尾会再次追加造成循环的主题名称，
     * 让错误信息能够清楚显示闭环。
     * </p>
     *
     * <p>示例：</p>
     *
     * <pre>
     * theme-a -> theme-b -> theme-c -> theme-a
     * </pre>
     *
     * @param visitedThemeNames 已访问主题名称
     * @param repeatedThemeName 再次出现并造成循环的主题名称
     * @return 完整循环继承路径
     */
    private String formatCyclePath(
        Set<String> visitedThemeNames,
        String repeatedThemeName
    ) {
        String existingPath =
            formatThemePath(
                visitedThemeNames
            );

        if (existingPath.isBlank()) {
            return repeatedThemeName
                + " -> "
                + repeatedThemeName;
        }

        return existingPath
            + " -> "
            + repeatedThemeName;
    }
}
