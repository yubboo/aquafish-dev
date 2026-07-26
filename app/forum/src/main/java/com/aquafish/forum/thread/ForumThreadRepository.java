package com.aquafish.forum.thread;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 论坛主题响应式存储契约。
 *
 * <p>接口按发布事务中的原子步骤拆分，事务边界由领域服务统一控制。
 * 实现不得在方法内部创建独立事务，否则主题、第一楼、统计和发件箱可能部分提交。</p>
 */
public interface ForumThreadRepository {

    /**
     * 判断作者在板块内是否已有审核通过的帖子。
     * FIRST_POST 策略会持续审核，直到该用户至少有一条通过审核的内容。
     */
    Mono<Boolean> existsApprovedPostByAuthorInSection(long sectionId, long authorUserId);

    /** 创建主题聚合并返回数据库生成的主键。 */
    Mono<Long> insertThread(
        long sectionId,
        long authorUserId,
        String title,
        ForumModerationStatus moderationStatus
    );

    /** 创建固定为第一楼的主题正文并返回数据库生成的主键。 */
    Mono<Long> insertFirstPost(
        long threadId,
        long sectionId,
        long authorUserId,
        String contentText,
        ForumModerationStatus moderationStatus
    );

    /** 把第一楼 ID 回填为主题的首帖和当前最后帖子。 */
    Mono<Void> completeThreadCreation(long threadId, long firstPostId);

    /**
     * 增加板块可见主题和帖子统计。
     * 待审核主题不调用该方法，避免后台统计包含前台不可见内容。
     */
    Mono<Void> incrementVisibleSectionStatistics(long sectionId);

    /** 在发布事务内追加不可丢失的主题创建事件。 */
    Mono<Void> appendCreationEvent(ForumThreadCreatedEvent event);

    /** 按稳定排序读取指定板块内可见主题。 */
    Flux<ForumThreadSummary> findVisibleBySection(
        long sectionId,
        ForumThreadQuery query
    );

    /** 统计指定板块内可见主题数量。 */
    Mono<Long> countVisibleBySection(long sectionId);
}
