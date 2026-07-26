package com.aquafish.forum.section;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquafish.forum.permission.ForumManagementActor;
import com.aquafish.forum.permission.ForumPermissions;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 板块管理领域规则测试。
 *
 * <p>存储层使用 mock，以便精确验证“权限和层级校验在写库之前完成”。
 * SQL 资源的方言和结构契约由 ForumFoundationMigrationTest 独立覆盖。</p>
 */
class ForumSectionManagementServiceTest {

    private ForumSectionRepository repository;
    private ForumSectionManagementService service;
    private ForumManagementActor manager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(ForumSectionRepository.class);
        TransactionalOperator transactions = mock(TransactionalOperator.class);

        /*
         * 单元测试不开真实数据库事务，只让事务包装原样返回 Mono。
         * 真实环境仍由 Spring 注入 R2DBC TransactionalOperator。
         */
        when(transactions.transactional(any(Mono.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service = new ForumSectionManagementService(repository, transactions);
        manager = new ForumManagementActor(
            9L,
            false,
            Set.of(ForumPermissions.SECTION_MANAGE)
        );
    }

    @Test
    void shouldCreateSecondLevelSectionInsideTransaction() {
        ForumSection parent = section(1L, null, "community");
        ForumSection child = section(2L, 1L, "general");
        ForumSectionCommand command = command(1L, "general");

        when(repository.existsBySectionKey("general", null)).thenReturn(Mono.just(false));
        when(repository.findById(1L)).thenReturn(Mono.just(parent));
        when(repository.insert(any(ForumSectionCommand.class), eq(9L))).thenReturn(Mono.just(child));

        StepVerifier.create(service.create(manager, command))
            .expectNext(child)
            .verifyComplete();

        verify(repository).insert(any(ForumSectionCommand.class), eq(9L));
    }

    @Test
    void shouldRejectThirdLevelSectionBeforeInsert() {
        ForumSection secondLevelParent = section(2L, 1L, "general");
        ForumSectionCommand command = command(2L, "deep-child");

        when(repository.existsBySectionKey("deep-child", null)).thenReturn(Mono.just(false));
        when(repository.findById(2L)).thenReturn(Mono.just(secondLevelParent));

        StepVerifier.create(service.create(manager, command))
            .expectErrorMatches(error -> error.getMessage().contains("最多只允许两级"))
            .verify();

        verify(repository, never()).insert(any(), eq(9L));
    }

    @Test
    void shouldRejectManagerWithoutSectionPermissionBeforeRepositoryAccess() {
        ForumManagementActor unauthorized = new ForumManagementActor(8L, false, Set.of());

        StepVerifier.create(Mono.defer(() -> service.create(unauthorized, command(null, "general"))))
            .expectErrorMatches(error -> error.getMessage().contains(ForumPermissions.SECTION_MANAGE))
            .verify();

        verify(repository, never()).existsBySectionKey(any(), any());
    }

    @Test
    void shouldNotMoveSectionWithChildrenUnderAnotherSection() {
        ForumSection current = section(1L, null, "community");
        ForumSection newParent = section(3L, null, "support");

        when(repository.findById(1L)).thenReturn(Mono.just(current));
        when(repository.existsBySectionKey("community", 1L)).thenReturn(Mono.just(false));
        when(repository.findById(3L)).thenReturn(Mono.just(newParent));
        when(repository.existsChild(1L)).thenReturn(Mono.just(true));

        StepVerifier.create(service.update(manager, 1L, command(3L, "community")))
            .expectErrorMatches(error -> error.getMessage().contains("存在子板块"))
            .verify();

        verify(repository, never()).update(eq(1L), any(), eq(9L));
    }

    private ForumSectionCommand command(Long parentId, String key) {
        return new ForumSectionCommand(
            parentId,
            key,
            "测试板块",
            "用于验证板块领域规则。",
            "",
            10,
            ForumSectionVisibility.PUBLIC,
            ForumSectionPostingPolicy.MEMBERS,
            ForumSectionModerationPolicy.NONE,
            true
        );
    }

    private ForumSection section(long id, Long parentId, String key) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 16, 12, 0);
        return new ForumSection(
            id,
            parentId,
            key,
            "测试板块",
            "",
            "",
            10,
            ForumSectionVisibility.PUBLIC,
            ForumSectionPostingPolicy.MEMBERS,
            ForumSectionModerationPolicy.NONE,
            0,
            0,
            true,
            9,
            9,
            now,
            now
        );
    }
}
