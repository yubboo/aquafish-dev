package com.aquafish.forum.thread;

/**
 * 论坛主题与楼层共用的审核状态。
 */
public enum ForumModerationStatus {
    /** 等待具备板块审核权限的操作人处理。 */
    PENDING,
    /** 已通过审核，可以进入普通可见列表。 */
    APPROVED,
    /** 已被明确拒绝，保留记录但不进入普通列表。 */
    REJECTED
}
