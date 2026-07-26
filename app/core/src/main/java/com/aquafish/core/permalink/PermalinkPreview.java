package com.aquafish.core.permalink;

import java.util.List;

/**
 * 固定链接预览结果。
 *
 * 当前作用：
 * 后台固定链接设置页面实时预览：
 * 文章链接
 * 单页链接
 * 分类链接
 * 标签链接
 * 板块链接
 * 帖子链接
 * 用户主页链接
 */
public record PermalinkPreview(
    PermalinkMode mode,
    String article,
    String page,
    String category,
    String tag,
    String forum,
    String thread,
    String user,
    List<String> examples
) {
}