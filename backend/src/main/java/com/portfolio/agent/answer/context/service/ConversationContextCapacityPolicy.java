package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.domain.ContextSlot;
import com.portfolio.agent.answer.context.domain.ConversationContextEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Fixed P3 retention and capacity policy. */
public final class ConversationContextCapacityPolicy {
    public static final int MAX_CONTEXTS_PER_CONVERSATION = 32;
    public static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    public static final Duration IDLE_TTL = Duration.ofHours(24);
    public static final Duration ABSOLUTE_TTL = Duration.ofDays(7);

    public static ConversationContextCapacityPolicy defaults() {
        return new ConversationContextCapacityPolicy(
                MAX_CONTEXTS_PER_CONVERSATION, MAX_PAYLOAD_BYTES, IDLE_TTL, ABSOLUTE_TTL);
    }

    private final int maxContexts;
    private final int maxPayloadBytes;
    private final Duration idleTtl;
    private final Duration absoluteTtl;

    public ConversationContextCapacityPolicy(
            int maxContexts, int maxPayloadBytes, Duration idleTtl, Duration absoluteTtl) {
        if (maxContexts < 1 || maxPayloadBytes < 1
                || idleTtl.isNegative() || idleTtl.isZero()
                || absoluteTtl.isNegative() || absoluteTtl.isZero()
                || idleTtl.compareTo(absoluteTtl) > 0) {
            throw new IllegalArgumentException("invalid Context capacity policy");
        }
        this.maxContexts = maxContexts;
        this.maxPayloadBytes = maxPayloadBytes;
        this.idleTtl = idleTtl;
        this.absoluteTtl = absoluteTtl;
    }

    public int getMaxContexts() { return maxContexts; }
    public int getMaxPayloadBytes() { return maxPayloadBytes; }
    public Duration getIdleTtl() { return idleTtl; }
    public Duration getAbsoluteTtl() { return absoluteTtl; }

    public Instant idleExpiresAt(Instant createdAt) {
        return createdAt.plus(idleTtl);
    }

    public Instant absoluteExpiresAt(Instant createdAt) {
        return createdAt.plus(absoluteTtl);
    }

    public void requirePayloadSize(int payloadBytes) {
        if (payloadBytes < 1 || payloadBytes > maxPayloadBytes) {
            throw new IllegalArgumentException("Context payload exceeds the fixed P3 bound");
        }
    }

    /**
     * Returns deterministic deletion candidates. Active entries are never returned.
     * Ordinary non-active entries are pruned before non-active recommendation entries.
     */
    public List<ConversationContextEntry> pruneCandidates(
            List<ConversationContextEntry> entries, Set<ContextSlot> activeSlots, int requiredCount) {
        Set<com.portfolio.agent.answer.context.domain.ContextHandle> activeHandles = new HashSet<>();
        for (ConversationContextEntry entry : entries) {
            if (isActive(entry, activeSlots)) {
                activeHandles.add(entry.getContextHandle());
            }
        }
        return pruneByHandle(entries, activeHandles, requiredCount);
    }

    public List<ConversationContextEntry> pruneCandidatesByHandle(
            List<ConversationContextEntry> entries,
            Set<com.portfolio.agent.answer.context.domain.ContextHandle> activeHandles,
            int requiredCount) {
        return pruneByHandle(entries, activeHandles, requiredCount);
    }

    private List<ConversationContextEntry> pruneByHandle(
            List<ConversationContextEntry> entries,
            Set<com.portfolio.agent.answer.context.domain.ContextHandle> activeHandles,
            int requiredCount) {
        if (requiredCount <= 0) {
            return List.of();
        }
        Comparator<ConversationContextEntry> oldestFirst = Comparator
                .comparing(ConversationContextEntry::getCreatedAt)
                .thenComparing(entry -> entry.getContextHandle().asBase64Url());
        List<ConversationContextEntry> ordinary = new ArrayList<>();
        List<ConversationContextEntry> recommendation = new ArrayList<>();
        for (ConversationContextEntry entry : entries) {
            if (activeHandles.contains(entry.getContextHandle())) {
                continue;
            }
            if (entry.getContextType()
                    == com.portfolio.agent.answer.context.domain.ConversationContextType.RECOMMENDATION) {
                recommendation.add(entry);
            } else {
                ordinary.add(entry);
            }
        }
        ordinary.sort(oldestFirst);
        recommendation.sort(oldestFirst);
        ordinary.addAll(recommendation);
        return List.copyOf(ordinary.subList(0, Math.min(requiredCount, ordinary.size())));
    }

    private boolean isActive(ConversationContextEntry entry, Set<ContextSlot> activeSlots) {
        return activeSlots.contains(slotForType(entry));
    }

    private ContextSlot slotForType(ConversationContextEntry entry) {
        if (entry.getContextType()
                == com.portfolio.agent.answer.context.domain.ConversationContextType.RECOMMENDATION) {
            return ContextSlot.ACTIVE_RECOMMENDATION;
        }
        return entry.getValue().getRecentSemanticTaskContext().getTaskType()
                == com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_COMPARE
                ? ContextSlot.ACTIVE_COMPARE_CONTEXT
                : ContextSlot.ACTIVE_FACT_CONTEXT;
    }
}
