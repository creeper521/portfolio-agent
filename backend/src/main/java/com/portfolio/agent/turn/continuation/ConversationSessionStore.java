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
            Instant createdAt, Instant expiresAt,
            ActiveDiscussionPointer activeDiscussionPointer,
            long discussionRevision,
            ConversationSemanticState semanticState) {
        public Session(
                String conversationId, byte[] tokenHash,
                Instant createdAt, Instant expiresAt) {
            this(conversationId, tokenHash, createdAt, expiresAt, null, 0, null);
        }
        public Session(
                String conversationId, byte[] tokenHash,
                Instant createdAt, Instant expiresAt,
                ActiveDiscussionPointer activeDiscussionPointer) {
            this(conversationId, tokenHash, createdAt, expiresAt,
                    activeDiscussionPointer, 0, null);
        }
        public Session(
                String conversationId, byte[] tokenHash,
                Instant createdAt, Instant expiresAt,
                ActiveDiscussionPointer activeDiscussionPointer,
                long discussionRevision) {
            this(conversationId, tokenHash, createdAt, expiresAt,
                    activeDiscussionPointer, discussionRevision, null);
        }
        public Session {
            conversationId = ContinuationContext.text(conversationId, "conversationId");
            tokenHash = tokenHash.clone();
            if (!createdAt.isBefore(expiresAt)) throw new IllegalArgumentException("session expiry is invalid");
            if (discussionRevision < 0) {
                throw new IllegalArgumentException(
                        "discussionRevision must not be negative");
            }
        }
        @Override public byte[] tokenHash() { return tokenHash.clone(); }
        public Optional<ActiveDiscussionPointer> activeDiscussion() {
            return Optional.ofNullable(activeDiscussionPointer);
        }
        public Optional<ConversationSemanticState> semanticStateOptional() {
            return Optional.ofNullable(semanticState);
        }
    }
}
