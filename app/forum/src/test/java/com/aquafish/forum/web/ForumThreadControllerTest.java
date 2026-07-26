package com.aquafish.forum.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquafish.forum.permission.ForumMemberActor;
import com.aquafish.forum.permission.ForumMemberActorFactory;
import com.aquafish.forum.permission.ForumPermissions;
import com.aquafish.forum.thread.ForumModerationStatus;
import com.aquafish.forum.thread.ForumThreadCreateCommand;
import com.aquafish.forum.thread.ForumThreadCreationResult;
import com.aquafish.forum.thread.ForumThreadService;
import com.aquafish.user.auth.MemberAuthUser;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 论坛主题 Controller 不信任请求作者字段的测试。
 */
class ForumThreadControllerTest {

    @Test
    void publishShouldTakeAuthorOnlyFromAuthenticatedPrincipal() {
        ForumThreadService service = mock(ForumThreadService.class);
        ForumMemberActorFactory factory = new ForumMemberActorFactory();
        ForumThreadController controller = new ForumThreadController(service, factory);
        MemberAuthUser user = member(9L);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            user,
            "secret",
            java.util.List.of()
        );
        when(service.publish(any(), any())).thenReturn(Mono.just(
            new ForumThreadCreationResult(
                101L,
                501L,
                ForumModerationStatus.APPROVED
            )
        ));

        StepVerifier.create(controller.publish(
                3L,
                new ForumThreadController.ForumThreadPublishRequest(
                    "主题",
                    "正文"
                ),
                authentication
            ))
            .assertNext(response -> {
                assertEquals(201, response.getStatusCode().value());
                assertEquals(101L, response.getBody().data().threadId());
            })
            .verifyComplete();

        ArgumentCaptor<ForumMemberActor> actor =
            ArgumentCaptor.forClass(ForumMemberActor.class);
        ArgumentCaptor<ForumThreadCreateCommand> command =
            ArgumentCaptor.forClass(ForumThreadCreateCommand.class);
        verify(service).publish(actor.capture(), command.capture());
        assertEquals(9L, actor.getValue().userId());
        assertEquals(3L, command.getValue().sectionId());
        assertEquals("主题", command.getValue().title());
    }

    @Test
    void anonymousListShouldUseAnonymousActor() {
        ForumThreadService service = mock(ForumThreadService.class);
        ForumMemberActorFactory factory = mock(ForumMemberActorFactory.class);
        ForumMemberActor anonymous = ForumMemberActor.anonymous();
        when(factory.anonymous()).thenReturn(anonymous);
        when(service.list(any(), anyLong(), any()))
            .thenReturn(Mono.error(new IllegalStateException("论坛板块不存在：3")));
        ForumThreadController controller = new ForumThreadController(service, factory);

        StepVerifier.create(controller.list(3L, 1, 20, null))
            .assertNext(response -> assertEquals(
                404,
                response.getStatusCode().value()
            ))
            .verifyComplete();

        verify(service).list(any(), anyLong(), any());
    }

    @Test
    void anonymousPublishShouldReturnUnauthorizedInsideReactivePipeline() {
        ForumThreadService service = mock(ForumThreadService.class);
        ForumThreadController controller = new ForumThreadController(
            service,
            new ForumMemberActorFactory()
        );

        StepVerifier.create(controller.publish(
                3L,
                new ForumThreadController.ForumThreadPublishRequest(
                    "主题",
                    "正文"
                ),
                null
            ))
            .assertNext(response -> assertEquals(
                401,
                response.getStatusCode().value()
            ))
            .verifyComplete();
    }

    private MemberAuthUser member(long id) {
        return new MemberAuthUser(
            id,
            id,
            "AQUA_" + id,
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
            false
        );
    }
}
