package com.portfolio.agent.turn.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicAgentTurnInvariantTest {
    @Test void fullPartialAndNoneShapesFailClosed() {
        PublicPresentation presentation = new PublicPresentation.Sectioned(List.of(
                new PublicSection("section-one", com.portfolio.agent.turn.execution.AnswerSectionType.GENERAL_PRINCIPLE,
                        "概念", "内容", new PublicSupport(
                        PublicSupport.Kind.GENERAL_KNOWLEDGE, List.of()))));
        assertThatThrownBy(() -> new AnswerGoalResult(
                "goal-one", "目标", AnswerGoalResult.Coverage.FULL,
                null, List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnswerGoalResult(
                "goal-one", "目标", AnswerGoalResult.Coverage.PARTIAL,
                presentation, List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnswerGoalResult(
                "goal-one", "目标", AnswerGoalResult.Coverage.NONE,
                presentation, List.of(new GoalNotice("NO_RESULT", "无结果")), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void serializerEmitsTheClosedDiscriminantsAndOmitsNullableFields() throws Exception {
        PublicAgentTurn.Answer turn = new PublicAgentTurnProjector().project(
                java.util.UUID.fromString("10000000-0000-4000-8000-000000000001"),
                ProjectionTestFixtures.generalPlan(), ProjectionTestFixtures.generalOutcome());
        String json = new ObjectMapper().writeValueAsString(turn);
        assertThat(json).contains("\"kind\":\"ANSWER\"")
                .contains("\"resolution\":\"COMPLETE\"")
                .contains("\"kind\":\"SECTIONED\"")
                .doesNotContain("\"presentation\":null")
                .doesNotContain("\"continuation\":null")
                .doesNotContain("degraded", "completedTasks", "execution");
    }

    @Test void closedKindRoundTripsForEncryptedReplayCodec() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                com.fasterxml.jackson.databind.json.JsonMapper.builder()
                        .addModule(new com.fasterxml.jackson.module.paramnames.ParameterNamesModule())
                        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();
        PublicAgentTurn.Answer original = new PublicAgentTurnProjector().project(
                java.util.UUID.randomUUID(), ProjectionTestFixtures.recommendationPlan(),
                ProjectionTestFixtures.recommendationOutcome());
        PublicAgentTurn decoded = mapper.readValue(
                mapper.writeValueAsBytes(original), PublicAgentTurn.class);
        assertThat(decoded).isInstanceOf(PublicAgentTurn.Answer.class);
        PublicPresentation.Recommendation recommendation = (PublicPresentation.Recommendation)
                ((PublicAgentTurn.Answer) decoded).getAnswer().getGoalResults().getFirst().getPresentation();
        assertThat(recommendation.getActualSize()).isEqualTo(1);
        assertThat(recommendation.getItems().getFirst().getRoute()).isEqualTo("/projects/project-a");
    }

    @Test void sourceReferencesMustResolveThroughTheSingleCatalog() {
        AnswerGoalResult goal = new AnswerGoalResult(
                "goal-one", "目标", AnswerGoalResult.Coverage.FULL,
                new PublicPresentation.Sectioned(List.of(new PublicSection(
                        "section-one", com.portfolio.agent.turn.execution.AnswerSectionType.SOLUTION, "方案", "内容",
                        new PublicSupport(PublicSupport.Kind.VERIFIED_PUBLIC_EVIDENCE,
                                List.of("missing-source"))))), List.of(), null);
        assertThatThrownBy(() -> new PublicAnswer(
                PublicAnswer.Resolution.COMPLETE, "public-1", List.of(goal),
                new PublicSourceCatalog(List.of()),
                List.of(PublicSupport.Kind.VERIFIED_PUBLIC_EVIDENCE), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
