package com.aquafish.theme.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * ThemeDescriptor 模板引擎规则自动化测试。
 *
 * <p>
 * 本测试用于验证主题扫描器把 theme.yaml 转换成
 * {@link ThemeDescriptor} 后，模板引擎字段能够保持稳定、
 * 可预测和安全的状态。
 * </p>
 *
 * <p>重点验证以下规则：</p>
 *
 * <ol>
 *     <li>旧主题没有声明 engine 时默认使用 Thymeleaf；</li>
 *     <li>模板引擎标识会去除首尾空格并转换为小写；</li>
 *     <li>Thymeleaf 和 Pebble 都可以作为合法引擎；</li>
 *     <li>未知模板引擎会在创建主题描述时立即被拒绝；</li>
 *     <li>isThymeleaf、isPebble 和 usesEngine 判断正确；</li>
 *     <li>supportsEngine 只接受平台正式支持的引擎；</li>
 *     <li>父主题空白值仍然会被标准化为 null。</li>
 * </ol>
 *
 * <p>
 * 这些测试不会读取真实 theme.yaml，也不会扫描真实主题目录。
 * 它们只验证 ThemeDescriptor 自身的标准化和校验规则，
 * 因此不会修改用户已经安装的主题。
 * </p>
 */
class ThemeDescriptorTest {

    /**
     * 验证旧主题没有声明 engine 时，
     * 系统仍然默认使用 Thymeleaf。
     *
     * <p>
     * 该兼容规则可以防止早期 Aquafish 主题
     * 在升级双模板引擎版本后立即失效。
     * </p>
     */
    @Test
    void shouldDefaultMissingEngineToThymeleaf() {
        ThemeDescriptor descriptor =
            createDescriptor(
                null,
                null
            );

        assertEquals(
            "thymeleaf",
            descriptor.engine()
        );

        assertTrue(
            descriptor.isThymeleaf()
        );

        assertFalse(
            descriptor.isPebble()
        );

        assertTrue(
            descriptor.usesEngine("thymeleaf")
        );

        assertTrue(
            descriptor.usesEngine(" THYMELEAF ")
        );
    }

    /**
     * 验证空白 engine 与 null 一样，
     * 都会兼容性回退为 Thymeleaf。
     */
    @Test
    void shouldDefaultBlankEngineToThymeleaf() {
        ThemeDescriptor descriptor =
            createDescriptor(
                "   ",
                null
            );

        assertEquals(
            "thymeleaf",
            descriptor.engine()
        );

        assertTrue(
            descriptor.isThymeleaf()
        );
    }

    /**
     * 验证 Pebble 标识会被标准化为稳定小写形式。
     *
     * <p>
     * theme.yaml 中即使写成 PEBBLE 或带有无意义空格，
     * 进入主题系统后也必须统一为 pebble。
     * </p>
     */
    @Test
    void shouldAcceptAndNormalizePebbleEngine() {
        ThemeDescriptor descriptor =
            createDescriptor(
                " PEBBLE ",
                null
            );

        assertEquals(
            "pebble",
            descriptor.engine()
        );

        assertTrue(
            descriptor.isPebble()
        );

        assertFalse(
            descriptor.isThymeleaf()
        );

        assertTrue(
            descriptor.usesEngine("pebble")
        );

        assertTrue(
            descriptor.usesEngine(" Pebble ")
        );

        assertFalse(
            descriptor.usesEngine("thymeleaf")
        );
    }

    /**
     * 验证显式声明的 Thymeleaf 标识也会被标准化。
     */
    @Test
    void shouldAcceptAndNormalizeThymeleafEngine() {
        ThemeDescriptor descriptor =
            createDescriptor(
                " THYMELEAF ",
                null
            );

        assertEquals(
            "thymeleaf",
            descriptor.engine()
        );

        assertTrue(
            descriptor.isThymeleaf()
        );

        assertFalse(
            descriptor.isPebble()
        );
    }

