package com.aquafish.forum.section;

/**
 * 板块内容审核策略。
 */
public enum ForumSectionModerationPolicy {
    /** 内容发布后直接进入已通过状态。 */
    NONE,
    /** 用户在本板块的第一次发布需要审核。 */
    FIRST_POST,
    /** 所有新主题和回复都需要审核。 */
    ALL_POSTS
}
