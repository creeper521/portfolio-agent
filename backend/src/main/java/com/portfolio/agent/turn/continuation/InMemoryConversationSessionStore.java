package com.portfolio.agent.turn.continuation;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryConversationSessionStore implements ConversationSessionStore {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> revokedConversations =
            new ConcurrentHashMap<>();
    @Override public Optional<Session> find(
            java.util.List<byte[]> tokenHashes, Instant now,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        if (deadline.isExpired()) throw new IllegalStateException("session lookup deadline exceeded");
        for (byte[] tokenHash : tokenHashes) {
            Session session = sessions.get(key(tokenHash));
            if (session != null && !revokedConversations.containsKey(session.conversationId())
                    && now.isBefore(session.expiresAt())) return Optional.of(session);
        }
        return Optional.empty();
    }
    @Override public synchronized void save(Session session) {
        Session existing = sessions.values().stream()
                .filter(value -> value.conversationId().equals(session.conversationId()))
                .findFirst().orElse(null);
        Instant revokedAt = revokedConversations.get(session.conversationId());
        boolean replacement = existing != null
                && !session.createdAt().isBefore(existing.expiresAt())
                && !java.security.MessageDigest.isEqual(
                session.tokenHash(), existing.tokenHash());
        if (revokedAt != null && !replacement) return;
        Session effective = existing == null ? session
                : replacement ? new Session(
                session.conversationId(), session.tokenHash(),
                session.createdAt(), session.expiresAt(), null,
                existing.discussionRevision() + 1)
                : new Session(
                session.conversationId(), session.tokenHash(),
                existing.createdAt(), existing.expiresAt(),
                existing.activeDiscussion().orElse(null),
                existing.discussionRevision());
        sessions.entrySet().removeIf(value ->
                value.getValue().conversationId().equals(session.conversationId()));
        if (replacement) revokedConversations.remove(session.conversationId());
        sessions.put(key(effective.tokenHash()), effective);
    }
    synchronized void revokeForTest(String conversationId) {
        revokedConversations.put(conversationId, Instant.now());
    }
    public synchronized boolean authorize(
            String conversationId, byte[] tokenHash, Instant now) {
        if (revokedConversations.containsKey(conversationId)) return false;
        Session session = sessions.values().stream()
                .filter(value -> value.conversationId().equals(conversationId))
                .findFirst().orElse(null);
        return session != null && now.isBefore(session.expiresAt())
                && java.security.MessageDigest.isEqual(session.tokenHash(), tokenHash);
    }
    public synchronized boolean authorizeTentative(Session tentative, Instant now) {
        Session existing = sessions.values().stream()
                .filter(value -> value.conversationId().equals(tentative.conversationId()))
                .findFirst().orElse(null);
        Instant revokedAt = revokedConversations.get(tentative.conversationId());
        if (revokedAt != null && existing != null
                && now.isBefore(existing.expiresAt())) return false;
        return existing == null || now.isBefore(existing.expiresAt())
                || !tentative.createdAt().isBefore(existing.expiresAt())
                && !java.security.MessageDigest.isEqual(
                tentative.tokenHash(), existing.tokenHash());
    }
    public synchronized boolean revokeIfMatches(
            String conversationId, byte[] tokenHash, Instant clearedAt) {
        if (!authorize(conversationId, tokenHash, clearedAt)) return false;
        revokedConversations.put(conversationId, clearedAt);
        return true;
    }
    public synchronized boolean applyDiscussionMutation(
            String conversationId, byte[] tokenHash,
            DiscussionStateMutation mutation, Instant now) {
        if (mutation.isNone()) return true;
        if (!authorize(conversationId, tokenHash, now)) return false;
        java.util.Map.Entry<String, Session> entry = sessions.entrySet().stream()
                .filter(value -> value.getValue().conversationId()
                        .equals(conversationId))
                .findFirst().orElse(null);
        if (entry == null) return false;
        Session currentSession = entry.getValue();
        ActiveDiscussionPointer current =
                currentSession.activeDiscussion().orElse(null);
        if (!mutation.matches(current)) return false;
        Session updated = new Session(
                currentSession.conversationId(),
                currentSession.tokenHash(),
                currentSession.createdAt(),
                currentSession.expiresAt(),
                mutation.result(current),
                currentSession.discussionRevision()
                        + (mutation.getKind()
                        == DiscussionStateMutation.Kind.REPLACE
                        || mutation.getKind()
                        == DiscussionStateMutation.Kind.CLEAR ? 1 : 0));
        sessions.put(entry.getKey(), updated);
        return true;
    }
    public synchronized int cleanup(Instant now, int limit) {
        if (limit < 1) return 0;
        int removed = 0;
        for (java.util.Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (removed >= limit) break;
            if (!now.isBefore(entry.getValue().expiresAt())
                    && sessions.remove(entry.getKey(), entry.getValue())) {
                revokedConversations.remove(entry.getValue().conversationId());
                removed++;
            }
        }
        return removed;
    }
    private String key(byte[] hash) { return Base64.getUrlEncoder().withoutPadding().encodeToString(hash); }
}
