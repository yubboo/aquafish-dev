package com.aquafish.content.article;

import java.time.LocalDateTime;

/**
 * CMS 文章的后台与前台只读快照。
 *
 * <p>该模型只包含页面所需字段，不包含数据库连接、Repository 或其他可被模板
 * 调用的服务对象。正文由 Thymeleaf 默认转义输出，当前初版不把原始文本当作
 * 已清洗 HTML。</p>
 */
public record ContentArticle(
    long id,
    String publicId,
    long authorUserId,
    String title,
    String slug,
    String excerpt,
    String contentText,
    String status,
    String visibility,
    long viewCount,
    long commentCount,
    LocalDateTime publishedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
