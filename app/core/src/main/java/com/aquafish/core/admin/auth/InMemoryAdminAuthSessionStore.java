package com.aquafish.core.admin.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 无 Redis 场景下的默认后台会话存储。
 */
@Component
public final class InMemoryAdminAuthSessionStore implements AdminAuthSessionStore {

    private final Map<String, AdminAuthSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(AdminAuthSession session) {
        if (session == null || session.token() == null || session.token().isBlank()) {
            throw new IllegalArgumentException("后台会话及 Token 不能为空。");
        }
        sessions.put(session.token(), session);
    }

    @Override
    public AdminAuthSession find(String token) {
        return token == null ? null : sessions.get(token);
    }

    @Override
    public AdminAuthSession delete(String token) {
        return token == null ? null : sessions.remove(token);
    }

    @Override
    public int deleteByUserId(long userId) {
        if (userId <= 0) {
            return 0;
        }

        int[] removed = {0};
        sessions.entrySet().removeIf(entry -> {
            AdminAuthSession session = entry.getValue();
            boolean matches = session != null
                && session.user() != null
                && session.user().id() == userId;
            if (matches) {
                removed[0]++;
            }
            return matches;
        });
        return removed[0];
    }

    @Override
    public int deleteExpired() {
        int[] removed = {0};
        sessions.entrySet().removeIf(entry -> {
            AdminAuthSession session = entry.getValue();
            boolean expired = session == null || session.expired();
            if (expired) {
                removed[0]++;
            }
            return expired;
        });
        return removed[0];
    }
}
