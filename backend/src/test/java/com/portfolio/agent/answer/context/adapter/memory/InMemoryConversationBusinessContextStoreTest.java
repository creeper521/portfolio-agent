package com.portfolio.agent.answer.context.adapter.memory;

import com.portfolio.agent.answer.context.codec.ConversationContextCodecRegistry;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ContextSlot;
import com.portfolio.agent.answer.context.domain.ConversationContextMutation;
import com.portfolio.agent.answer.context.domain.ConversationContextValue;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RecentSemanticTaskContext;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.context.gateway.ConversationBusinessContextStore;
import com.portfolio.agent.answer.context.service.ConversationContextCapacityPolicy;
import com.portfolio.agent.answer.context.service.ConversationContextMutationFactory;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryConversationBusinessContextStoreTest {
    private static final Instant START = Instant.parse("2026-08-12T04:00:00Z");

    @Test
    void handleRequiresTheSameConversationAndResumeTokenAndLegalResolveRenewsIdleTtl() {
        ConversationId conversationId = ConversationId.random();
        ConversationId otherConversation = ConversationId.random();
        ResumeToken token = ResumeToken.issue();
        ResumeToken wrongToken = ResumeToken.issue();
        ConversationContextMutation mutation = factory().create(
                ConversationContextValue.recentSemanticTask(recent("task-1")), null, "task-1");
        InMemoryConversationBusinessContextStore store = new InMemoryConversationBusinessContextStore();
        store.save(conversationId, token, mutation, START);

        assertTrue(store.resolve(conversationId, token, mutation.getContextHandle(), START.plusSeconds(1)).isPresent());
        assertTrue(store.resolve(conversationId, wrongToken, mutation.getContextHandle(), START.plusSeconds(2)).isEmpty());
        assertTrue(store.resolve(otherConversation, token, mutation.getContextHandle(), START.plusSeconds(2)).isEmpty());
        assertTrue(store.resolve(conversationId, token, mutation.getContextHandle(), START.plus(java.time.Duration.ofHours(23))).isPresent());
        assertTrue(store.resolve(conversationId, token, mutation.getContextHandle(), START.plus(java.time.Duration.ofHours(46))).isPresent());
        assertTrue(store.resolve(conversationId, token, mutation.getContextHandle(), START.plus(java.time.Duration.ofDays(7))).isEmpty());
    }

    @Test
    void capacityPrunesOldNonActiveEntriesButCannotDeleteActiveEntry() {
        ConversationId conversationId = ConversationId.random();
        ResumeToken token = ResumeToken.issue();
        InMemoryConversationBusinessContextStore store = new InMemoryConversationBusinessContextStore();
        ConversationContextMutation oldestNonActive = null;
        ConversationContextMutation active = factory().create(
                ConversationContextValue.recentSemanticTask(recent("active")), null, "active",
                ContextSlot.ACTIVE_FACT_CONTEXT, 0L);
        store.save(conversationId, token, active, START);
        for (int index = 0; index < 31; index++) {
            ConversationContextMutation mutation = factory().create(
                    ConversationContextValue.recentSemanticTask(recent("task-" + index)), null,
                    "task-" + index);
            if (index == 0) {
                oldestNonActive = mutation;
            }
            store.save(conversationId, token, mutation, START.plusSeconds(index + 1));
        }
        ConversationContextMutation newest = factory().create(
                ConversationContextValue.recentSemanticTask(recent("newest")), null, "newest");
        store.save(conversationId, token, newest, START.plusSeconds(100));

        assertEquals(32, store.list(conversationId, token, START.plusSeconds(100)).size());
        assertTrue(store.resolve(conversationId, token, active.getContextHandle(), START.plusSeconds(100)).isPresent());
        assertTrue(store.resolve(conversationId, token, newest.getContextHandle(), START.plusSeconds(100)).isPresent());
        assertTrue(store.resolve(conversationId, token, oldestNonActive.getContextHandle(),
                START.plusSeconds(100)).isEmpty());
    }

    @Test
    void concurrentRecommendationBranchesBothPersistButOnlyCasWinnerBecomesActive() {
        ConversationId conversationId = ConversationId.random();
        ResumeToken token = ResumeToken.issue();
        InMemoryConversationBusinessContextStore store = new InMemoryConversationBusinessContextStore();
        ConversationContextMutation parent = factory().create(
                ConversationContextValue.recentSemanticTask(recent("parent")), null, "parent",
                ContextSlot.ACTIVE_FACT_CONTEXT, 0L);
        store.save(conversationId, token, parent, START);
        ConversationContextMutation branchA = factory().create(
                ConversationContextValue.recentSemanticTask(recent("branch-a")), parent.getContextHandle(), "branch-a",
                ContextSlot.ACTIVE_FACT_CONTEXT, 1L);
        ConversationContextMutation branchB = factory().create(
                ConversationContextValue.recentSemanticTask(recent("branch-b")), parent.getContextHandle(), "branch-b",
                ContextSlot.ACTIVE_FACT_CONTEXT, 1L);

        ConversationBusinessContextStore.SaveResult resultA = store.save(
                conversationId, token, branchA, START.plusSeconds(1));
        ConversationBusinessContextStore.SaveResult resultB = store.save(
                conversationId, token, branchB, START.plusSeconds(2));

        assertTrue(resultA.isActiveAdvanced());
        assertFalse(resultB.isActiveAdvanced());
        assertEquals(branchA.getContextHandle(), store.active(
                conversationId, token, ContextSlot.ACTIVE_FACT_CONTEXT, START.plusSeconds(2)).orElseThrow().getContextHandle());
        assertTrue(store.resolve(conversationId, token, branchB.getContextHandle(), START.plusSeconds(2)).isPresent());
    }

    @Test
    void rejectsParentFromAnotherConversationAndCapacityWithOnlyActiveEntry() {
        ConversationId first = ConversationId.random();
        ConversationId second = ConversationId.random();
        ResumeToken firstToken = ResumeToken.issue();
        ResumeToken secondToken = ResumeToken.issue();
        InMemoryConversationBusinessContextStore store = new InMemoryConversationBusinessContextStore(
                new ConversationContextCapacityPolicy(1, 16 * 1024,
                        java.time.Duration.ofHours(24), java.time.Duration.ofDays(7)));
        ConversationContextMutation firstMutation = factory().create(
                ConversationContextValue.recentSemanticTask(recent("first")), null, "first",
                ContextSlot.ACTIVE_FACT_CONTEXT, 0L);
        store.save(first, firstToken, firstMutation, START);
        ConversationContextMutation secondMutation = factory().create(
                ConversationContextValue.recentSemanticTask(recent("second")), null, "second",
                ContextSlot.ACTIVE_FACT_CONTEXT, 0L);
        assertThrows(ContextCapacityExceededException.class,
                () -> store.save(first, firstToken, secondMutation, START.plusSeconds(1)));
        ConversationContextMutation foreignParent = factory().create(
                ConversationContextValue.recentSemanticTask(recent("foreign")), firstMutation.getContextHandle(), "foreign");
        assertThrows(IllegalArgumentException.class,
                () -> store.save(second, secondToken, foreignParent, START.plusSeconds(1)));
    }

    private static ConversationContextMutationFactory factory() {
        return new ConversationContextMutationFactory(
                ConversationContextCodecRegistry.defaults(), ConversationContextCapacityPolicy.defaults());
    }

    private static RecentSemanticTaskContext recent(String sourceTaskId) {
        return new RecentSemanticTaskContext(
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                List.of(SubjectReference.project("agent", "v1")), Set.of("OVERVIEW"),
                Set.of(), "v1", sourceTaskId);
    }
}
