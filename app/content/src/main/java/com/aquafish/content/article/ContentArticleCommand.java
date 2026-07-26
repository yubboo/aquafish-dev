package com.aquafish.content.article;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 文章创建输入。
 *
 * <p>作者 ID 和发布状态不接受浏览器提交：作者来自后台认证主体，新文章固定先
 * 进入 DRAFT，再通过独立发布动作切换为 PUBLISHED。</p>
 */
public record ContentArticleCommand(
    String title,
    String slug,
    String excerpt,
    String contentText
) {

    private static final Pattern SLUG_PATTERN =
        Pattern.compile("^[a-z0-9][a-z0-9-]{0,190}$");

    /**
     * 统一清理并校验文章输入。
     */
    public ContentArticleCommand normalized() {
        String safeTitle = text(title);
        String safeSlug = text(slug).toLowerCase(Locale.ROOT);
        String safeExcerpt = text(excerpt);
        String safeContent = text(contentText);

        if (safeTitle.isBlank() || safeTitle.length() > 240) {
            throw new IllegalStateException("文章标题必须为 1 到 240 个字符。");
        }
        if (!SLUG_PATTERN.matcher(safeSlug).matches()) {
            throw new IllegalStateException("文章别名只能包含小写字母、数字和横线，最大 191 个字符。");
        }
        if (safeExcerpt.length() > 5000) {
            throw new IllegalStateException("文章摘要不能超过 5000 个字符。");
        }
        if (safeContent.isBlank() || safeContent.length() > 1_000_000) {
            throw new IllegalStateException("文章正文必须为 1 到 1000000 个字符。");
        }
        return new ContentArticleCommand(
            safeTitle,
            safeSlug,
            safeExcerpt,
            safeContent
        );
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
