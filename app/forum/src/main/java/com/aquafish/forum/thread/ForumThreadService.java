package com.aquafish.forum.thread;

import com.aquafish.forum.permission.ForumMemberActor;
import com.aquafish.forum.section.ForumSection;
import com.aquafish.forum.section.ForumSectionModerationPolicy;
import com.aquafish.forum.section.ForumSectionPostingPolicy;
import com.aquafish.forum.section.ForumSectionRepository;
import com.aquafish.forum.section.ForumSectionVisibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * 论坛主题发布与板块主题列表领域服务。
 *
 * <p>发布主题的全部写操作使用同一个 R2DBC 响应式事务：
 * 权限与封禁检查、板块策略检查、主题、第一楼、可见统计和发件箱
 * 任一步失败都会回滚，禁止产生没有首帖的主题或丢失事件。</p>
 *
 * <p>前台会员认证由 user 模块的数据库会话和 Spring Security 统一完成；
 * 本服务只接受由服务端认证层构造的 {@link ForumMemberActor}，
 * 不提供接收请求 userId 的发帖接口。主题列表具备公开、会员和私有板块的
 * 领域访问规则。</p>
 */
@Service
public final class ForumThreadService {

    private final ForumSectionRepository sectionRepository;
    private final ForumThreadRepository threadRepository;
    private final TransactionalOperator transactionalOperator;

    public ForumThreadService(
        ForumSectionRepository sectionRepository,
        ForumThreadRepository threadRepository,
        TransactionalOperator transactionalOperator
    ) {
        this.sectionRepository = sectionRepository;
        this.threadRepository = threadRepository;
        this.transactionalOperator = transactionalOperator;
    }

    /**
     * 发布主题与第一楼。
     *
     * <p>前置账号校验在任何数据库访问之前执行。FIRST_POST 审核策略只在作者已有
     * 审核通过内容时自动通过；被拒绝或仍待审核的内容不会绕过第一次审核。</p>
     */
    public Mono<ForumThreadCreationResult> publish(
        ForumMemberActor actor,
        ForumThreadCreateCommand source
    ) {
        return Mono.defer(() -> {
            ForumMemberActor safeActor = requireActor(actor);
            safeActor.requireCanCreateThread();
            ForumThreadCreateCommand command = requireCommand(source);

            Mono<ForumThreadCreationResult> work = sectionRepository
                .findById(command.sectionId())
                .switchIfEmpty(Mono.error(new IllegalStateException(
                    "发布主题的论坛板块不存在：" + command.sectionId()
                )))
                .flatMap(section -> {
                    requirePublishPolicy(safeActor, section);
                    return resolveModerationStatus(section, safeActor.userId())
                        .flatMap(status -> persistThread(safeActor, command, status));
                });

            return transactionalOperator.transactional(work);
        });
    }

