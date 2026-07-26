package com.aquafish.forum.section;

import java.time.LocalDateTime;

/**
 * 论坛板块领域模型。
 *
 * <p>该 record 只表示一个已经持久化的板块快照。
 * 创建和修改的输入校验由 ForumSectionCommand 与领域服务负责，
 * 避免把数据库行映射和外部请求混成一个类。</p>
 */
public record ForumSection(
    long id,
    Long parentId,
    String sectionKey,
    String name,
    String description,
    String icon,
    int sortOrder,
    ForumSectionVisibility visibility,
    ForumSectionPostingPolicy postingPolicy,
    ForumSectionModerationPolicy moderationPolicy,
    long threadCount,
    long postCount,
    boolean enabled,
    long createdBy,
    long updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    /**
     * 顶级板块没有父板块。
     *
     * @return 当前板块是顶级板块时返回 true
     */
    public boolean topLevel() {
        return parentId == null;
    }
}
