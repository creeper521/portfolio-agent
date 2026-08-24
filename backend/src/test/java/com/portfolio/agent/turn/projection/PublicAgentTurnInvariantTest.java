package com.portfolio.agent.turn.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicAgentTurnInvariantTest {
    @Test void modelExecutionIsClosedAcrossAllSettledParticipationStates() {
        assertThat(ModelExecutionProjection.none().getParticipation())
                .isEqualTo(ModelExecutionProjection.Participation.NONE);

        for (ModelExecutionProjection.Participation participation : List.of(
                ModelExecutionProjection.Participation.NONE,
                ModelExecutionProjection.Participation.GOAL_INTERPRETATION_ONLY,
                ModelExecutionProjection.Participation.ANSWER_GENERATION,
                ModelExecutionProjection.Participation.GOAL_AND_ANSWER,
                ModelExecutionProjection.Participation.ATTEMPTED_UNAVAILABLE)) {
            ModelExecutionProjection projection = ModelExecutionProjection.model(
                    "glm-4-7-flash", "glm-4-7-flash-v1", participation);
            assertThat(projection.getSelectionKind())
                    .isEqualTo(ModelExecutionProjection.SelectionKind.MODEL);
            assertThat(projection.getRequestedModelRef()).isEqualTo("glm-4-7-flash");
            assertThat(projection.getSelectionVersion()).isEqualTo("glm-4-7-flash-v1");
            assertThat(projection.getParticipation()).isEqualTo(participation);
        }
    }

    @Test void modelExecutionRejectsOpenOrContradictoryShapes() {
        assertThatThrownBy(() -> new ModelExecutionProjection(
                ModelExecutionProjection.SelectionKind.NONE,
                "glm-4-7-flash", null,
                ModelExecutionProjection.Participation.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelExecutionProjection(
                ModelExecutionProjection.SelectionKind.NONE,
                null, null,
                ModelExecutionProjection.Participation.GOAL_INTERPRETATION_ONLY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModelExecutionProjection.model(
                "https://internal.example/model", "v1",
                ModelExecutionProjection.Participation.NONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void modelExecutionIsRequiredOnEveryDeserializedPublicTurn() {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                com.fasterxml.jackson.databind.json.JsonMapper.builder()
                        .addModule(new com.fasterxml.jackson.module.paramnames.ParameterNamesModule())
                        .build();

        assertThatThrownBy(() -> mapper.readValue("""
                {"requestId":"10000000-0000-4000-8000-000000000006",
                 "kind":"CONVERSATIONAL","message":"固定公开文本","suggestedActions":[]}
                """, PublicAgentTurn.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test void fullPartialAndNoneShapesFailClosed() {
        PublicPresentation presentation = new PublicPresentation.Sectioned(List.of(
                new PublicSection("section-one", com.portfolio.agent.turn.execution.AnswerSectionType.GENERAL_PRINCIPLE,
                        "概念", "内容", new PublicSupport(
                        PublicSupport.Kind.GENERAL_KNOWLEDGE, List.of()))));
        assertThatThrownBy(() -> new AnswerGoalResult(
                "goal-one", "目标", AnswerGoalResult.Coverage.FULL,
                null, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnswerGoalResult(
                "goal-one", "目标", AnswerGoalResult.Coverage.PARTIAL,
                presentation, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnswerGoalResult(
                "goal-one", "目标", AnswerGoalResult.Coverage.NONE,
                presentation, List.of(new GoalNotice("NO_RESULT", "无结果"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void serializerEmitsTheClosedDiscriminantsAndOmitsNullableFields() throws Exception {
        PublicAgentTurn.Answer turn = new PublicAgentTurnProjector().project(
                java.util.UUID.fromString("10000000-0000-4000-8000-000000000001"),
                ProjectionTestFixtures.generalPlan(), ProjectionTestFixtures.generalOutcome(),
                ModelExecutionProjection.model(
                        "glm-4-7-flash", "glm-4-7-flash-v1",
                        ModelExecutionProjection.Participation.GOAL_AND_ANSWER));
        String json = new ObjectMapper().writeValueAsString(turn);
        assertThat(json).contains("\"kind\":\"ANSWER\"")
                .contains("\"modelExecution\":{")
                .contains("\"selectionKind\":\"MODEL\"")
                .contains("\"requestedModelRef\":\"glm-4-7-flash\"")
                .contains("\"selectionVersion\":\"glm-4-7-flash-v1\"")
                .contains("\"participation\":\"GOAL_AND_ANSWER\"")
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
                                List.of("missing-source"))))), List.of());
        assertThatThrownBy(() -> new PublicAnswer(
                PublicAnswer.Resolution.COMPLETE, "public-1", List.of(goal),
                new PublicSourceCatalog(List.of()),
                List.of(PublicSupport.Kind.VERIFIED_PUBLIC_EVIDENCE), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void fullItemCountMayStillReportUnsatisfiedRecommendationConstraints() {
        PublicPresentation.Recommendation recommendation =
                new PublicPresentation.Recommendation(
                        1,
                        List.of(new PublicPresentation.Recommendation.Item(
                                "project-a", "项目 A", "公开摘要", "/projects/project-a",
                                List.of("具备已验证的公开实现证据"),
                                new PublicSupport(
                                        PublicSupport.Kind.GENERAL_KNOWLEDGE, List.of()),
                                null)),
                        List.of("CAPABILITY_SQL"), List.of(), List.of());

        assertThat(recommendation.getActualSize()).isEqualTo(1);
        assertThat(recommendation.getUnsatisfiedConstraints())
                .containsExactly("CAPABILITY_SQL");
        assertThat(recommendation.getIncompleteReasons()).isEmpty();
    }
}
