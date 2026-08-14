package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.adapter.memory.InMemoryConversationBusinessContextStore;
import com.portfolio.agent.answer.context.codec.ConversationContextCodecRegistry;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ContextSlot;
import com.portfolio.agent.answer.context.domain.ConversationContextMutation;
import com.portfolio.agent.answer.context.domain.ConversationContextResolution;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.ConversationContextValue;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RecentSemanticTaskContext;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationContextResolverTest {
    private static final Instant START = Instant.parse("2026-08-12T04:00:00Z");

    @Test
    void explicitHandleWinsAndNeverFallsBackWhenTypeIsWrong() {
        ConversationId conversationId = ConversationId.random();
        ResumeToken token = ResumeToken.issue();
        InMemoryConversationBusinessContextStore store = new InMemoryConversationBusinessContextStore();
        ConversationContextMutation mutation = factory().create(
                ConversationContextValue.recentSemanticTask(recent(
                        SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT, "fact")),
                null, "fact", ContextSlot.ACTIVE_FACT_CONTEXT, 0L);
        store.save(conversationId, token, mutation, START);
        ConversationContextResolver resolver = new ConversationContextResolver(store);

        ConversationContextResolution resolved = resolver.resolve(
                conversationId, token, mutation.getContextHandle(),
                new ConversationContextLookupCriteria(
                        ConversationContextType.RECENT_SEMANTIC_TASK,
                        SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT), START.plusSeconds(1));
        ConversationContextResolution incompatible = resolver.resolve(
                conversationId, token, mutation.getContextHandle(),
                ConversationContextType.RECOMMENDATION, START.plusSeconds(1));

        assertEquals(ConversationContextResolution.Status.RESOLVED, resolved.getStatus());
        assertEquals(ConversationContextResolution.SelectionReason.EXPLICIT_HANDLE,
                resolved.getSelectionReason());
        assertEquals(ConversationContextResolution.Status.INCOMPATIBLE, incompatible.getStatus());
    }

    @Test
    void choosesUniqueActiveThenMostRecentActiveAndClarifiesTies() {
        ConversationId conversationId = ConversationId.random();
        ResumeToken token = ResumeToken.issue();
        InMemoryConversationBusinessContextStore store = new InMemoryConversationBusinessContextStore();
        ConversationContextMutation fact = factory().create(
                ConversationContextValue.recentSemanticTask(recent(
                        SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT, "fact")),
                null, "fact", ContextSlot.ACTIVE_FACT_CONTEXT, 0L);
        store.save(conversationId, token, fact, START);
        ConversationContextResolver resolver = new ConversationContextResolver(store);

        ConversationContextResolution unique = resolver.resolve(
                conversationId, token, null,
                ConversationContextType.RECENT_SEMANTIC_TASK, START.plusSeconds(1));
        ConversationContextMutation compare = factory().create(
                ConversationContextValue.recentSemanticTask(recent(
                        SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_COMPARE, "compare")),
                null, "compare", ContextSlot.ACTIVE_COMPARE_CONTEXT, 0L);
        store.save(conversationId, token, compare, START.plusSeconds(2));
        ConversationContextResolution latest = resolver.resolve(
                conversationId, token, null,
                ConversationContextType.RECENT_SEMANTIC_TASK, START.plusSeconds(3));

        assertEquals(ConversationContextResolution.SelectionReason.UNIQUE_ACTIVE,
                unique.getSelectionReason());
        assertEquals(compare.getContextHandle(), latest.getEntry().orElseThrow().getContextHandle());
        assertEquals(ConversationContextResolution.SelectionReason.MOST_RECENT_ACTIVE,
                latest.getSelectionReason());

        InMemoryConversationBusinessContextStore tiedStore = new InMemoryConversationBusinessContextStore();
        ConversationContextMutation tiedFact = factory().create(
                ConversationContextValue.recentSemanticTask(recent(
                        SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT, "tied-fact")),
                null, "tied-fact", ContextSlot.ACTIVE_FACT_CONTEXT, 0L);
        ConversationContextMutation tiedCompare = factory().create(
                ConversationContextValue.recentSemanticTask(recent(
                        SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_COMPARE, "tied-compare")),
                null, "tied-compare", ContextSlot.ACTIVE_COMPARE_CONTEXT, 0L);
        tiedStore.save(conversationId, token, tiedFact, START);
        tiedStore.save(conversationId, token, tiedCompare, START);
        ConversationContextResolution clarification = new ConversationContextResolver(tiedStore).resolve(
                conversationId, token, null,
                ConversationContextType.RECENT_SEMANTIC_TASK, START.plusSeconds(1));
        assertEquals(ConversationContextResolution.Status.CLARIFICATION_REQUIRED,
                clarification.getStatus());
    }

    @Test
    void missingExplicitContextIsInvalidAndDoesNotUseAnotherActiveContext() {
        ConversationId conversationId = ConversationId.random();
        ResumeToken token = ResumeToken.issue();
        InMemoryConversationBusinessContextStore store = new InMemoryConversationBusinessContextStore();
        ConversationContextMutation active = factory().create(
                ConversationContextValue.recentSemanticTask(recent(
                        SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT, "active")),
                null, "active", ContextSlot.ACTIVE_FACT_CONTEXT, 0L);
        store.save(conversationId, token, active, START);
        ConversationContextResolution result = new ConversationContextResolver(store).resolve(
                conversationId, token, ContextHandle.issue(),
                ConversationContextType.RECENT_SEMANTIC_TASK, START.plusSeconds(1));

        assertEquals(ConversationContextResolution.Status.INVALID_REFERENCE, result.getStatus());
        assertTrue(result.getEntry().isEmpty());
    }

    @Test
    void expiredExplicitContextIsDistinctFromMissingReference() {
        ConversationId conversationId = ConversationId.random();
        ResumeToken token = ResumeToken.issue();
        InMemoryConversationBusinessContextStore store = new InMemoryConversationBusinessContextStore();
        ConversationContextMutation mutation = factory().create(
                ConversationContextValue.recentSemanticTask(recent(
                        SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT, "expired")),
                null, "expired", ContextSlot.ACTIVE_FACT_CONTEXT, 0L);
        store.save(conversationId, token, mutation, START);

        ConversationContextResolution result = new ConversationContextResolver(store).resolve(
                conversationId, token, mutation.getContextHandle(),
                ConversationContextType.RECENT_SEMANTIC_TASK, START.plusSeconds(25 * 60 * 60));

        assertEquals(ConversationContextResolution.Status.EXPIRED, result.getStatus());
    }

    private static ConversationContextMutationFactory factory() {
        return new ConversationContextMutationFactory(
                ConversationContextCodecRegistry.defaults(), ConversationContextCapacityPolicy.defaults());
    }

    private static RecentSemanticTaskContext recent(
            SemanticRoutingTypes.SemanticTaskType taskType, String sourceTaskId) {
        return new RecentSemanticTaskContext(
                taskType, List.of(SubjectReference.project("agent", "v1")), Set.of("OVERVIEW"),
                Set.of(), "v1", sourceTaskId);
    }
}
