package com.portfolio.agent.answer.domain;

import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnswerResultTest {

    @Test
    void preservesRecommendationWhenGuidanceIsRebuilt() {
        PortfolioRecommendation recommendation = recommendation();
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-1", "public-2026-07-31", ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO, AnswerResolution.ANSWERED, "title",
                List.of(), List.of(), false, GenerationMode.DETERMINISTIC, null, null,
                new ConversationProgress(List.of(), ConversationGuidanceStage.OPENING),
                recommendation);

        ConversationAnswerResult rebuilt = result.withGuidance(
                List.of(), new ConversationProgress(
                        List.of(), ConversationGuidanceStage.EXPLORE_OTHERS));

        assertThat(rebuilt.getPortfolioRecommendation()).isSameAs(recommendation);
    }

    private PortfolioRecommendation recommendation() {
        PortfolioRecommendationContext context = new PortfolioRecommendationContext(
                "rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "public-2026-07-31", "BACKEND", "INTERVIEWER", Set.of("RAG"), 2,
                List.of("project-1"));
        return new PortfolioRecommendation(
                context.getRecommendationBatchId(), context, List.of(), List.of(), List.of());
    }
}
