package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.codec.ConversationContextCodecRegistry;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ContextSlot;
import com.portfolio.agent.answer.context.domain.ConversationContextEntry;
import com.portfolio.agent.answer.context.domain.ConversationContextMutation;
import com.portfolio.agent.answer.context.domain.ConversationContextValue;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RecentSemanticTaskContext;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConversationContextCapacityPolicyTest {
    private static final Instant CREATED = Instant.parse("2026-08-12T04:00:00Z");

    @Test
    void exposesFixedBoundsAndRejectsOversizedPayload() {
        ConversationContextCapacityPolicy policy = ConversationContextCapacityPolicy.defaults();

        assertEquals(32, policy.getMaxContexts());
        assertEquals(16 * 1024, policy.getMaxPayloadBytes());
        assertEquals(Instant.parse("2026-08-13T04:00:00Z"), policy.idleExpiresAt(CREATED));
        assertEquals(Instant.parse("2026-08-19T04:00:00Z"), policy.absoluteExpiresAt(CREATED));
        assertThrows(IllegalArgumentException.class,
                () -> policy.requirePayloadSize(16 * 1024 + 1));
    }

    @Test
    void prunesOrdinaryBeforeRecommendationAndNeverActive() {
        ConversationContextCapacityPolicy policy = new ConversationContextCapacityPolicy(
                2, 16 * 1024, policyDurationHours(24), policyDurationHours(168));
        ConversationContextEntry active = entry(CREATED, ContextHandle.issue(), "ACTIVE");
        ConversationContextEntry ordinary = entry(CREATED.plusSeconds(1), ContextHandle.issue(), "ORDINARY");
        ConversationContextEntry recommendation = entry(CREATED.plusSeconds(2), ContextHandle.issue(), "RECOMMENDATION");

        List<ConversationContextEntry> candidates = policy.pruneCandidatesByHandle(
                List.of(active, ordinary, recommendation),
                Set.of(active.getContextHandle()), 1);

        assertEquals(List.of(ordinary.getContextHandle()),
                candidates.stream().map(ConversationContextEntry::getContextHandle).toList());
    }

    private static ConversationContextEntry entry(Instant created, ContextHandle handle, String taskId) {
        RecentSemanticTaskContext context = new RecentSemanticTaskContext(
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                List.of(SubjectReference.project("agent", "v1")), Set.of("OVERVIEW"), Set.of(), "v1", taskId);
        ConversationContextMutation mutation = new ConversationContextMutation(
                handle, ConversationContextValue.recentSemanticTask(context), null, taskId, 1,
                taskId.equals("ACTIVE") ? ContextSlot.ACTIVE_FACT_CONTEXT : null, 0L);
        return new ConversationContextEntry(
                ConversationId.random(), handle, mutation.getValue(), null, taskId, 1,
                created, created, created.plusSeconds(3600), created.plusSeconds(7200));
    }

    private static java.time.Duration policyDurationHours(long hours) {
        return java.time.Duration.ofHours(hours);
    }
}
