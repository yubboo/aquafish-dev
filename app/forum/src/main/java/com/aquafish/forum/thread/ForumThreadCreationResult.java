package com.aquafish.forum.thread;

/**
 * 主题与第一楼在同一事务内创建成功后的结果。
 *
 * @param threadId 新主题 ID
 * @param firstPostId 第一楼帖子 ID
 * @param moderationStatus 主题和第一楼共同使用的审核状态
 */
public record ForumThreadCreationResult(
    long threadId,
    long firstPostId,
    ForumModerationStatus moderationStatus
) {
}
