package com.aquafish.forum.thread;

/**
 * 论坛主题列表分页条件。
 *
 * <p>页码从 1 开始，单页上限固定为 100，防止前台请求一次读取全部主题。</p>
 *
 * @param page 页码，从 1 开始
 * @param size 每页数量
 */
public record ForumThreadQuery(
    int page,
    int size
) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /**
     * 创建默认分页条件。
     */
    public static ForumThreadQuery defaults() {
        return new ForumThreadQuery(DEFAULT_PAGE, DEFAULT_SIZE);
    }

    /**
     * 拒绝非法页码，并把过大的单页数量限制到稳定上限。
     */
    public ForumThreadQuery normalized() {
        if (page <= 0) {
            throw new IllegalStateException("论坛主题列表页码必须大于 0。");
        }
        if (size <= 0) {
            throw new IllegalStateException("论坛主题列表每页数量必须大于 0。");
        }
        return new ForumThreadQuery(page, Math.min(size, MAX_SIZE));
    }

    /**
     * 计算数据库分页偏移量，使用 long 避免大页码整数溢出。
     */
    public long offset() {
        return Math.multiplyExact((long) page - 1L, (long) size);
    }
}
