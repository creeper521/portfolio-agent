package com.portfolio.agent.turn.state.memory;

import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import com.portfolio.agent.turn.lifecycle.TurnExecutionRecord;
import com.portfolio.agent.turn.lifecycle.TurnExecutionStore;
import com.portfolio.agent.turn.lifecycle.AgentStateStore;
import com.portfolio.agent.turn.projection.PublicAgentTurn;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Local/test implementation with the same single terminal gate as the JDBC store. */
public final class InMemoryTurnExecutionStore implements AgentStateStore {
    private final ConcurrentHashMap<UUID, TurnExecutionRecord> records = new ConcurrentHashMap<>();
    private final ClarificationStore clarificationStore;

    public InMemoryTurnExecutionStore() {
        this(new ClarificationStore(
                java.time.Clock.systemUTC(), java.time.Duration.ofMinutes(5)));
    }
    public InMemoryTurnExecutionStore(ClarificationStore clarificationStore) {
        this.clarificationStore = clarificationStore;
    }

    @Override public ClaimResult claim(
            UUID requestId, String conversationId, byte[] fingerprint,
            Instant now, Duration leaseDuration) {
        AtomicReference<ClaimResult> result = new AtomicReference<>();
        records.compute(requestId, (key, existing) -> {
            if (existing == null) {
                result.set(ClaimResult.claimed());
                return TurnExecutionRecord.claimed(
                        requestId, conversationId, fingerprint, now.plus(leaseDuration));
            }
            if (!existing.getConversationId().equals(conversationId)
                    || !MessageDigest.isEqual(existing.getRequestFingerprint(), fingerprint)) {
                result.set(ClaimResult.state(ClaimResult.Status.CONFLICT));
                return existing;
            }
            if (existing.getStatus() == TurnExecutionRecord.Status.COMPLETED) {
                result.set(ClaimResult.replay(existing.getPublicSnapshot()));
                return existing;
            }
            if (existing.getStatus() == TurnExecutionRecord.Status.CANCELLED) {
                result.set(ClaimResult.state(ClaimResult.Status.CANCELLED));
                return existing;
            }
            if (now.isBefore(existing.getLeaseExpiresAt())) {
                long seconds = Math.max(1, Duration.between(now, existing.getLeaseExpiresAt()).toSeconds());
                result.set(ClaimResult.inProgress(seconds));
                return existing;
            }
            result.set(ClaimResult.claimed());
            return TurnExecutionRecord.claimed(
                    requestId, conversationId, fingerprint, now.plus(leaseDuration));
        });
        return result.get();
    }

    @Override public synchronized boolean complete(
            UUID requestId, byte[] fingerprint, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate, Instant completedAt) {
        AtomicBoolean completed = new AtomicBoolean();
        records.computeIfPresent(requestId, (key, existing) -> {
            if (existing.getStatus() != TurnExecutionRecord.Status.CLAIMED
                    || !MessageDigest.isEqual(existing.getRequestFingerprint(), fingerprint)) {
                return existing;
            }
            completed.set(true);
            challenges.forEach(clarificationStore::save);
            return existing.completed(snapshot, contexts, challenges, completedAt);
        });
        return completed.get();
    }

    @Override public boolean cancel(UUID requestId, String conversationId, Instant cancelledAt) {
        AtomicBoolean cancelled = new AtomicBoolean();
        records.computeIfPresent(requestId, (key, existing) -> {
            if (existing.getStatus() != TurnExecutionRecord.Status.CLAIMED
                    || !existing.getConversationId().equals(conversationId)) return existing;
            cancelled.set(true);
            return existing.cancelled(cancelledAt);
        });
        return cancelled.get();
    }

    @Override public Optional<TurnExecutionRecord> find(UUID requestId) {
        return Optional.ofNullable(records.get(requestId));
    }
    @Override public void clearConversation(String conversationId) {
        records.entrySet().removeIf(value ->
                value.getValue().getConversationId().equals(conversationId));
        clarificationStore.clear(conversationId);
    }
    @Override public Optional<ContinuationContext> findContext(
            String conversationId, String contextHandle, Instant now) {
        return records.values().stream()
                .filter(value -> value.getStatus() == TurnExecutionRecord.Status.COMPLETED)
                .filter(value -> value.getConversationId().equals(conversationId))
                .flatMap(value -> value.getContexts().stream())
                .filter(value -> value.getContextHandle().equals(contextHandle))
                .filter(value -> now.isBefore(value.getExpiresAt())).findFirst();
    }
    @Override public ClarificationStore.ConsumeResult consumeClarification(
            String clarificationId, String conversationId, byte[] resumeTokenHash,
            String currentContentReleaseId,
            ClarificationStore.ClarificationAnswer answer, Instant now) {
        return clarificationStore.consume(
                clarificationId, conversationId, resumeTokenHash,
                currentContentReleaseId, answer);
    }
}
