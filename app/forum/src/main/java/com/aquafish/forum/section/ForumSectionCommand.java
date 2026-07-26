package com.aquafish.forum.section;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 创建或修改板块的标准化命令。
 *
 * <p>命令不携带操作人 ID，操作人必须由服务层从已鉴权上下文传入，
 * 不能相信页面自己提交的用户 ID。</p>
 */
public record ForumSectionCommand(
    Long parentId,
    String sectionKey,
    String name,
    String description,
    String icon,
    int sortOrder,
    ForumSectionVisibility visibility,
    ForumSectionPostingPolicy postingPolicy,
    ForumSectionModerationPolicy moderationPolicy,
    boolean enabled
) {

    private static final Pattern SECTION_KEY_PATTERN =
        Pattern.compile("^[a-z][a-z0-9-]{0,119}$");

    /**
     * 对输入做统一标准化和长度校验。
     *
     * <p>该方法只校验单个板块字段。父子层级、唯一键和循环引用
     * 需要读取数据库，因此由 ForumSectionManagementService 校验。</p>
     *
     * @return 字段完整且已标准化的新命令
     */
    public ForumSectionCommand normalized() {
        Long safeParentId = parentId == null || parentId <= 0 ? null : parentId;
        String safeKey = text(sectionKey).toLowerCase(Locale.ROOT);
        String safeName = text(name);
        String safeDescription = text(description);
        String safeIcon = text(icon);

        if (!SECTION_KEY_PATTERN.matcher(safeKey).matches()) {
            throw new IllegalStateException(
                "板块标识必须以小写字母开头，且只能包含小写字母、数字和横线。"
            );
        }
        if (safeName.isBlank() || safeName.length() > 120) {
            throw new IllegalStateException("板块名称必须为 1 到 120 个字符。");
        }
        if (safeDescription.length() > 5000) {
            throw new IllegalStateException("板块说明不能超过 5000 个字符。");
        }
        if (safeIcon.length() > 500) {
            throw new IllegalStateException("板块图标引用不能超过 500 个字符。");
        }
        if (sortOrder < 0 || sortOrder > 1_000_000) {
            throw new IllegalStateException("板块排序值必须在 0 到 1000000 之间。");
        }

        return new ForumSectionCommand(
            safeParentId,
            safeKey,
            safeName,
            safeDescription,
            safeIcon,
            sortOrder,
            visibility == null ? ForumSectionVisibility.PUBLIC : visibility,
            postingPolicy == null ? ForumSectionPostingPolicy.MEMBERS : postingPolicy,
            moderationPolicy == null ? ForumSectionModerationPolicy.NONE : moderationPolicy,
            enabled
        );
    }

    /** 只清理普通文本的首尾空白，不修改中间的真实内容。 */
    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
