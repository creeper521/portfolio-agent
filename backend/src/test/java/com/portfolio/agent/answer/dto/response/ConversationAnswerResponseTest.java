package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AgentTurnResult;
import com.portfolio.agent.answer.domain.AnswerConstructionMode;
import com.portfolio.agent.answer.domain.AnswerEvidenceState;
import com.portfolio.agent.answer.domain.AnswerSectionType;
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
import com.portfolio.agent.answer.routing.domain.PlanConfirmation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnswerResponseTest {

    @Test
    void serializesPlanInvalidationWithoutPlanTokensOrInternalRoutingDetails() throws Exception {
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-1", "public-1", ConversationIntent.CONVERSATION,
                ConversationAnswerScope.CONVERSATION, AnswerResolution.REJECTED, "title",
                List.of(), List.of(), false).withAgentTurn(
                AgentTurnResult.planInvalidated(
                        PlanConfirmation.PlanInvalidationReason.CONTENT_VERSION_CHANGED,
                        new com.portfolio.agent.answer.routing.domain.SemanticTurnInput.InvalidatedPlanReference(
                                "plan-1", "sha256:plan")));

        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                new com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper().toResponse(result));

        assertThat(json).contains("\"disposition\":\"REJECTED\"")
                .contains("\"planChange\"")
                .contains("\"summary\":\"公开内容已更新，需要重新生成计划\"")
                .contains("\"changeLabels\":[\"内容版本已更新\"]")
                .contains("\"invalidatedPlanReference\":{\"planId\":\"plan-1\",\"planFingerprint\":\"sha256:plan\"}")
                .doesNotContain("reasonCode", "task-01", "REQUIRES_SUCCESS");
    }

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

        ConversationAnswerResponse response = new com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper().toResponse(result);

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

        ConversationAnswerResponse response = new com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper().toResponse(result);

        assertThat(response.isContextVersionUpdated()).isTrue();
    }

    @Test
    void mapsLegacyBoundaryAndScopeNamesAtThePublicEdge() {
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-1", "public-1", ConversationIntent.CONVERSATION,
                ConversationAnswerScope.CONVERSATION, AnswerResolution.BOUNDARY, "title",
                List.of(), List.of(), false);

        ConversationAnswerResponse response = new com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper().toResponse(result);

        assertThat(response.getResolution()).isEqualTo(AnswerResolution.NEEDS_CLARIFICATION);
        assertThat(response.getAnswerScope()).isEqualTo(ConversationAnswerScope.GLOBAL);
    }

    @Test
    void exposesOptionalSummarySectionTypeAndTitleInJson() throws Exception {
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-1", "public-1", ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO, AnswerResolution.ANSWERED, "title",
                List.of(new ConversationAnswerBlock(
                        ConversationSourceScope.PORTFOLIO,
                        AnswerSectionType.SOLUTION,
                        "技术方案与实现",
                        "使用受控路由替代硬编码。",
                        List.of("claim-1"),
                        List.of("evidence-1"))),
                List.of(), false).withSummary("公开项目摘要");

        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .setDefaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                .writeValueAsString(new com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper().toResponse(result));

        assertThat(json)
                .contains("\"summary\":\"公开项目摘要\"")
                .contains("\"sectionType\":\"SOLUTION\"")
                .contains("\"title\":\"技术方案与实现\"")
                .contains("\"claimIds\":[\"claim-1\"]")
                .contains("\"evidenceIds\":[\"evidence-1\"]");
    }

    @Test
    void omitsOptionalBlockFieldsWhenAbsent() throws Exception {
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-1", "public-1", ConversationIntent.CONVERSATION,
                ConversationAnswerScope.CONVERSATION, AnswerResolution.ANSWERED, "title",
                List.of(new ConversationAnswerBlock(
                        ConversationSourceScope.GENERAL, "正文", List.of(), List.of())),
                List.of(), false);

        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .setDefaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                .writeValueAsString(new com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper().toResponse(result));
        com.fasterxml.jackson.databind.JsonNode block = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(json).path("blocks").get(0);

        assertThat(json).doesNotContain("\"summary\"");
        assertThat(block.has("sectionType")).isFalse();
        assertThat(block.has("title")).isFalse();
        assertThat(block.get("content").asText()).isEqualTo("正文");
        assertThat(block.has("claimIds")).isFalse();
        assertThat(block.has("evidenceIds")).isFalse();
    }

    @Test
    void omitsPortfolioRecommendationWhenTheAnswerHasNone() throws Exception {
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-1", "2026-07-27.1", ConversationIntent.CONVERSATION,
                ConversationAnswerScope.CONVERSATION, AnswerResolution.ANSWERED, "title",
                List.of(), List.of(), false);

        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .setDefaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                .writeValueAsString(new com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper().toResponse(result));

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
                .writeValueAsString(new com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper().toResponse(result));

        assertThat(json).contains("\"portfolioRecommendation\"")
                .contains("\"items\":[]")
                .contains("\"satisfiedConstraints\":[]")
                .contains("\"unsatisfiedConstraints\":[]")
                .doesNotContain("\"selectedPortfolioIds\"");
    }
}
