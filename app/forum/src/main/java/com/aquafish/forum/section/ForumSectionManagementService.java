package com.aquafish.forum.section;

import com.aquafish.forum.permission.ForumManagementActor;
import com.aquafish.forum.permission.ForumPermissions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 论坛板块后台管理领域服务。
 *
 * <p>该服务是板块管理规则的唯一入口，当前负责：</p>
 *
 * <ol>
 *     <li>校验 forum.section.manage 权限；</li>
 *     <li>标准化板块命令并校验唯一标识；</li>
 *     <li>强制第一版最多两级板块；</li>
 *     <li>防止自己成为自己的父板块或把已有子板块变成二级板块；</li>
 *     <li>将校验与写入放在同一响应式事务中。</li>
 * </ol>
 *
 * <p>本阶段不提供物理删除。停用板块会保留主题、楼层和审核历史，
 * 避免后台误操作破坏论坛内容链。</p>
 */
@Service
public final class ForumSectionManagementService {

    private final ForumSectionRepository repository;
    private final TransactionalOperator transactionalOperator;

    public ForumSectionManagementService(
        ForumSectionRepository repository,
        TransactionalOperator transactionalOperator
    ) {
        this.repository = repository;
        this.transactionalOperator = transactionalOperator;
    }

    /**
     * 读取后台板块列表。即使是只读操作，也不允许绕过后台权限。
     */
    public Flux<ForumSection> listForManagement(ForumManagementActor actor) {
        requireManagePermission(actor);
        return repository.findAllOrdered();
    }

    /**
     * 创建新板块。
     *
     * <p>唯一键预检可提供友好中文错误，数据库唯一索引仍是并发情况下的最终保护。</p>
     */
    public Mono<ForumSection> create(
        ForumManagementActor actor,
        ForumSectionCommand source
    ) {
        requireManagePermission(actor);
        ForumSectionCommand command = requireCommand(source);

        Mono<ForumSection> work = Mono.defer(() ->
            requireUniqueKey(command.sectionKey(), null)
                /*
                 * then(Mono.defer(...)) 保证只有前一项校验成功后，
                 * 才会组装和执行下一个存储操作。
                 * 这不只是测试要求，也可避免无权或非法请求提前访问数据库。
                 */
                .then(Mono.defer(() -> validateParent(command.parentId(), null)))
                .then(Mono.defer(() -> repository.insert(command, actor.userId())))
        );

        return transactionalOperator.transactional(work);
    }

    /**
     * 修改板块。更换父板块时会重新校验两级深度和循环关系。
     */
    public Mono<ForumSection> update(
        ForumManagementActor actor,
        long sectionId,
        ForumSectionCommand source
    ) {
        requireManagePermission(actor);
        requireSectionId(sectionId);
        ForumSectionCommand command = requireCommand(source);

        Mono<ForumSection> work = Mono.defer(() -> requireSection(sectionId))
            .then(Mono.defer(() -> requireUniqueKey(command.sectionKey(), sectionId)))
            .then(Mono.defer(() -> validateParent(command.parentId(), sectionId)))
            .then(Mono.defer(() -> validateSectionCanBecomeChild(sectionId, command.parentId())))
            .then(Mono.defer(() -> repository.update(sectionId, command, actor.userId())));

        return transactionalOperator.transactional(work);
    }

    /**
     * 启用或停用板块。这是第一版对板块下线的正式方式，不进行物理删除。
     */
    public Mono<ForumSection> changeEnabled(
        ForumManagementActor actor,
        long sectionId,
        boolean enabled
    ) {
        requireManagePermission(actor);
        requireSectionId(sectionId);

        Mono<ForumSection> work = Mono.defer(() -> requireSection(sectionId))
            .then(Mono.defer(() ->
                repository.updateEnabled(sectionId, enabled, actor.userId())
            ));

        return transactionalOperator.transactional(work);
    }

    /** 校验板块标识不与其他板块重复。 */
    private Mono<Void> requireUniqueKey(String sectionKey, Long excludedSectionId) {
        return repository.existsBySectionKey(sectionKey, excludedSectionId)
            .flatMap(exists -> exists
                ? Mono.error(new IllegalStateException("板块标识已存在：" + sectionKey))
                : Mono.empty()
            );
    }

    /**
     * 校验父板块存在且必须为顶级板块。
     * 子板块不能再成为父节点，从源头保证树深度不超过两级。
     */
    private Mono<Void> validateParent(Long parentId, Long currentSectionId) {
        if (parentId == null) {
            return Mono.empty();
        }
        if (currentSectionId != null && parentId.equals(currentSectionId)) {
            return Mono.error(new IllegalStateException("论坛板块不能把自己设为父板块。"));
        }

        return repository.findById(parentId)
            .switchIfEmpty(Mono.error(new IllegalStateException("选择的父板块不存在。")))
            .flatMap(parent -> parent.topLevel()
                ? Mono.empty()
                : Mono.error(new IllegalStateException("论坛第一版最多只允许两级板块。"))
            );
    }

    /**
     * 已经存在子板块的顶级板块不能直接改成二级板块，
     * 否则其子板块会在一次修改后变成第三级。
     */
    private Mono<Void> validateSectionCanBecomeChild(long sectionId, Long parentId) {
        if (parentId == null) {
            return Mono.empty();
        }
        return repository.existsChild(sectionId)
            .flatMap(hasChild -> hasChild
                ? Mono.error(new IllegalStateException("当前板块存在子板块，不能直接改为二级板块。"))
                : Mono.empty()
            );
    }

    private Mono<ForumSection> requireSection(long sectionId) {
        return repository.findById(sectionId)
            .switchIfEmpty(Mono.error(new IllegalStateException("论坛板块不存在：" + sectionId)));
    }

    private ForumSectionCommand requireCommand(ForumSectionCommand command) {
        if (command == null) {
            throw new IllegalStateException("论坛板块配置不能为空。");
        }
        return command.normalized();
    }

    private void requireSectionId(long sectionId) {
        if (sectionId <= 0) {
            throw new IllegalStateException("论坛板块 ID 必须大于 0。");
        }
    }

    private void requireManagePermission(ForumManagementActor actor) {
        if (actor == null) {
            throw new IllegalStateException("论坛管理操作缺少鉴权上下文。");
        }
        actor.require(ForumPermissions.SECTION_MANAGE);
    }
}
