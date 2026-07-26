package com.aquafish.forum.section;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** 板块命令字段标准化与输入边界测试。 */
class ForumSectionCommandTest {

    @Test
    void shouldNormalizeTextAndFillDefaultPolicies() {
        ForumSectionCommand command = new ForumSectionCommand(
            0L,
            " General-Chat ",
            " 综合交流 ",
            null,
            null,
            10,
            null,
            null,
            null,
            true
        ).normalized();

        assertEquals(null, command.parentId());
        assertEquals("general-chat", command.sectionKey());
        assertEquals("综合交流", command.name());
        assertEquals(ForumSectionVisibility.PUBLIC, command.visibility());
        assertEquals(ForumSectionPostingPolicy.MEMBERS, command.postingPolicy());
        assertEquals(ForumSectionModerationPolicy.NONE, command.moderationPolicy());
    }

    @Test
    void shouldRejectInvalidSectionKeyAndSortOrder() {
        assertThrows(IllegalStateException.class, () -> command("中文标识", 0).normalized());
        assertThrows(IllegalStateException.class, () -> command("valid-key", -1).normalized());
    }

    private ForumSectionCommand command(String key, int sortOrder) {
        return new ForumSectionCommand(
            null,
            key,
            "测试板块",
            "",
            "",
            sortOrder,
            ForumSectionVisibility.PUBLIC,
            ForumSectionPostingPolicy.MEMBERS,
            ForumSectionModerationPolicy.NONE,
            true
        );
    }
}
