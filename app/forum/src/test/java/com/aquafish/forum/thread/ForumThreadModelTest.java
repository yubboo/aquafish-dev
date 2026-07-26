package com.aquafish.forum.thread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquafish.forum.permission.ForumMemberActor;
import com.aquafish.forum.permission.ForumPermissions;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 主题命令、分页和会员安全上下文的纯领域测试。
 */
class ForumThreadModelTest {

    @Test
    void shouldNormalizeThreadCommandWithoutChangingInternalContent() {
        ForumThreadCreateCommand command = new ForumThreadCreateCommand(
            3L,
            "  保留 标题间空格  ",
            "\n  第一行\n第二行  \n"
        ).normalized();

        assertEquals("保留 标题间空格", command.title());
        assertEquals("第一行\n第二行", command.contentText());
    }

    @Test
    void shouldRejectTitleLongerThanDatabaseContract() {
        String title = "题".repeat(241);

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> new ForumThreadCreateCommand(3L, title, "正文").normalized()
        );
        assertEquals("论坛主题标题不能超过 240 个字符。", error.getMessage());
    }

    @Test
    void shouldLimitPageSizeAndCalculateLongOffset() {
        ForumThreadQuery query = new ForumThreadQuery(3, 1000).normalized();

        assertEquals(100, query.size());
        assertEquals(200L, query.offset());
    }

    @Test
    void shouldCopyMemberPermissionAndSectionGrants() {
        Set<String> permissions = new HashSet<>();
        Set<Long> sections = new HashSet<>();
        permissions.add(ForumPermissions.THREAD_CREATE);
        sections.add(3L);

        ForumMemberActor actor = new ForumMemberActor(
            9L,
            true,
            false,
            permissions,
            sections,
            sections
        );
        permissions.clear();
        sections.clear();

        actor.requireCanCreateThread();
        assertEquals(Set.of(3L), actor.selectedPostingSectionIds());
        assertEquals(Set.of(3L), actor.privateReadableSectionIds());
    }
}
