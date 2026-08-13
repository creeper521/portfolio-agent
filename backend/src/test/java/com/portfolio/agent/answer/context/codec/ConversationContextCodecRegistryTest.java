package com.portfolio.agent.answer.context.codec;

import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.RecentSemanticTaskContext;
import com.portfolio.agent.answer.context.domain.RecommendationContext;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConversationContextCodecRegistryTest {
    @Test
    void codecsAreCanonicalAndRoundTripOnlyTypedContextFields() {
        ConversationContextCodecRegistry registry = ConversationContextCodecRegistry.defaults();
        RecentSemanticTaskContext context = new RecentSemanticTaskContext(
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                List.of(SubjectReference.project("project-a", "public-v1")),
                Set.of("OUTCOME", "IMPLEMENTATION"), Set.of(), "public-v1", "task-1");

        ConversationContextCodecRegistry.EncodedContext first = registry.encode(
                ConversationContextType.RECENT_SEMANTIC_TASK, context);
        ConversationContextCodecRegistry.EncodedContext second = registry.encode(
                ConversationContextType.RECENT_SEMANTIC_TASK, context);

        assertArrayEquals(first.getPayload(), second.getPayload());
        RecentSemanticTaskContext decoded = (RecentSemanticTaskContext) registry.decode(first);
        assertEquals(context.getContentVersion(), decoded.getContentVersion());
        assertThrows(IllegalArgumentException.class, () -> registry.decode(
                new ConversationContextCodecRegistry.EncodedContext(
                        ConversationContextType.RECENT_SEMANTIC_TASK, "p3-recent-v0", first.getPayload())));
    }

    @Test
    void recommendationCodecDoesNotAcceptRawQuestionOrAnswerFields() {
        ConversationContextCodecRegistry registry = ConversationContextCodecRegistry.defaults();
        RecommendationContext context = new RecommendationContext(
                com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope
                        .allPublishedCandidates("public-v1"), "recommendation-v1",
                Set.of("PUBLIC_DELIVERY_EVIDENCE"), Set.of("EXCLUDE_UNVERIFIED"),
                Set.of("JAVA"), Set.of(), 3, null);
        ConversationContextCodecRegistry.EncodedContext encoded = registry.encode(
                ConversationContextType.RECOMMENDATION, context);
        String json = new String(encoded.getPayload(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(-1, json.indexOf("question"));
        assertEquals(-1, json.indexOf("answer"));
        assertEquals(3, ((RecommendationContext) registry.decode(encoded)).getResultLimit());
    }
}
