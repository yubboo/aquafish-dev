package com.aquafish.core.permalink;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 固定链接目标类型。
 *
 * 当前阶段：
 * Step 17-21-5：固定链接生成器 PermalinkBuilder。
 *
 * 说明：
 * 这里定义系统里哪些对象可以生成固定链接。
 *
 * 当前第一批：
 * 1. article：CMS 文章；
 * 2. page：CMS 单页；
 * 3. category：CMS 分类；
 * 4. tag：CMS 标签；
 * 5. forum：BBS 板块；
 * 6. thread：BBS 帖子；
 * 7. user：用户主页。
 */
public enum PermalinkTargetType {

    ARTICLE("article"),
    PAGE("page"),
    CATEGORY("category"),
    TAG("tag"),
    FORUM("forum"),
    THREAD("thread"),
    USER("user");

    private final String value;

    PermalinkTargetType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static PermalinkTargetType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return ARTICLE;
        }

        for (PermalinkTargetType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }

        return ARTICLE;
    }
}
