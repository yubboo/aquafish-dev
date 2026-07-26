package com.aquafish.forum.thread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquafish.forum.permission.ForumMemberActor;
import com.aquafish.forum.permission.ForumPermissions;
import com.aquafish.forum.section.ForumSection;
import com.aquafish.forum.section.ForumSectionModerationPolicy;
import com.aquafish.forum.section.ForumSectionPostingPolicy;
import com.aquafish.forum.section.ForumSectionRepository;
import com.aquafish.forum.section.ForumSectionVisibility;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 主题发布事务、板块策略和列表权限测试。
 *
 * <p>仓储使用 mock，便于验证权限校验先于数据库访问，以及主题、第一楼、
 * 统计和事务发件箱的固定写入顺序。SQL 方言由迁移契约测试和编译独立验证。</p>
 */
class ForumThreadServiceTest {

    private ForumSectionRepository sectionRepository;
    private ForumThreadRepository threadRepository;
    private ForumThreadService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sectionRepository = mock(ForumSectionRepository.class);
        threadRepository = mock(ForumThreadRepository.class);
        TransactionalOperator transactions = mock(TransactionalOperator.class);
        when(transactions.transactional(any(Mono.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        service = new ForumThreadService(
            sectionRepository,
            threadRepository,
            transactions
        );
    }

    @Test
    void shouldPublishApprovedThreadWithFirstPostStatisticsAndOutboxInOrder() {
        ForumSection section = section(
            ForumSectionVisibility.PUBLIC,
            ForumSectionPostingPolicy.MEMBERS,
            ForumSectionModerationPolicy.NONE,
            true
        );
        when(sectionRepository.findById(3L)).thenReturn(Mono.just(section));
        when(threadRepository.insertThread(
            3L,
            9L,
            "第一篇主题",
            ForumModerationStatus.APPROVED
        )).thenReturn(Mono.just(101L));
        when(threadRepository.insertFirstPost(
            101L,
            3L,
            9L,
            "第一楼正文",
            ForumModerationStatus.APPROVED
        )).thenReturn(Mono.just(501L));
        when(threadRepository.completeThreadCreation(101L, 501L))
            .thenReturn(Mono.empty());
        when(threadRepository.incrementVisibleSectionStatistics(3L))
            .thenReturn(Mono.empty());
        when(threadRepository.appendCreationEvent(any()))
            .thenReturn(Mono.empty());

        StepVerifier.create(service.publish(
                member(false, Set.of(), Set.of()),
                new ForumThreadCreateCommand(3L, " 第一篇主题 ", " 第一楼正文 ")
            ))
            .assertNext(result -> {
                assertEquals(101L, result.threadId());
                assertEquals(501L, result.firstPostId());
                assertEquals(ForumModerationStatus.APPROVED, result.moderationStatus());
            })
            .verifyComplete();

        ArgumentCaptor<ForumThreadCreatedEvent> eventCaptor =
            ArgumentCaptor.forClass(ForumThreadCreatedEvent.class);
        InOrder order = inOrder(sectionRepository, threadRepository);
        order.verify(sectionRepository).findById(3L);
        order.verify(threadRepository).insertThread(
            3L,
            9L,
            "第一篇主题",
            ForumModerationStatus.APPROVED
        );
        order.verify(threadRepository).insertFirstPost(
            101L,
            3L,
            9L,
            "第一楼正文",
            ForumModerationStatus.APPROVED
        );
        order.verify(threadRepository).completeThreadCreation(101L, 501L);
        order.verify(threadRepository).incrementVisibleSectionStatistics(3L);
        order.verify(threadRepository).appendCreationEvent(eventCaptor.capture());

        ForumThreadCreatedEvent event = eventCaptor.getValue();
        assertEquals(101L, event.threadId());
        assertEquals(3L, event.sectionId());
        assertEquals(9L, event.authorUserId());
        assertEquals(ForumModerationStatus.APPROVED, event.moderationStatus());
        assertTrue(event.eventKey().startsWith("forum-thread-created-"));
    }

    @Test
    void shouldKeepPendingThreadOutOfVisibleSectionStatistics() {
        ForumSection section = section(
            ForumSectionVisibility.PUBLIC,
            ForumSectionPostingPolicy.MEMBERS,
            ForumSectionModerationPolicy.ALL_POSTS,
            true
        );
        stubPendingPublish(section);

        StepVerifier.create(service.publish(
                member(false, Set.of(), Set.of()),
                new ForumThreadCreateCommand(3L, "待审核主题", "待审核正文")
            ))
            .assertNext(result -> assertEquals(
                ForumModerationStatus.PENDING,
                result.moderationStatus()
            ))
            .verifyComplete();

        verify(threadRepository, never()).incrementVisibleSectionStatistics(anyLong());
        ArgumentCaptor<ForumThreadCreatedEvent> eventCaptor =
            ArgumentCaptor.forClass(ForumThreadCreatedEvent.class);
        verify(threadRepository).appendCreationEvent(eventCaptor.capture());
        assertEquals(
            ForumModerationStatus.PENDING,
            eventCaptor.getValue().moderationStatus()
        );
    }

    @Test
    void shouldRequireFirstPostReviewUntilAuthorHasApprovedContent() {
        ForumSection section = section(
            ForumSectionVisibility.PUBLIC,
            ForumSectionPostingPolicy.MEMBERS,
            ForumSectionModerationPolicy.FIRST_POST,
            true
        );
        when(sectionRepository.findById(3L)).thenReturn(Mono.just(section));
        when(threadRepository.existsApprovedPostByAuthorInSection(3L, 9L))
            .thenReturn(Mono.just(false));
        stubWrite(
            ForumModerationStatus.PENDING,
            "第一次发布",
            "第一次正文"
        );

        StepVerifier.create(service.publish(
                member(false, Set.of(), Set.of()),
                new ForumThreadCreateCommand(3L, "第一次发布", "第一次正文")
            ))
            .assertNext(result -> assertEquals(
                ForumModerationStatus.PENDING,
                result.moderationStatus()
            ))
            .verifyComplete();

        verify(threadRepository).existsApprovedPostByAuthorInSection(3L, 9L);
        verify(threadRepository, never()).incrementVisibleSectionStatistics(anyLong());
    }

    @Test
    void shouldRejectBannedMemberBeforeAnyRepositoryAccess() {
        StepVerifier.create(service.publish(
                member(true, Set.of(), Set.of()),
                new ForumThreadCreateCommand(3L, "不能发布", "正文")
            ))
            .expectErrorMatches(error -> error.getMessage().contains("禁止发布"))
            .verify();

        verify(sectionRepository, never()).findById(anyLong());
        verify(threadRepository, never()).insertThread(
            anyLong(),
            anyLong(),
            any(),
            any()
        );
    }

    @Test
    void shouldRejectMissingCreatePermissionBeforeAnyRepositoryAccess() {
        ForumMemberActor unauthorized = new ForumMemberActor(
            9L,
            true,
            false,
            Set.of(ForumPermissions.THREAD_READ),
            Set.of(),
            Set.of()
        );

        StepVerifier.create(service.publish(
                unauthorized,
                new ForumThreadCreateCommand(3L, "不能发布", "正文")
            ))
            .expectErrorMatches(error -> error.getMessage().contains(
                ForumPermissions.THREAD_CREATE
            ))
            .verify();

        verify(sectionRepository, never()).findById(anyLong());
    }

    @Test
    void shouldRejectClosedSectionBeforeThreadInsert() {
        ForumSection section = section(
            ForumSectionVisibility.PUBLIC,
            ForumSectionPostingPolicy.CLOSED,
            ForumSectionModerationPolicy.NONE,
            true
        );
        when(sectionRepository.findById(3L)).thenReturn(Mono.just(section));

        StepVerifier.create(service.publish(
                member(false, Set.of(), Set.of()),
                new ForumThreadCreateCommand(3L, "关闭板块", "正文")
            ))
            .expectErrorMatches(error -> error.getMessage().contains("关闭新主题"))
            .verify();

        verify(threadRepository, never()).insertThread(
            anyLong(),
            anyLong(),
            any(),
            any()
        );
    }

    @Test
    void shouldAllowSelectedGroupOnlyWithResolvedSectionGrant() {
        ForumSection section = section(
            ForumSectionVisibility.PUBLIC,
            ForumSectionPostingPolicy.SELECTED_GROUPS,
            ForumSectionModerationPolicy.NONE,
            true
        );
        when(sectionRepository.findById(3L)).thenReturn(Mono.just(section));
        stubWrite(
            ForumModerationStatus.APPROVED,
            "分组主题",
            "分组正文"
        );
        when(threadRepository.incrementVisibleSectionStatistics(3L))
            .thenReturn(Mono.empty());

        StepVerifier.create(service.publish(
                member(false, Set.of(3L), Set.of()),
                new ForumThreadCreateCommand(3L, "分组主题", "分组正文")
            ))
            .expectNextCount(1)
            .verifyComplete();

        verify(threadRepository).insertThread(
            3L,
            9L,
            "分组主题",
            ForumModerationStatus.APPROVED
        );
    }

    @Test
    void shouldListPublicSectionForAnonymousViewerWithStablePage() {
        ForumSection section = section(
            ForumSectionVisibility.PUBLIC,
            ForumSectionPostingPolicy.MEMBERS,
            ForumSectionModerationPolicy.NONE,
            true
        );
        ForumThreadQuery query = new ForumThreadQuery(2, 20);
        ForumThreadSummary summary = summary(101L);
        when(sectionRepository.findById(3L)).thenReturn(Mono.just(section));
        when(threadRepository.findVisibleBySection(3L, query))
            .thenReturn(Flux.just(summary));
        when(threadRepository.countVisibleBySection(3L))
            .thenReturn(Mono.just(21L));

        StepVerifier.create(service.list(null, 3L, query))
            .assertNext(page -> {
                assertEquals(1, page.items().size());
                assertEquals(2, page.page());
                assertEquals(20, page.size());
                assertEquals(21L, page.total());
                assertEquals(2L, page.totalPages());
            })
            .verifyComplete();
    }

    @Test
    void shouldRejectAnonymousMemberSectionBeforeThreadQuery() {
        ForumSection section = section(
            ForumSectionVisibility.MEMBERS,
            ForumSectionPostingPolicy.MEMBERS,
            ForumSectionModerationPolicy.NONE,
            true
        );
        when(sectionRepository.findById(3L)).thenReturn(Mono.just(section));

        StepVerifier.create(service.list(null, 3L, ForumThreadQuery.defaults()))
            .expectErrorMatches(error -> error.getMessage().contains("需要先登录"))
            .verify();

        verify(threadRepository, never()).findVisibleBySection(anyLong(), any());
        verify(threadRepository, never()).countVisibleBySection(anyLong());
    }

    private void stubPendingPublish(ForumSection section) {
        when(sectionRepository.findById(3L)).thenReturn(Mono.just(section));
        stubWrite(
            ForumModerationStatus.PENDING,
            "待审核主题",
            "待审核正文"
        );
    }

    private void stubWrite(
        ForumModerationStatus status,
        String title,
        String content
    ) {
        when(threadRepository.insertThread(3L, 9L, title, status))
            .thenReturn(Mono.just(101L));
        when(threadRepository.insertFirstPost(101L, 3L, 9L, content, status))
            .thenReturn(Mono.just(501L));
        when(threadRepository.completeThreadCreation(101L, 501L))
            .thenReturn(Mono.empty());
        when(threadRepository.appendCreationEvent(any()))
            .thenReturn(Mono.empty());
    }

    private ForumMemberActor member(
        boolean banned,
        Set<Long> selectedSections,
        Set<Long> privateSections
    ) {
        return new ForumMemberActor(
            9L,
            true,
            banned,
            Set.of(
                ForumPermissions.THREAD_CREATE,
                ForumPermissions.THREAD_READ
            ),
            selectedSections,
            privateSections
        );
    }

    private ForumSection section(
        ForumSectionVisibility visibility,
        ForumSectionPostingPolicy postingPolicy,
        ForumSectionModerationPolicy moderationPolicy,
        boolean enabled
    ) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 12, 0);
        return new ForumSection(
            3L,
            null,
            "general",
            "综合讨论",
            "",
            "",
            10,
            visibility,
            postingPolicy,
            moderationPolicy,
            0L,
            0L,
            enabled,
            1L,
            1L,
            now,
            now
        );
    }

    private ForumThreadSummary summary(long id) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 12, 0);
        return new ForumThreadSummary(
            id,
            3L,
            9L,
            "列表主题",
            ForumThreadStatus.OPEN,
            ForumModerationStatus.APPROVED,
            0,
            0,
            0L,
            0L,
            501L,
            501L,
            null,
            null,
            now,
            now
        );
    }
}
