package com.aquafish.forum.section;

/**
 * 板块新主题发布策略。
 */
public enum ForumSectionPostingPolicy {
    /** 板块不接受新主题，但可以保留历史内容阅读。 */
    CLOSED,
    /** 具备前台发布权限的普通会员可发布。 */
    MEMBERS,
    /** 只允许板块另行配置的用户组发布。 */
    SELECTED_GROUPS
}
