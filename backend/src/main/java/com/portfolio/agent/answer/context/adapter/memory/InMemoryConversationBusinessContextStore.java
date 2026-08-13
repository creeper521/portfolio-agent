package com.portfolio.agent.answer.context.adapter.memory;

import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ContextSlot;
import com.portfolio.agent.answer.context.domain.ConversationContextEntry;
import com.portfolio.agent.answer.context.domain.ConversationContextMutation;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.context.gateway.ConversationBusinessContextStore;
import com.portfolio.agent.answer.context.service.ConversationContextCapacityPolicy;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Explicit test/local implementation; it must never be selected as production fallback. */
public final class InMemoryConversationBusinessContextStore
        implements ConversationBusinessContextStore {
    private final ConversationContextCapacityPolicy capacityPolicy;
    private final Map<ConversationId, Session> sessions = new HashMap<>();

    public InMemoryConversationBusinessContextStore() {
        this(ConversationContextCapacityPolicy.defaults());
    }

    public InMemoryConversationBusinessContextStore(ConversationContextCapacityPolicy capacityPolicy) {
        this.capacityPolicy = Objects.requireNonNull(capacityPolicy, "capacityPolicy");
    }

    @Override
    public synchronized void open(
            ConversationId conversationId, ResumeToken resumeToken, Instant now) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(resumeToken, "resumeToken");
        Objects.requireNonNull(now, "now");
        Session existing = sessions.get(conversationId);
        if (existing == null) {
            sessions.put(conversationId, new Session(resumeToken));
        } else if (!existing.resumeToken.equals(resumeToken)) {
            throw new IllegalArgumentException("Context session is not authorized");
        }
    }

    @Override
    public synchronized void rotateResumeToken(
            ConversationId conversationId, ResumeToken replacement, Instant now) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(now, "now");
        Session session = sessions.get(conversationId);
        if (session == null) {
            throw new IllegalArgumentException("Context session is unavailable");
        }
        session.resumeToken = replacement;
    }

    @Override
    public synchronized SaveResult save(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ConversationContextMutation mutation,
            Instant now) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(resumeToken, "resumeToken");
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(now, "now");
        capacityPolicy.requirePayloadSize(mutation.getPayloadBytes());

        Session session = sessions.get(conversationId);
        if (session == null) {
            session = new Session(resumeToken);
            sessions.put(conversationId, session);
        } else if (!session.resumeToken.equals(resumeToken)) {
            throw new IllegalArgumentException("Context session is not authorized");
        }
        purgeExpired(session, now);
        if (session.entries.containsKey(mutation.getContextHandle())) {
            throw new IllegalArgumentException("Context handle already exists");
        }
        if (mutation.getParentContextHandle() != null) {
            MutableEntry parent = session.entries.get(mutation.getParentContextHandle());
            if (parent == null || parent.isExpired(now)) {
                throw new IllegalArgumentException("parent Context is unavailable");
            }
        }
        ensureCapacity(session, now);

        MutableEntry mutable = new MutableEntry(
                conversationId, mutation, now,
                capacityPolicy.idleExpiresAt(now), capacityPolicy.absoluteExpiresAt(now));
        session.entries.put(mutable.contextHandle, mutable);

        boolean activeAdvanced = false;
        long activeRevision = currentRevision(session, mutation.getActiveSlot());
        if (mutation.getActiveSlot() != null) {
            long expectedRevision = mutation.getExpectedActiveRevision() == null
                    ? 0L : mutation.getExpectedActiveRevision();
            if (expectedRevision == activeRevision) {
                activeRevision++;
                session.active.put(
                        mutation.getActiveSlot(), new ActiveState(mutable.contextHandle, activeRevision));
                activeAdvanced = true;
            }
        }
        return new SaveResult(mutable.snapshot(), activeAdvanced, activeRevision);
    }

    @Override
    public synchronized Optional<ConversationContextEntry> resolve(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextHandle contextHandle,
            Instant now) {
        Session session = authorizedSession(conversationId, resumeToken);
        if (session == null) {
            return Optional.empty();
        }
        purgeExpired(session, now);
        MutableEntry entry = session.entries.get(contextHandle);
        if (entry == null || entry.isExpired(now)) {
            return Optional.empty();
        }
        entry.touch(now, capacityPolicy);
        return Optional.of(entry.snapshot());
    }

    @Override
    public synchronized List<ConversationContextEntry> list(
            ConversationId conversationId, ResumeToken resumeToken, Instant now) {
        Session session = authorizedSession(conversationId, resumeToken);
        if (session == null) {
            return List.of();
        }
        purgeExpired(session, now);
        return session.entries.values().stream()
                .map(MutableEntry::snapshot)
                .sorted(Comparator.comparing(ConversationContextEntry::getCreatedAt).reversed()
                        .thenComparing(entry -> entry.getContextHandle().asBase64Url()))
                .toList();
    }

    @Override
    public synchronized Optional<ActiveContext> active(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextSlot slot,
            Instant now) {
        Session session = authorizedSession(conversationId, resumeToken);
        if (session == null) {
            return Optional.empty();
        }
        purgeExpired(session, now);
        ActiveState active = session.active.get(slot);
        if (active == null || !session.entries.containsKey(active.contextHandle)) {
            return Optional.empty();
        }
        return Optional.of(new ActiveContext(slot, active.contextHandle, active.revision));
    }

    @Override
    public synchronized Optional<ConversationId> findConversation(ResumeToken resumeToken) {
        return sessions.entrySet().stream()
                .filter(entry -> entry.getValue().resumeToken.equals(resumeToken))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    @Override
    public synchronized Optional<ConversationContextEntry> resolve(
            ResumeToken resumeToken, ContextHandle contextHandle, Instant now) {
        return findConversation(resumeToken)
                .flatMap(conversationId -> resolve(conversationId, resumeToken, contextHandle, now));
    }

    @Override
    public synchronized void clear(ConversationId conversationId, ResumeToken resumeToken) {
        Session session = sessions.get(conversationId);
        if (session != null && session.resumeToken.equals(resumeToken)) {
            sessions.remove(conversationId);
        }
    }

    @Override
    public synchronized void clear(ResumeToken resumeToken) {
        findConversation(resumeToken).ifPresent(conversationId -> clear(conversationId, resumeToken));
    }

    private void ensureCapacity(Session session, Instant now) {
        if (session.entries.size() < capacityPolicy.getMaxContexts()) {
            return;
        }
        List<ConversationContextEntry> snapshots = session.entries.values().stream()
                .map(MutableEntry::snapshot).toList();
        Set<ContextHandle> activeHandles = new HashSet<>();
        for (ActiveState activeState : session.active.values()) {
            activeHandles.add(activeState.contextHandle);
        }
        List<ConversationContextEntry> candidates = capacityPolicy.pruneCandidatesByHandle(
                snapshots, activeHandles, session.entries.size() - capacityPolicy.getMaxContexts() + 1);
        if (candidates.isEmpty()) {
            throw new ContextCapacityExceededException();
        }
        for (ConversationContextEntry candidate : candidates) {
            session.entries.remove(candidate.getContextHandle());
        }
    }

    private void purgeExpired(Session session, Instant now) {
        List<ContextHandle> expired = session.entries.values().stream()
                .filter(entry -> entry.isExpired(now))
                .map(entry -> entry.contextHandle)
                .toList();
        for (ContextHandle handle : expired) {
            session.entries.remove(handle);
            session.active.entrySet().removeIf(entry -> entry.getValue().contextHandle.equals(handle));
        }
    }

    private long currentRevision(Session session, ContextSlot slot) {
        ActiveState active = slot == null ? null : session.active.get(slot);
        return active == null ? 0L : active.revision;
    }

    private Session authorizedSession(ConversationId conversationId, ResumeToken resumeToken) {
        Session session = sessions.get(conversationId);
        return session != null && session.resumeToken.equals(resumeToken) ? session : null;
    }

    private static final class Session {
        private ResumeToken resumeToken;
        private final Map<ContextHandle, MutableEntry> entries = new HashMap<>();
        private final EnumMap<ContextSlot, ActiveState> active = new EnumMap<>(ContextSlot.class);

        private Session(ResumeToken resumeToken) {
            this.resumeToken = resumeToken;
        }
    }

    private static final class ActiveState {
        private final ContextHandle contextHandle;
        private final long revision;

        private ActiveState(ContextHandle contextHandle, long revision) {
            this.contextHandle = contextHandle;
            this.revision = revision;
        }
    }

    private static final class MutableEntry {
        private final ConversationId conversationId;
        private final ContextHandle contextHandle;
        private final com.portfolio.agent.answer.context.domain.ConversationContextValue value;
        private final ContextHandle parentContextHandle;
        private final String sourceTaskId;
        private final int payloadBytes;
        private final Instant createdAt;
        private Instant lastAccessedAt;
        private Instant idleExpiresAt;
        private final Instant absoluteExpiresAt;

        private MutableEntry(
                ConversationId conversationId,
                ConversationContextMutation mutation,
                Instant createdAt,
                Instant idleExpiresAt,
                Instant absoluteExpiresAt) {
            this.conversationId = conversationId;
            this.contextHandle = mutation.getContextHandle();
            this.value = mutation.getValue();
            this.parentContextHandle = mutation.getParentContextHandle();
            this.sourceTaskId = mutation.getSourceTaskId();
            this.payloadBytes = mutation.getPayloadBytes();
            this.createdAt = createdAt;
            this.lastAccessedAt = createdAt;
            this.idleExpiresAt = idleExpiresAt;
            this.absoluteExpiresAt = absoluteExpiresAt;
        }

        private boolean isExpired(Instant now) {
            return !now.isBefore(idleExpiresAt) || !now.isBefore(absoluteExpiresAt);
        }

        private void touch(Instant now, ConversationContextCapacityPolicy policy) {
            if (now.isAfter(lastAccessedAt)) {
                lastAccessedAt = now;
            }
            Instant candidate = policy.idleExpiresAt(lastAccessedAt);
            idleExpiresAt = candidate.isAfter(absoluteExpiresAt) ? absoluteExpiresAt : candidate;
        }

        private ConversationContextEntry snapshot() {
            return new ConversationContextEntry(
                    conversationId, contextHandle, value, parentContextHandle, sourceTaskId,
                    payloadBytes, createdAt, lastAccessedAt, idleExpiresAt, absoluteExpiresAt);
        }
    }
}
