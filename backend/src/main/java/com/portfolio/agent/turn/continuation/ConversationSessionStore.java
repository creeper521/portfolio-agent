package com.portfolio.agent.turn.continuation;

import java.time.Instant;
import java.util.Optional;

public interface ConversationSessionStore {
    Optional<Session> find(byte[] tokenHash, Instant now);
    void save(Session session);
    void revoke(String conversationId);

    record Session(
            String conversationId, byte[] tokenHash,
            Instant createdAt, Instant expiresAt) {
        public Session {
            conversationId = ContinuationContext.text(conversationId, "conversationId");
            tokenHash = tokenHash.clone();
            if (!createdAt.isBefore(expiresAt)) throw new IllegalArgumentException("session expiry is invalid");
        }
        @Override public byte[] tokenHash() { return tokenHash.clone(); }
    }
}