    /**
     * 读取板块主题分页列表。
     *
     * <p>匿名访问者可读取公开板块；会员板块要求读取权限；
     * 私有板块还必须出现在认证层解析出的私有板块授权集合中。</p>
     */
    public Mono<ForumThreadPage> list(
        ForumMemberActor viewer,
        long sectionId,
        ForumThreadQuery source
    ) {
        return Mono.defer(() -> {
            if (sectionId <= 0) {
                return Mono.error(new IllegalStateException("论坛板块 ID 必须大于 0。"));
            }
            ForumMemberActor safeViewer =
                viewer == null ? ForumMemberActor.anonymous() : viewer;
            ForumThreadQuery query =
                source == null ? ForumThreadQuery.defaults() : source.normalized();

            return sectionRepository.findById(sectionId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                    "论坛板块不存在：" + sectionId
                )))
                .flatMap(section -> {
                    requireReadableSection(safeViewer, section);
                    return Mono.zip(
                        threadRepository.findVisibleBySection(sectionId, query).collectList(),
                        threadRepository.countVisibleBySection(sectionId)
                    );
                })
                .map(result -> ForumThreadPage.of(result.getT1(), query, result.getT2()));
        });
    }

    /**
     * 完成主题、第一楼、统计和事件的顺序写入。
     */
    private Mono<ForumThreadCreationResult> persistThread(
        ForumMemberActor actor,
        ForumThreadCreateCommand command,
        ForumModerationStatus moderationStatus
    ) {
        return threadRepository.insertThread(
                command.sectionId(),
                actor.userId(),
                command.title(),
                moderationStatus
            )
            .flatMap(threadId -> threadRepository.insertFirstPost(
                    threadId,
                    command.sectionId(),
                    actor.userId(),
                    command.contentText(),
                    moderationStatus
                )
                .flatMap(firstPostId -> threadRepository
                    .completeThreadCreation(threadId, firstPostId)
                    .then(incrementVisibleStatisticsIfApproved(
                        command.sectionId(),
                        moderationStatus
                    ))
                    .then(threadRepository.appendCreationEvent(
                        ForumThreadCreatedEvent.create(
                            threadId,
                            command.sectionId(),
                            actor.userId(),
                            moderationStatus
                        )
                    ))
                    .thenReturn(new ForumThreadCreationResult(
                        threadId,
                        firstPostId,
                        moderationStatus
                    ))
                )
            );
    }

    private Mono<Void> incrementVisibleStatisticsIfApproved(
        long sectionId,
        ForumModerationStatus moderationStatus
    ) {
        if (moderationStatus != ForumModerationStatus.APPROVED) {
            return Mono.empty();
        }
        return threadRepository.incrementVisibleSectionStatistics(sectionId);
    }

    /**
     * 根据板块审核策略计算主题和第一楼的共同审核状态。
     */
    private Mono<ForumModerationStatus> resolveModerationStatus(
        ForumSection section,
        long authorUserId
    ) {
        ForumSectionModerationPolicy policy = section.moderationPolicy();
        return switch (policy) {
            case NONE -> Mono.just(ForumModerationStatus.APPROVED);
            case ALL_POSTS -> Mono.just(ForumModerationStatus.PENDING);
            case FIRST_POST -> threadRepository
                .existsApprovedPostByAuthorInSection(section.id(), authorUserId)
                .map(hasApprovedPost -> hasApprovedPost
                    ? ForumModerationStatus.APPROVED
                    : ForumModerationStatus.PENDING
                );
        };
    }

    /**
     * 检查板块是否允许当前会员发布新主题。
     */
    private void requirePublishPolicy(
        ForumMemberActor actor,
        ForumSection section
    ) {
        if (!section.enabled()) {
            throw new IllegalStateException("当前论坛板块已停用，不能发布主题。");
        }
        if (section.visibility() == ForumSectionVisibility.PRIVATE
            && !actor.canReadPrivateSection(section.id())) {
            throw new IllegalStateException("当前用户没有该私有板块的访问权限。");
        }

        ForumSectionPostingPolicy policy = section.postingPolicy();
        if (policy == ForumSectionPostingPolicy.CLOSED) {
            throw new IllegalStateException("当前论坛板块已关闭新主题发布。");
        }
        if (policy == ForumSectionPostingPolicy.SELECTED_GROUPS
            && !actor.canPublishInSelectedSection(section.id())) {
            throw new IllegalStateException("当前用户组不能在该板块发布主题。");
        }
    }

    /**
     * 检查板块可见策略；停用板块不对普通前台列表开放。
     */
    private void requireReadableSection(
        ForumMemberActor viewer,
        ForumSection section
    ) {
        if (!section.enabled()) {
            throw new IllegalStateException("当前论坛板块已停用。");
        }
        if (section.visibility() == ForumSectionVisibility.PUBLIC) {
            return;
        }

        viewer.requireCanReadThread();
        if (section.visibility() == ForumSectionVisibility.PRIVATE
            && !viewer.canReadPrivateSection(section.id())) {
            throw new IllegalStateException("当前用户没有该私有板块的访问权限。");
        }
    }

    private ForumMemberActor requireActor(ForumMemberActor actor) {
        if (actor == null) {
            throw new IllegalStateException("发布论坛主题缺少可信认证上下文。");
        }
        return actor;
    }

    private ForumThreadCreateCommand requireCommand(ForumThreadCreateCommand command) {
        if (command == null) {
            throw new IllegalStateException("论坛主题发布命令不能为空。");
        }
        return command.normalized();
    }
}
