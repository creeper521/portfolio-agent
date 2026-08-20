package com.portfolio.agent.turn.continuation;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import com.portfolio.agent.turn.execution.TurnDeadline;

public interface ConversationSessionStore {
    Optional<Session> find(
            List<byte[]> tokenHashes, Instant now, TurnDeadline deadline);
    void save(Session session);

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
