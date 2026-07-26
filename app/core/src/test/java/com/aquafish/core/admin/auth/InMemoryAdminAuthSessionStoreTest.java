package com.aquafish.core.admin.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryAdminAuthSessionStoreTest {

    @Test
    void shouldSaveFindAndDeleteSession() {
        InMemoryAdminAuthSessionStore store = new InMemoryAdminAuthSessionStore();
        AdminAuthSession session = session("token-a", 1, LocalDateTime.now().plusHours(1));

        store.save(session);

        assertSame(session, store.find("token-a"));
        assertSame(session, store.delete("token-a"));
        assertNull(store.find("token-a"));
    }

    @Test
    void shouldRevokeAllSessionsForUserOnly() {
        InMemoryAdminAuthSessionStore store = new InMemoryAdminAuthSessionStore();
        store.save(session("token-a", 7, LocalDateTime.now().plusHours(1)));
        store.save(session("token-b", 7, LocalDateTime.now().plusHours(1)));
        store.save(session("token-c", 8, LocalDateTime.now().plusHours(1)));

        assertEquals(2, store.deleteByUserId(7));
        assertNull(store.find("token-a"));
        assertNull(store.find("token-b"));
        assertEquals(8, store.find("token-c").user().id());
    }

    @Test
    void shouldDeleteExpiredSessions() {
        InMemoryAdminAuthSessionStore store = new InMemoryAdminAuthSessionStore();
        store.save(session("expired", 1, LocalDateTime.now().minusSeconds(1)));
        store.save(session("active", 1, LocalDateTime.now().plusHours(1)));

        assertEquals(1, store.deleteExpired());
        assertNull(store.find("expired"));
        assertEquals("active", store.find("active").token());
    }

    private AdminAuthSession session(String token, long userId, LocalDateTime expiresAt) {
        AdminAuthUser user = new AdminAuthUser(
            userId,
            "user" + userId,
            "user@example.com",
            "用户",
            "",
            "ACTIVE",
            List.of("admin"),
            true
        );
        return new AdminAuthSession(token, user, expiresAt);
    }
}
