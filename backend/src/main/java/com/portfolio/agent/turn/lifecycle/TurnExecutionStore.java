package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.continuation.DiscussionStateMutation;
import com.portfolio.agent.turn.continuation.ClarificationSettlementMutation;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.execution.TurnDeadline;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TurnExecutionStore {
    ClaimResult claim(
            UUID requestId, String conversationId, RequestFingerprintSet fingerprints,
            SessionAccess sessionAccess, Instant now, Duration leaseDuration,
            TurnDeadline deadline);
    boolean complete(
            UUID requestId, byte[] requestFingerprint, PublicAgentTurn publicSnapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess, Instant completedAt,
            TurnDeadline deadline);
    default boolean complete(
            UUID requestId, byte[] requestFingerprint,
            PublicAgentTurn publicSnapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess, Instant completedAt,
            TurnDeadline deadline,
            DiscussionStateMutation discussionMutation) {
        if (discussionMutation.isNone()) {
            return complete(
                    requestId, requestFingerprint, publicSnapshot,
                    contexts, challenges, sessionToCreate,
                    sessionAccess, completedAt, deadline);
        }
        throw new UnsupportedOperationException(
                "discussion settlement is unavailable");
    }
    default boolean complete(
            UUID requestId, byte[] requestFingerprint,
            PublicAgentTurn publicSnapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess, Instant completedAt,
            TurnDeadline deadline,
            DiscussionStateMutation discussionMutation,
            ClarificationSettlementMutation clarificationMutation) {
        if (clarificationMutation.isNone()) {
            return complete(
                    requestId, requestFingerprint, publicSnapshot,
                    contexts, challenges, sessionToCreate, sessionAccess,
                    completedAt, deadline, discussionMutation);
        }
        throw new UnsupportedOperationException(
                "clarification settlement is unavailable");
    }
    default SettlementResult completeWithSession(
            UUID requestId, byte[] requestFingerprint,
            PublicAgentTurn publicSnapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess, Instant completedAt,
            TurnDeadline deadline,
            DiscussionStateMutation discussionMutation,
            ClarificationSettlementMutation clarificationMutation) {
        return new SettlementResult(complete(
                requestId, requestFingerprint, publicSnapshot,
                contexts, challenges, sessionToCreate, sessionAccess,
                completedAt, deadline, discussionMutation,
                clarificationMutation), null);
    }
    boolean cancel(UUID requestId, String conversationId, Instant cancelledAt);
    Optional<TurnExecutionRecord> find(UUID requestId);

    boolean clearConversation(
            String conversationId, byte[] tokenHash, Instant clearedAt);

    record SessionAccess(
            String conversationId, byte[] tokenHash,
            ConversationSessionStore.Session tentativeSession) {
        public SessionAccess {
            tokenHash = tokenHash == null ? null : tokenHash.clone();
            if (tokenHash != null && tentativeSession != null) {
                throw new IllegalArgumentException("session access is ambiguous");
            }
            if (tentativeSession != null) conversationId = tentativeSession.conversationId();
            if (!isBlankOrNull(conversationId) && conversationId.length() > 128) {
                throw new IllegalArgumentException("conversationId is invalid");
            }
            if (tokenHash == null && tentativeSession == null) {
                throw new IllegalArgumentException("session access is required");
            }
        }
        public static SessionAccess authenticated(String conversationId, byte[] tokenHash) {
            return new SessionAccess(java.util.Objects.requireNonNull(conversationId),
                    java.util.Objects.requireNonNull(tokenHash), null);
        }
        public static SessionAccess tentative(ConversationSessionStore.Session session) {
            return new SessionAccess(session.conversationId(), null,
                    java.util.Objects.requireNonNull(session));
        }
        @Override public byte[] tokenHash() { return tokenHash == null ? null : tokenHash.clone(); }
        private static boolean isBlankOrNull(String value) {
            return value == null || value.isBlank();
        }
    }

    record ClaimResult(
            Status status, PublicAgentTurn replay,
            long retryAfterSeconds,
            ConversationSessionStore.Session sessionSnapshot) {
        public enum Status { CLAIMED, REPLAY, IN_PROGRESS, CONFLICT, CANCELLED }
        public static ClaimResult claimed() { return new ClaimResult(Status.CLAIMED, null, 0, null); }
        public static ClaimResult replay(PublicAgentTurn replay) { return replay(replay, null); }
        public static ClaimResult replay(
                PublicAgentTurn replay,
                ConversationSessionStore.Session sessionSnapshot) {
            return new ClaimResult(Status.REPLAY, replay, 0, sessionSnapshot);
        }
        public static ClaimResult state(Status status) { return new ClaimResult(status, null, 0, null); }
        public static ClaimResult inProgress(long retryAfter) {
            return new ClaimResult(
                    Status.IN_PROGRESS, null,
                    Math.max(1, retryAfter), null);
        }
    }
    record SettlementResult(
            boolean completed,
            ConversationSessionStore.Session sessionSnapshot) { }
}
