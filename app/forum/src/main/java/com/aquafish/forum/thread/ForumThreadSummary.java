package com.aquafish.forum.thread;

import java.time.LocalDateTime;

/**
 * 论坛板块主题列表使用的只读摘要。
 *
 * <p>列表不加载第一楼正文，避免主题正文随分页列表重复传输。
 * 用户展示名由后续查询组合层批量装配，forum 表只保存权威作者用户 ID。</p>
 */
public record ForumThreadSummary(
    long id,
    long sectionId,
    long authorUserId,
    String title,
    ForumThreadStatus status,
    ForumModerationStatus moderationStatus,
    int pinnedLevel,
    int featuredLevel,
    long replyCount,
    long viewCount,
    long firstPostId,
    long lastPostId,
    Long lastReplyUserId,
    LocalDateTime lastReplyAt,
    LocalDateTime createdAt,
    LocalDateTime lastActivityAt
) {
}
