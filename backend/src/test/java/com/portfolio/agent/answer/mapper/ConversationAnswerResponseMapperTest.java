package com.portfolio.agent.answer.mapper;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationGuidanceStage;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationProgress;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.dto.response.ConversationAnswerResponse;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnswerResponseMapperTest {

    @Test
    void mapsCompleteRecommendationWithoutExposingFreeTextGoal() {
        PortfolioRecommendationContext context = new PortfolioRecommendationContext(
                "rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "public-2026-07-31", "BACKEND", "INTERVIEWER", Set.of("RAG", "POSTGRESQL"),
                2, List.of("project-1"));
        PortfolioRecommendation recommendation = new PortfolioRecommendation(
                context.getRecommendationBatchId(), context,
                List.of(new PortfolioRecommendationItem(
                        "project-1", "Public project", "/portfolio/project-1",
                        List.of("covers RAG"), List.of("evidence-1"))),
                List.of("RAG"), List.of("KUBERNETES"));
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-1", "public-2026-07-31", ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO, AnswerResolution.ANSWERED, "title",
                List.of(), List.of(), false, GenerationMode.DETERMINISTIC, null, null,
                new ConversationProgress(List.of(), ConversationGuidanceStage.OPENING), recommendation);

        ConversationAnswerResponse response = new ConversationAnswerResponseMapper().toResponse(result);

        assertThat(response.getPortfolioRecommendation().getRecommendationBatchId())
                .isEqualTo(context.getRecommendationBatchId());
        assertThat(response.getPortfolioRecommendation().getContext().getContentVersion())
                .isEqualTo("public-2026-07-31");
        assertThat(response.getPortfolioRecommendation().getContext().getCapabilityCodes())
                .containsExactlyInAnyOrder("RAG", "POSTGRESQL");
        assertThat(response.getPortfolioRecommendation().getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getPortfolioId()).isEqualTo("project-1");
                    assertThat(item.getEvidenceIds()).containsExactly("evidence-1");
                });
    }
}
