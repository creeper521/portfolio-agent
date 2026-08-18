package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.projection.PublicAgentTurn;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TurnExecutionStore {
    ClaimResult claim(
            UUID requestId, String conversationId, byte[] requestFingerprint,
            Instant now, Duration leaseDuration);
    boolean complete(
            UUID requestId, byte[] requestFingerprint, PublicAgentTurn publicSnapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            Instant completedAt);
    boolean cancel(UUID requestId, String conversationId, Instant cancelledAt);
    Optional<TurnExecutionRecord> find(UUID requestId);
    void clearConversation(String conversationId);

    record ClaimResult(Status status, PublicAgentTurn replay, long retryAfterSeconds) {
        public enum Status { CLAIMED, REPLAY, IN_PROGRESS, CONFLICT, CANCELLED }
        public static ClaimResult claimed() { return new ClaimResult(Status.CLAIMED, null, 0); }
        public static ClaimResult replay(PublicAgentTurn replay) { return new ClaimResult(Status.REPLAY, replay, 0); }
        public static ClaimResult state(Status status) { return new ClaimResult(status, null, 0); }
        public static ClaimResult inProgress(long retryAfter) {
            return new ClaimResult(Status.IN_PROGRESS, null, Math.max(1, retryAfter));
        }
    }
}
