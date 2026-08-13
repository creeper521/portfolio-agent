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
                "profile-v1", Set.of("BASELINE"), Set.of(), Set.of("LOW_RISK"), Set.of(), 3, null);
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
}