    /**
     * 验证平台不支持的模板引擎会被立即拒绝。
     *
     * <p>
     * 不允许未知引擎进入主题启用和访客页面渲染阶段，
     * 否则错误会延迟到用户访问页面时才出现。
     * </p>
     */
    @Test
    void shouldRejectUnsupportedThemeEngine() {
        IllegalArgumentException error =
            assertThrows(
                IllegalArgumentException.class,
                () -> createDescriptor(
                    "freemarker",
                    null
                )
            );

        assertTrue(
            error.getMessage().contains(
                "不受支持的模板引擎"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "freemarker"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "thymeleaf"
            )
        );

        assertTrue(
            error.getMessage().contains(
                "pebble"
            )
        );
    }

    /**
     * 验证 supportsEngine 静态方法能够复用平台引擎规则。
     *
     * <p>
     * 后续主题安装器、应用中心兼容检查和后台表单
     * 可以调用该方法进行基础引擎判断。
     * </p>
     */
    @Test
    void shouldReportSupportedThemeEngines() {
        assertTrue(
            ThemeDescriptor.supportsEngine(
                "thymeleaf"
            )
        );

        assertTrue(
            ThemeDescriptor.supportsEngine(
                " THYMELEAF "
            )
        );

        assertTrue(
            ThemeDescriptor.supportsEngine(
                "pebble"
            )
        );

        assertTrue(
            ThemeDescriptor.supportsEngine(
                " PEBBLE "
            )
        );

        assertFalse(
            ThemeDescriptor.supportsEngine(
                "freemarker"
            )
        );

        assertFalse(
            ThemeDescriptor.supportsEngine(null)
        );

        assertFalse(
            ThemeDescriptor.supportsEngine("   ")
        );
    }

    /**
     * 验证 usesEngine 对无效调用参数安全返回 false。
     */
    @Test
    void shouldReturnFalseForBlankEngineComparison() {
        ThemeDescriptor descriptor =
            createDescriptor(
                "pebble",
                null
            );

        assertFalse(
            descriptor.usesEngine(null)
        );

        assertFalse(
            descriptor.usesEngine("   ")
        );

        assertFalse(
            descriptor.usesEngine("freemarker")
        );
    }

    /**
     * 验证父主题空白值仍然会被标准化为 null。
     *
     * <p>
     * 本步骤虽然主要测试模板引擎，
     * 但覆盖 ThemeDescriptor 时不能破坏原有父主题空值行为。
     * </p>
     */
    @Test
    void shouldNormalizeBlankParentToNull() {
        ThemeDescriptor descriptor =
            createDescriptor(
                "thymeleaf",
                "   "
            );

        assertNull(
            descriptor.parent()
        );

        assertFalse(
            descriptor.hasParent()
        );
    }

    /**
     * 验证有效父主题名称能够被保留。
     */
    @Test
    void shouldKeepValidParentThemeName() {
        ThemeDescriptor descriptor =
            createDescriptor(
                "pebble",
                " pebble-parent "
            );

        assertEquals(
            "pebble-parent",
            descriptor.parent()
        );

        assertTrue(
            descriptor.hasParent()
        );
    }

    /**
     * 创建测试使用的主题描述对象。
     *
     * <p>
     * 路径字段使用固定测试值，
     * 不要求这些文件和目录真实存在。
     * 本测试只验证对象自身的字段标准化和校验。
     * </p>
     *
     * @param engine 需要测试的模板引擎原始值
     * @param parent 需要测试的父主题原始值
     * @return 可用于断言的主题描述对象
     */
    private ThemeDescriptor createDescriptor(
        String engine,
        String parent
    ) {
        return new ThemeDescriptor(
            "test-theme",
            "测试主题",
            "1.0.0",
            engine,
            "Aquafish",
            parent,
            "ThemeDescriptor 自动化测试主题。",
            "H:\\aquafish-test\\themes\\test-theme",
            "H:\\aquafish-test\\themes\\test-theme\\theme.yaml",
            "H:\\aquafish-test\\themes\\test-theme\\settings.yaml",
            "H:\\aquafish-test\\themes\\test-theme\\templates",
            "H:\\aquafish-test\\themes\\test-theme\\assets",
            true,
            true,
            true
        );
    }
}
