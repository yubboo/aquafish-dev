package com.aquafish.forum.thread;

import java.util.UUID;

/**
 * 主题发布事务写入发件箱的领域事件。
 *
 * <p>当前步骤只负责可靠保存事件，不实现通知或搜索消费者。
 * 审核状态随事件一起保存，消费者不得把待审核主题当作公开内容。</p>
 */
public record ForumThreadCreatedEvent(
    String eventKey,
    long threadId,
    long sectionId,
    long authorUserId,
    ForumModerationStatus moderationStatus
) {

    public static final String EVENT_TYPE = "forum.thread.created.v1";
    public static final String AGGREGATE_TYPE = "THREAD";

    /**
     * 为一次成功的主题创建生成全局幂等事件键。
     */
    public static ForumThreadCreatedEvent create(
        long threadId,
        long sectionId,
        long authorUserId,
        ForumModerationStatus moderationStatus
    ) {
        return new ForumThreadCreatedEvent(
            "forum-thread-created-" + UUID.randomUUID(),
            threadId,
            sectionId,
            authorUserId,
            moderationStatus
        );
    }

    public ForumThreadCreatedEvent {
        if (eventKey == null || eventKey.isBlank() || eventKey.length() > 100) {
            throw new IllegalStateException("论坛主题事件键无效。");
        }
        if (threadId <= 0 || sectionId <= 0 || authorUserId <= 0) {
            throw new IllegalStateException("论坛主题事件缺少有效业务 ID。");
        }
        if (moderationStatus == null) {
            throw new IllegalStateException("论坛主题事件缺少审核状态。");
        }
    }
}
