package com.aquafish.forum.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aquafish.user.auth.MemberAuthUser;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 统一会员主体到论坛 Actor 的信任边界测试。
 */
class ForumMemberActorFactoryTest {

    @Test
    void shouldUseOnlyServerAuthenticatedMemberFacts() {
        ForumMemberActorFactory factory = new ForumMemberActorFactory();
        MemberAuthUser member = new MemberAuthUser(
            9L,
            9L,
            "AQUA_9",
            "member",
            "会员",
            "",
            1L,
            "member",
            Set.of("member"),
            Set.of(
                ForumPermissions.THREAD_READ,
                ForumPermissions.THREAD_CREATE
            ),
            true
        );

        ForumMemberActor actor = factory.authenticated(member);

        assertEquals(9L, actor.userId());
        assertTrue(actor.forumPostingBanned());
        assertEquals(member.permissions(), actor.permissions());
        assertTrue(actor.selectedPostingSectionIds().isEmpty());
        assertTrue(actor.privateReadableSectionIds().isEmpty());
    }
}
