package com.aquafish.forum.thread;

import java.util.List;

/**
 * 论坛主题分页结果。
 */
public record ForumThreadPage(
    List<ForumThreadSummary> items,
    int page,
    int size,
    long total,
    long totalPages
) {

    /**
     * 创建不可变分页快照并计算总页数。
     */
    public static ForumThreadPage of(
        List<ForumThreadSummary> items,
        ForumThreadQuery query,
        long total
    ) {
        List<ForumThreadSummary> safeItems =
            items == null ? List.of() : List.copyOf(items);
        long safeTotal = Math.max(total, 0L);
        long totalPages = safeTotal == 0L
            ? 0L
            : (safeTotal + query.size() - 1L) / query.size();
        return new ForumThreadPage(
            safeItems,
            query.page(),
            query.size(),
            safeTotal,
            totalPages
        );
    }
}
