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
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.portfolio.agent.turn.lifecycle.RequestFingerprintSet;
import com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore;

/** Local/test implementation with the same single terminal gate as the JDBC store. */
public final class InMemoryTurnExecutionStore implements AgentStateStore {
    private final ConcurrentHashMap<UUID, TurnExecutionRecord> records = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Instant> absoluteExpiries = new ConcurrentHashMap<>();
    private final ClarificationStore clarificationStore;
    private final Duration absoluteTtl;
    private final InMemoryConversationSessionStore sessionStore;
    private final Clock clock;

    public InMemoryTurnExecutionStore() {
        this(new ClarificationStore(
                java.time.Clock.systemUTC(), java.time.Duration.ofMinutes(5)),
                Duration.ofMinutes(30), new InMemoryConversationSessionStore(), Clock.systemUTC());
    }
    public InMemoryTurnExecutionStore(ClarificationStore clarificationStore) {
        this(clarificationStore, Duration.ofMinutes(30),
                new InMemoryConversationSessionStore(), Clock.systemUTC());
    }
    public InMemoryTurnExecutionStore(
            ClarificationStore clarificationStore, Duration absoluteTtl) {
        this(clarificationStore, absoluteTtl,
                new InMemoryConversationSessionStore(), Clock.systemUTC());
    }
    public InMemoryTurnExecutionStore(
            ClarificationStore clarificationStore, Duration absoluteTtl,
            InMemoryConversationSessionStore sessionStore, Clock clock) {
        this.clarificationStore = clarificationStore;
        if (absoluteTtl == null || absoluteTtl.isZero() || absoluteTtl.isNegative()) {
            throw new IllegalArgumentException("absoluteTtl is invalid");
        }
        this.absoluteTtl = absoluteTtl;
        this.sessionStore = java.util.Objects.requireNonNull(sessionStore, "sessionStore");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override public synchronized ClaimResult claim(
            UUID requestId, String conversationId, RequestFingerprintSet fingerprints,
            SessionAccess sessionAccess, Instant now, Duration leaseDuration,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        if (deadline.isExpired()) {
            throw new IllegalStateException("agent claim deadline exceeded");
        }
        if (!authorizeSession(conversationId, sessionAccess, now)) {
            return ClaimResult.state(ClaimResult.Status.CANCELLED);
        }
        AtomicReference<ClaimResult> result = new AtomicReference<>();
        records.compute(requestId, (key, existing) -> {
            Instant absoluteExpiry = absoluteExpiries.get(requestId);
            if (existing != null && absoluteExpiry != null && !now.isBefore(absoluteExpiry)) {
                absoluteExpiries.remove(requestId);
                existing = null;
            }
            if (existing == null) {
                result.set(ClaimResult.claimed());
                absoluteExpiries.put(requestId, now.plus(absoluteTtl));
                return TurnExecutionRecord.claimed(
                        requestId, conversationId, fingerprints.current(),
                        fingerprints.currentKeyId(), now.plus(leaseDuration));
            }
            if (!existing.getConversationId().equals(conversationId)
                    || !fingerprints.matches(existing.getRequestFingerprint())) {
                result.set(ClaimResult.state(ClaimResult.Status.CONFLICT));
                return existing;
            }
            if (existing.getStatus() == TurnExecutionRecord.Status.COMPLETED) {
                result.set(ClaimResult.replay(existing.getPublicSnapshot()));
                List<ClarificationStore.Record> rebound = existing.getChallenges();
                if (sessionAccess.tentativeSession() != null) {
                    byte[] newTokenHash = sessionAccess.tentativeSession().tokenHash();
                    clarificationStore.rebindLiveChallenges(
                            conversationId, newTokenHash, now, 32);
                    sessionStore.save(sessionAccess.tentativeSession());
                    rebound = existing.getChallenges().stream()
                            .map(challenge -> rebind(challenge, newTokenHash)).toList();
                }
                return TurnExecutionRecord.restore(
                        existing.getRequestId(), existing.getConversationId(),
                        fingerprints.current(), fingerprints.currentKeyId(),
                        TurnExecutionRecord.Status.COMPLETED,
                        existing.getLeaseExpiresAt(), existing.getPublicSnapshot(),
                        existing.getContexts(), rebound, existing.getTerminalAt());
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
                    requestId, conversationId, fingerprints.current(),
                    fingerprints.currentKeyId(), now.plus(leaseDuration));
        });
        return result.get();
    }

    @Override public synchronized boolean complete(
            UUID requestId, byte[] fingerprint, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate, SessionAccess sessionAccess,
            Instant completedAt,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        return complete(
                requestId, fingerprint, snapshot, contexts, challenges,
                sessionToCreate, sessionAccess, completedAt, deadline,
                com.portfolio.agent.turn.continuation.DiscussionStateMutation.none());
    }

    @Override public synchronized boolean complete(
            UUID requestId, byte[] fingerprint, PublicAgentTurn snapshot,
            List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges,
            ConversationSessionStore.Session sessionToCreate,
            SessionAccess sessionAccess,
            Instant completedAt,
            com.portfolio.agent.turn.execution.TurnDeadline deadline,
            com.portfolio.agent.turn.continuation.DiscussionStateMutation discussionMutation) {
        if (deadline.isExpired()) return false;
        if (!authorizeSettlementSession(sessionAccess, sessionToCreate, completedAt)) return false;
        TurnExecutionRecord existing = records.get(requestId);
        Instant absoluteExpiry = absoluteExpiries.get(requestId);
        if (existing == null
                || existing.getStatus() != TurnExecutionRecord.Status.CLAIMED
                || !MessageDigest.isEqual(
                existing.getRequestFingerprint(), fingerprint)
                || absoluteExpiry == null
                || !completedAt.isBefore(absoluteExpiry)) {
            return false;
        }
        if (sessionToCreate != null) sessionStore.save(sessionToCreate);
        byte[] tokenHash = sessionAccess.tokenHash() != null
                ? sessionAccess.tokenHash()
                : sessionToCreate.tokenHash();
        if (!sessionStore.applyDiscussionMutation(
                existing.getConversationId(), tokenHash,
                discussionMutation, completedAt)) {
            return false;
        }
        clarificationStore.saveAllAtomically(challenges);
        records.put(requestId, existing.completed(
                snapshot, contexts, challenges, completedAt));
        return true;
    }

    @Override public synchronized boolean cancel(
            UUID requestId, String conversationId, Instant cancelledAt) {
        AtomicBoolean cancelled = new AtomicBoolean();
        records.computeIfPresent(requestId, (key, existing) -> {
            if (existing.getStatus() != TurnExecutionRecord.Status.CLAIMED
                    || !existing.getConversationId().equals(conversationId)
                    || !cancelledAt.isBefore(absoluteExpiries.get(requestId))) return existing;
            cancelled.set(true);
            return existing.cancelled(cancelledAt);
        });
        return cancelled.get();
    }

    @Override public Optional<TurnExecutionRecord> find(UUID requestId) {
        TurnExecutionRecord record = records.get(requestId);
        Instant expiresAt = absoluteExpiries.get(requestId);
        return record == null || expiresAt == null || !clock.instant().isBefore(expiresAt)
                ? Optional.empty() : Optional.of(record);
    }
    @Override public synchronized boolean clearConversation(
            String conversationId, byte[] tokenHash, Instant clearedAt) {
        if (!sessionStore.revokeIfMatches(conversationId, tokenHash, clearedAt)) return false;
        records.entrySet().removeIf(value ->
                value.getValue().getConversationId().equals(conversationId));
        absoluteExpiries.keySet().removeIf(requestId -> !records.containsKey(requestId));
        clarificationStore.clear(conversationId);
        return true;
    }
    @Override public Optional<ContinuationContext> findContext(
            String conversationId, String contextHandle, Instant now,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        if (deadline.isExpired()) throw new IllegalStateException("agent state deadline exceeded");
        return records.values().stream()
                .filter(value -> value.getStatus() == TurnExecutionRecord.Status.COMPLETED)
                .filter(value -> value.getConversationId().equals(conversationId))
                .filter(value -> now.isBefore(absoluteExpiries.get(value.getRequestId())))
                .flatMap(value -> value.getContexts().stream())
                .filter(value -> value.getContextHandle().equals(contextHandle))
                .filter(value -> now.isBefore(value.getExpiresAt())).findFirst();
    }
    @Override public ClarificationStore.ConsumeResult consumeClarification(
            String clarificationId, String conversationId, byte[] resumeTokenHash,
            String currentContentReleaseId,
            ClarificationStore.ClarificationAnswer answer, Instant now,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        if (deadline.isExpired()) throw new IllegalStateException("agent state deadline exceeded");
        return clarificationStore.consume(
                clarificationId, conversationId, resumeTokenHash,
                currentContentReleaseId, answer);
    }

    public CleanupResult cleanup(Instant now, int limit) {
        if (limit < 1) return new CleanupResult(0, 0, 0);
        int executions = 0;
        for (java.util.Map.Entry<UUID, Instant> entry : absoluteExpiries.entrySet()) {
            if (executions >= limit) break;
            if (!now.isBefore(entry.getValue())
                    && absoluteExpiries.remove(entry.getKey(), entry.getValue())) {
                records.remove(entry.getKey());
                executions++;
            }
        }
        int challenges = clarificationStore.cleanup(now, limit - executions);
        int sessions = sessionStore.cleanup(now, limit - executions - challenges);
        return new CleanupResult(executions, challenges, sessions);
    }

    public record CleanupResult(int executions, int challenges, int sessions) {
        public int total() { return executions + challenges + sessions; }
    }

    private boolean authorizeSession(
            String conversationId, SessionAccess access, Instant now) {
        if (!conversationId.equals(access.conversationId())) return false;
        if (access.tentativeSession() != null) {
            return sessionStore.authorizeTentative(access.tentativeSession(), now);
        }
        return sessionStore.authorize(conversationId, access.tokenHash(), now);
    }

    private boolean authorizeSettlementSession(
            SessionAccess access, ConversationSessionStore.Session sessionToCreate,
            Instant now) {
        if (access.tentativeSession() != null) {
            return sessionToCreate != null
                    && sessionToCreate.conversationId().equals(access.conversationId())
                    && sessionStore.authorizeTentative(sessionToCreate, now);
        }
        return sessionToCreate == null
                && sessionStore.authorize(access.conversationId(), access.tokenHash(), now);
    }

    private ClarificationStore.Record rebind(
            ClarificationStore.Record current, byte[] tokenHash) {
        return new ClarificationStore.Record(
                current.conversationId(), tokenHash, current.contentReleaseId(),
                current.challenge(), current.choiceBindings(), current.textBindings(),
                current.resumeTemplate());
    }
}
