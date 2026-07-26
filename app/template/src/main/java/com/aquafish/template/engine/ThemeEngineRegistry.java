package com.aquafish.template.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Aquafish 主题模板引擎注册中心。
 *
 * <p>
 * 本类位于模板系统的引擎管理层，负责统一收集、保存和查询
 * 所有已经接入 Aquafish 平台的 {@link ThemeEngine} 实现。
 * </p>
 *
 * <p>
 * 当前系统已经存在 Thymeleaf 模板引擎。
 * 后续接入 Pebble 后，Pebble 对应的实现类同样实现
 * {@link ThemeEngine} 接口，并由 Spring 自动注入本注册中心。
 * </p>
 *
 * <p>注册中心主要解决以下问题：</p>
 *
 * <ol>
 *     <li>
 *         避免 CMS、论坛、用户中心等业务模块
 *         直接依赖某一个具体模板引擎；
 *     </li>
 *     <li>
 *         根据主题 theme.yaml 中声明的 engine 标识，
 *         找到对应的模板引擎实现；
 *     </li>
 *     <li>
 *         在系统启动阶段检查是否存在重复的引擎标识；
 *     </li>
 *     <li>
 *         为后台模板引擎设置、主题安装检查和应用中心兼容检查
 *         提供统一的引擎查询入口；
 *     </li>
 *     <li>
 *         为后续增加新的模板引擎保留稳定扩展点，
 *         避免在业务代码中堆积大量 if/else 判断。
 *     </li>
 * </ol>
 *
 * <p>正常注册过程如下：</p>
 *
 * <ol>
 *     <li>Spring 扫描所有实现 {@link ThemeEngine} 的组件；</li>
 *     <li>将所有实现以 List 形式注入构造方法；</li>
 *     <li>读取每个实现返回的 engineId；</li>
 *     <li>将引擎标识统一转换为小写并去除首尾空格；</li>
 *     <li>检查标识是否为空或重复；</li>
 *     <li>生成不可修改的引擎注册表。</li>
 * </ol>
 *
 * <p>
 * 本类只负责管理“已经加载到系统中的模板引擎实现”，
 * 不负责决定管理员是否允许第三方主题使用某个引擎。
 * 后续后台中的启用或禁用开关，应由独立的模板引擎设置服务管理。
 * </p>
 *
 * <p>
 * 同样，本类不负责扫描主题、选择当前主题、处理父子主题继承、
 * 查找模板文件或执行 default、core fallback 回退。
 * 这些职责分别属于主题模块和模板解析模块。
 * </p>
 */
@Component
public class ThemeEngineRegistry {

    /**
     * 系统当前已经注册的全部模板引擎。
     *
     * <p>
     * Map 的 Key 是经过标准化处理的模板引擎唯一标识，例如：
     * </p>
     *
     * <pre>
     * thymeleaf
     * pebble
     * </pre>
     *
     * <p>
     * Map 的 Value 是对应的 {@link ThemeEngine} 实现对象。
     * </p>
     *
     * <p>
     * 使用 {@link LinkedHashMap} 收集引擎，
     * 是为了保持 Spring 注入列表中的稳定顺序，
     * 方便后台展示、日志输出和自动化测试。
     * </p>
     *
     * <p>
     * 构造完成后会通过 {@link Collections#unmodifiableMap(Map)}
     * 转换成不可修改的 Map，防止运行过程中被其他代码
     * 随意增加、替换或删除模板引擎。
     * </p>
     */
    private final Map<String, ThemeEngine> engines;

    /**
     * 创建模板引擎注册中心。
     *
     * <p>
     * Spring 会自动收集容器中所有 {@link ThemeEngine} 实现，
     * 并通过本构造方法注入。
     * </p>
     *
     * <p>当前阶段通常会注入：</p>
     *
     * <pre>
     * ThymeleafTemplateRenderService
     * </pre>
     *
     * <p>接入 Pebble 后还会注入：</p>
     *
     * <pre>
     * PebbleThemeEngine
     * </pre>
     *
     * <p>
     * 构造过程中会立即验证每一个模板引擎的标识。
     * 如果标识为空，或者两个实现声明了相同标识，
     * 系统会在启动阶段直接抛出异常。
     * </p>
     *
     * <p>
     * 选择在启动阶段失败，而不是等页面请求时再失败，
     * 可以避免系统运行一段时间后才暴露引擎冲突问题，
     * 也能防止同一个主题被随机交给错误的引擎渲染。
     * </p>
     *
     * @param themeEngines Spring 容器中发现的全部模板引擎实现；
     *                     列表中的单个元素不应该为 null
     * @throws IllegalStateException 当引擎标识为空，
     *                               或者发现重复引擎标识时抛出
     */
    public ThemeEngineRegistry(List<ThemeEngine> themeEngines) {
        Map<String, ThemeEngine> registeredEngines =
            new LinkedHashMap<>();

        for (ThemeEngine themeEngine : themeEngines) {
            /*
             * 正常情况下 Spring 不会向列表中注入 null。
             * 此处仍然进行防御性检查，
             * 避免未来使用手动装配或测试替身时出现空指针异常。
             */
            if (themeEngine == null) {
                continue;
            }

            /*
             * 所有引擎标识进入注册表前都必须经过统一标准化。
             *
             * 例如：
             * " Thymeleaf " 会转换为 "thymeleaf"。
             *
             * 这样 theme.yaml、后台配置和 Java 实现之间
             * 不会因为大小写或无意义空格产生不一致。
             */
            String engineId = normalizeEngineId(
                themeEngine.engineId()
            );

            if (engineId.isBlank()) {
                throw new IllegalStateException(
                    "模板引擎标识不能为空："
                        + themeEngine.getClass().getName()
                );
            }

            /*
             * putIfAbsent 只会在标识尚未注册时写入。
             *
             * 如果返回值不为 null，
             * 说明已经存在另一个使用相同标识的引擎实现。
             */
            ThemeEngine existingEngine =
                registeredEngines.putIfAbsent(
                    engineId,
                    themeEngine
                );

            if (existingEngine != null) {
                throw new IllegalStateException(
                    "发现重复的模板引擎标识："
                        + engineId
                        + "，冲突实现："
                        + existingEngine.getClass().getName()
                        + " 和 "
                        + themeEngine.getClass().getName()
                );
            }
        }

        /*
         * 注册中心初始化完成后，
         * 不允许外部代码再直接修改引擎集合。
         *
         * 后续如果需要动态启用或禁用某类外部主题，
         * 应修改模板引擎设置，而不是修改本注册表。
         */
        this.engines = Collections.unmodifiableMap(
            registeredEngines
        );
    }

