package com.aquafish.core.admin.auth;

/**
 * 后台会话存储边界。
 *
 * <p>默认使用单机内存实现；启用 Redis 时由基础设施模块提供替代实现，
 * 认证领域服务不感知具体存储介质。</p>
 */
public interface AdminAuthSessionStore {

    void save(AdminAuthSession session);

    AdminAuthSession find(String token);

    AdminAuthSession delete(String token);

    int deleteByUserId(long userId);

    int deleteExpired();
}
