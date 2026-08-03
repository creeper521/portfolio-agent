package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerConstructionMode;
import com.portfolio.agent.answer.domain.AnswerEvidenceState;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationGuidanceStage;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationProgress;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.domain.ConversationTopic;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnswerResponseTest {

    @Test
    void exposesIndependentPublicAnswerSemantics() throws Exception {
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-1",
                "2026-07-27.1",
                ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO,
                AnswerResolution.ANSWERED,
                "title",
                List.of(new ConversationAnswerBlock(
                        ConversationSourceScope.PORTFOLIO,
                        "content",
                        List.of("claim"),
                        List.of("evidence"))),
                List.of(),
                true,
                GenerationMode.FALLBACK,
                AnswerSource.PRESET,
                "MODEL_UNAVAILABLE_FALLBACK",
                new ConversationProgress(
                        List.of(
                                ConversationTopic.BACKGROUND,
                                ConversationTopic.SOLUTION),
                        ConversationGuidanceStage.OPENING));

        ConversationAnswerResponse response = new ConversationAnswerResponse(result);

        assertThat(response.getConstructionMode())
                .isEqualTo(AnswerConstructionMode.EVIDENCE_COMPOSITION);
        assertThat(response.getIntentSource()).isEqualTo(AnswerIntentSource.PRESET);
        assertThat(response.getEvidenceState()).isEqualTo(AnswerEvidenceState.VERIFIED);
        assertThat(response.getNoticeCode()).isEqualTo("MODEL_UNAVAILABLE_FALLBACK");
        assertThat(response.getCoveredTopics()).containsExactly(
                ConversationTopic.BACKGROUND,
                ConversationTopic.SOLUTION);
        assertThat(response.getGuidanceStage())
                .isEqualTo(ConversationGuidanceStage.OPENING);

        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(response);
        assertThat(json).doesNotContain("generationMode", "answerSource");
    }

    @Test
    void exposesReferenceVersionRevalidationWithoutServerConversationState() {
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-1", "public-2", ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO, AnswerResolution.ANSWERED, "title",
                List.of(), List.of(), false).withContextVersionUpdated(true);

        ConversationAnswerResponse response = new ConversationAnswerResponse(result);

        assertThat(response.isContextVersionUpdated()).isTrue();
    }

    @Test
    void mapsLegacyBoundaryAndScopeNamesAtThePublicEdge() {
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-1", "public-1", ConversationIntent.CONVERSATION,
                ConversationAnswerScope.CONVERSATION, AnswerResolution.BOUNDARY, "title",
                List.of(), List.of(), false);

        ConversationAnswerResponse response = new ConversationAnswerResponse(result);

        assertThat(response.getResolution()).isEqualTo(AnswerResolution.NEEDS_CLARIFICATION);
        assertThat(response.getAnswerScope()).isEqualTo(ConversationAnswerScope.GLOBAL);
    }

    @Test
    void omitsPortfolioRecommendationWhenTheAnswerHasNone() throws Exception {
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-1", "2026-07-27.1", ConversationIntent.CONVERSATION,
                ConversationAnswerScope.CONVERSATION, AnswerResolution.ANSWERED, "title",
                List.of(), List.of(), false);

        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .setDefaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                .writeValueAsString(new ConversationAnswerResponse(result));

        assertThat(json).doesNotContain("portfolioRecommendation");
    }

    @Test
    void keepsAnEmptyPortfolioRecommendationInTheResponse() throws Exception {
        PortfolioRecommendationContext context = new PortfolioRecommendationContext(
                "rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "public-2026-07-31", "BACKEND", "INTERVIEWER", Set.of("RAG"), 2,
                List.of());
        PortfolioRecommendation recommendation = new PortfolioRecommendation(
                context.getRecommendationBatchId(), context, List.of(), List.of(), List.of());
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-1", "2026-07-27.1", ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO, AnswerResolution.ANSWERED, "title",
                List.of(), List.of(), false, GenerationMode.DETERMINISTIC, null, null,
                new ConversationProgress(List.of(), ConversationGuidanceStage.OPENING), recommendation);

        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .setDefaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                .writeValueAsString(new ConversationAnswerResponse(result));

        assertThat(json).contains("\"portfolioRecommendation\"")
                .contains("\"items\":[]")
                .contains("\"satisfiedConstraints\":[]")
                .contains("\"unsatisfiedConstraints\":[]")
                .contains("\"selectedPortfolioIds\":[]");
    }
}
