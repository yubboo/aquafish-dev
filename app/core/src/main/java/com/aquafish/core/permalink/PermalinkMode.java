package com.aquafish.core.permalink;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Aquafish 固定链接模式。
 *
 * 当前阶段：
 * Step 17-21-3：固定链接后端配置接口。
 *
 * 设计目标：
 * 1. short：Aquafish 默认短链接模式；
 * 2. halo：兼容 Halo CMS 风格；
 * 3. discuz：兼容 Discuz 伪静态风格；
 * 4. custom：自定义规则。
 */
public enum PermalinkMode {

    /**
     * 短链接模式。
     *
     * 示例：
     * /p/1
     * /t/1
     * /f/general
     */
    SHORT("short"),

    /**
     * Halo CMS 风格。
     *
     * 示例：
     * /archives/demo
     * /categories/dev
     * /tags/ai
     */
    HALO("halo"),

    /**
     * Discuz 兼容风格。
     *
     * 示例：
     * thread-1.html
     * forum-1.html
     * article-1.html
     */
    DISCUZ("discuz"),

    /**
     * 自定义规则。
     */
    CUSTOM("custom");

    private final String value;

    PermalinkMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static PermalinkMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return SHORT;
        }

        for (PermalinkMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }

        return SHORT;
    }
}