package com.aquafish.forum.thread;

/**
 * 发布论坛主题的领域命令。
 *
 * <p>作者身份不属于请求命令，必须从 {@code ForumMemberActor} 取得，
 * 防止调用方伪造其他用户 ID。</p>
 *
 * @param sectionId 目标板块 ID
 * @param title 主题标题
 * @param contentText 第一楼正文原始文本
 */
public record ForumThreadCreateCommand(
    long sectionId,
    String title,
    String contentText
) {

    /**
     * 标准化文本并按照当前数据库字段上限进行基础校验。
     */
    public ForumThreadCreateCommand normalized() {
        if (sectionId <= 0) {
            throw new IllegalStateException("发布主题必须选择有效论坛板块。");
        }

        String safeTitle = title == null ? "" : title.strip();
        String safeContent = contentText == null ? "" : contentText.strip();
        if (safeTitle.isEmpty()) {
            throw new IllegalStateException("论坛主题标题不能为空。");
        }
        if (safeTitle.length() > 240) {
            throw new IllegalStateException("论坛主题标题不能超过 240 个字符。");
        }
        if (safeContent.isEmpty()) {
            throw new IllegalStateException("论坛主题正文不能为空。");
        }

        return new ForumThreadCreateCommand(sectionId, safeTitle, safeContent);
    }
}
