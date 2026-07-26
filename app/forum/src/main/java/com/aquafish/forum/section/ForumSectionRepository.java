package com.aquafish.forum.section;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 论坛板块响应式存储契约。
 *
 * <p>领域服务只依赖该接口，不依赖具体 SQL 和数据库方言。
 * 数据库实现位于同模块的 database 子包，避免把持久化细节放进领域服务。</p>
 */
public interface ForumSectionRepository {

    /** 按树形展示所需的父级、排序值和 ID 稳定读取全部板块。 */
    Flux<ForumSection> findAllOrdered();

    /** 按主键读取板块，不存在时返回空 Mono。 */
    Mono<ForumSection> findById(long sectionId);

    /** 判断板块标识是否被其他板块占用。 */
    Mono<Boolean> existsBySectionKey(String sectionKey, Long excludedSectionId);

    /** 判断指定板块是否已有子板块。 */
    Mono<Boolean> existsChild(long parentSectionId);

    /** 创建板块并返回数据库中的完整快照。 */
    Mono<ForumSection> insert(ForumSectionCommand command, long operatorUserId);

    /** 修改板块基础配置并返回更新后快照。 */
    Mono<ForumSection> update(long sectionId, ForumSectionCommand command, long operatorUserId);

    /** 启用或停用板块，不删除历史主题与回复。 */
    Mono<ForumSection> updateEnabled(long sectionId, boolean enabled, long operatorUserId);
}