    /**
     * 根据模板引擎标识查找对应实现。
     *
     * <p>
     * 本方法适合“不确定引擎是否存在”的调用场景，例如：
     * </p>
     *
     * <ul>
     *     <li>扫描第三方主题时检查 engine 是否受支持；</li>
     *     <li>后台展示主题兼容性状态；</li>
     *     <li>应用中心安装主题前执行兼容检查；</li>
     *     <li>输出模板引擎诊断信息。</li>
     * </ul>
     *
     * <p>
     * 参数会先经过统一标准化。
     * 如果传入 null、空字符串或系统未注册的标识，
     * 返回 {@link Optional#empty()}，不会直接抛出异常。
     * </p>
     *
     * @param engineId 待查找的模板引擎标识；
     *                 允许传入不同大小写和首尾空格
     * @return 找到时返回包含模板引擎的 Optional；
     *         找不到时返回 Optional.empty()
     */
    public Optional<ThemeEngine> find(String engineId) {
        String normalizedEngineId =
            normalizeEngineId(engineId);

        if (normalizedEngineId.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(
            engines.get(normalizedEngineId)
        );
    }

    /**
     * 根据模板引擎标识获取对应实现。
     *
     * <p>
     * 本方法适合“该引擎必须存在”的正式渲染流程。
     * 如果主题声明的引擎未注册，系统不应该继续尝试渲染，
     * 而应该抛出明确异常，交给上层执行主题回退或错误诊断。
     * </p>
     *
     * <p>
     * 例如主题声明：
     * </p>
     *
     * <pre>
     * engine: pebble
     * </pre>
     *
     * <p>
     * 但系统尚未安装或加载 Pebble 引擎时，
     * 本方法会抛出包含 pebble 标识的异常。
     * </p>
     *
     * @param engineId 必须存在的模板引擎标识
     * @return 对应的模板引擎实现
     * @throws IllegalStateException 当系统未注册该模板引擎时抛出
     */
    public ThemeEngine require(String engineId) {
        String normalizedEngineId =
            normalizeEngineId(engineId);

        return find(normalizedEngineId)
            .orElseThrow(
                () -> new IllegalStateException(
                    "系统不支持模板引擎："
                        + normalizedEngineId
                )
            );
    }

    /**
     * 判断系统是否已经注册指定模板引擎。
     *
     * <p>
     * 本方法只表示引擎实现是否已经加载到 Spring 容器中，
     * 不代表管理员已经允许第三方主题使用该引擎。
     * </p>
     *
     * <p>例如未来可能出现以下状态：</p>
     *
     * <pre>
     * Pebble 引擎已注册：true
     * 后台允许外部 Pebble 主题：false
     * </pre>
     *
     * <p>
     * 所以在正式安装或启用主题时，
     * 除了调用本方法，还需要检查后台模板引擎设置。
     * </p>
     *
     * @param engineId 待检查的模板引擎标识
     * @return 已经注册返回 true，否则返回 false
     */
    public boolean supports(String engineId) {
        return find(engineId).isPresent();
    }

    /**
     * 返回系统当前已经注册的全部模板引擎标识。
     *
     * <p>当前阶段预期包含：</p>
     *
     * <pre>
     * thymeleaf
     * </pre>
     *
     * <p>接入 Pebble 后预期包含：</p>
     *
     * <pre>
     * thymeleaf
     * pebble
     * </pre>
     *
     * <p>
     * 返回集合来源于不可修改的 engines Map，
     * 调用方只能读取，不应该尝试修改。
     * </p>
     *
     * @return 当前已注册模板引擎的只读标识集合
     */
    public Set<String> engineIds() {
        return engines.keySet();
    }

    /**
     * 标准化模板引擎标识。
     *
     * <p>标准化规则：</p>
     *
     * <ol>
     *     <li>null 转换为空字符串；</li>
     *     <li>移除字符串首尾空格；</li>
     *     <li>使用 {@link Locale#ROOT} 转换为小写。</li>
     * </ol>
     *
     * <p>
     * 使用 Locale.ROOT 而不是系统默认语言环境，
     * 可以避免服务器运行在不同地区时产生不同的大小写转换结果。
     * </p>
     *
     * <p>例如：</p>
     *
     * <pre>
     * " Thymeleaf " -> "thymeleaf"
     * "PEBBLE"       -> "pebble"
     * null           -> ""
     * </pre>
     *
     * @param engineId 原始模板引擎标识
     * @return 标准化后的模板引擎标识；
     *         参数为 null 时返回空字符串
     */
    private String normalizeEngineId(String engineId) {
        if (engineId == null) {
            return "";
        }

        return engineId
            .trim()
            .toLowerCase(Locale.ROOT);
    }
}
