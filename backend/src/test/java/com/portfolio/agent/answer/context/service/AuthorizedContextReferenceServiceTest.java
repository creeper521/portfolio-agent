package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.adapter.memory.InMemoryConversationBusinessContextStore;
import com.portfolio.agent.answer.context.codec.ConversationContextCodecRegistry;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ContextSlot;
import com.portfolio.agent.answer.context.domain.ConversationContextMutation;
import com.portfolio.agent.answer.context.domain.ConversationContextValue;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RecommendationContext;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.context.domain.OrderedResultSelection;
import com.portfolio.agent.answer.context.domain.SubjectOrderKind;
import com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope;
import com.portfolio.agent.answer.routing.domain.AuthorizedContextReference;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizedContextReferenceServiceTest {
    @Test
    void onlyTheAuthorizedSessionCanProduceExecutorReferenceAndRecommendationBinding() {
        ConversationId conversationId = ConversationId.random();
        ResumeToken token = ResumeToken.issue();
        InMemoryConversationBusinessContextStore store = new InMemoryConversationBusinessContextStore();
        RecommendationContext context = new RecommendationContext(
                AuthorizedSubjectScope.exactSubjects(
                        List.of(SubjectReference.project("agent", "v1")), "v1"),
                "profile-v1", Set.of("BASELINE"), Set.of(), Set.of("LOW_RISK"), Set.of(), 3, null,
                new OrderedResultSelection(SubjectOrderKind.RECOMMENDATION_RANK, List.of(
                        new OrderedResultSelection.Item(1, "result-item-agent", "agent"))), "batch-1");
        ConversationContextMutation mutation = new ConversationContextMutation(
                ContextHandle.issue(), ConversationContextValue.recommendation(context), null,
                "recommendation-task", 128, ContextSlot.ACTIVE_RECOMMENDATION, 0L);
        store.save(conversationId, token, mutation, Instant.parse("2026-08-12T04:00:00Z"));
        AuthorizedContextReference requested = new AuthorizedContextReference(
                mutation.getContextHandle().asBase64Url(), "RECOMMENDATION");
        AuthorizedContextReferenceService service = new AuthorizedContextReferenceService(
                new ConversationContextResolver(store));

        Optional<AuthorizedContextReference> authorized = service.authorize(
                conversationId, token, requested, Instant.parse("2026-08-12T04:00:01Z"));
        Optional<AuthorizedContextReference> foreign = service.authorize(
                conversationId, ResumeToken.issue(), requested, Instant.parse("2026-08-12T04:00:01Z"));

        assertTrue(authorized.isPresent());
        assertEquals("RECOMMENDATION", authorized.orElseThrow().getExpectedContextType());
        assertTrue(authorized.orElseThrow().getRecommendationScopeBinding().isPresent());
        assertTrue(foreign.isEmpty());
    }

    @Test
    void resultItemSelectsExactSubjectThenRevalidatesItToCurrentContent() {
        ConversationId conversationId = ConversationId.random();
        ResumeToken token = ResumeToken.issue();
        InMemoryConversationBusinessContextStore store = new InMemoryConversationBusinessContextStore();
        RecommendationContext context = new RecommendationContext(
                AuthorizedSubjectScope.allPublishedCandidates("v1"), "profile-v1",
                Set.of("BASELINE"), Set.of(), Set.of(), Set.of(), 3, null,
                new OrderedResultSelection(SubjectOrderKind.RECOMMENDATION_RANK, List.of(
                        new OrderedResultSelection.Item(1, "result-item-case", "case-a",
                                com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType.CASE))),
                "batch-1");
        ConversationContextMutation mutation = new ConversationContextMutation(
                ContextHandle.issue(), ConversationContextValue.recommendation(context), null,
                "recommendation-task", 128, ContextSlot.ACTIVE_RECOMMENDATION, 0L);
        Instant now = Instant.parse("2026-08-12T04:00:00Z");
        store.save(conversationId, token, mutation, now);
        AuthorizedContextReferenceResult result = new AuthorizedContextReferenceService(
                new ConversationContextResolver(store)).authorizeDetailed(
                conversationId, token, new AuthorizedContextReference(
                        mutation.getContextHandle().asBase64Url(), "RECOMMENDATION",
                        null, "result-item-case"), now.plusSeconds(1), "v2");

        assertEquals(ContextVersionStatus.REVALIDATED,
                result.getVersionDecision().orElseThrow().getStatus());
        assertEquals("v2", result.getReference().orElseThrow().getSelectedSubject()
                .orElseThrow().getContentVersion());
        assertEquals(AuthorizedSubjectScope.ScopeMode.EXACT_SUBJECTS,
                result.getReference().orElseThrow().getRecommendationScopeBinding()
                        .orElseThrow().getScope().getMode());
    }
}
