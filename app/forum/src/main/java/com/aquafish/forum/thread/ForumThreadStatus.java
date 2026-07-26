package com.aquafish.forum.thread;

/**
 * 论坛主题当前业务状态。
 */
public enum ForumThreadStatus {
    /** 正常开放，可以继续回复。 */
    OPEN,
    /** 已关闭，不接受新回复。 */
    CLOSED,
    /** 被管理操作隐藏。 */
    HIDDEN,
    /** 已软删除。 */
    DELETED
}
