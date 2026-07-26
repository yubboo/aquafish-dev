package com.aquafish.content.article;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * 文章创建输入的边界测试。
 *
 * <p>这些测试固定后台初版最重要的输入契约：浏览器提交的首尾空白会被清理，
 * slug 会统一为小写，空正文和非法 slug 不得进入数据库事务。</p>
 */
class ContentArticleCommandTest {

    @Test
    void shouldNormalizeValidArticleInput() {
        ContentArticleCommand normalized = new ContentArticleCommand(
            "  第一篇文章  ",
            "  First-Post  ",
            "  初版摘要  ",
            "  初版正文  "
        ).normalized();

        assertEquals("第一篇文章", normalized.title());
        assertEquals("first-post", normalized.slug());
        assertEquals("初版摘要", normalized.excerpt());
        assertEquals("初版正文", normalized.contentText());
    }

    @Test
    void shouldRejectInvalidSlugBeforePersistence() {
        ContentArticleCommand command = new ContentArticleCommand(
            "第一篇文章",
            "包含 中文",
            "",
            "正文"
        );

        assertThrows(IllegalStateException.class, command::normalized);
    }

    @Test
    void shouldRejectBlankContentBeforePersistence() {
        ContentArticleCommand command = new ContentArticleCommand(
            "第一篇文章",
            "first-post",
            "",
            "   "
        );

        assertThrows(IllegalStateException.class, command::normalized);
    }
}
