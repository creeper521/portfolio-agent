package com.portfolio.agent.turn.continuation;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryConversationSessionStore implements ConversationSessionStore {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    @Override public Optional<Session> find(byte[] tokenHash, Instant now) {
        Session session = sessions.get(key(tokenHash));
        return session == null || !now.isBefore(session.expiresAt())
                ? Optional.empty() : Optional.of(session);
    }
    @Override public void save(Session session) {
        sessions.putIfAbsent(key(session.tokenHash()), session);
    }
    @Override public void revoke(String conversationId) {
        sessions.entrySet().removeIf(value -> value.getValue().conversationId().equals(conversationId));
    }
    private String key(byte[] hash) { return Base64.getUrlEncoder().withoutPadding().encodeToString(hash); }
}
