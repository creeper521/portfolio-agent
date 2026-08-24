package com.portfolio.agent.turn.projection;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublicAgentTurnProjectorTest {
    @Test void projectsOnlyOrderedFulfillmentGoalsIntoOneAnswerAuthority() {
        ModelExecutionProjection modelExecution = ModelExecutionProjection.model(
                "glm-4-7-flash", "glm-4-7-flash-v1",
                ModelExecutionProjection.Participation.GOAL_INTERPRETATION_ONLY);
        PublicAgentTurn.Answer turn = new PublicAgentTurnProjector().project(
                UUID.fromString("10000000-0000-4000-8000-000000000001"),
                ProjectionTestFixtures.generalPlan(), ProjectionTestFixtures.generalOutcome(),
                modelExecution);
        assertThat(turn.getKind()).isEqualTo(PublicAgentTurn.Kind.ANSWER);
        assertThat(turn.getModelExecution()).isEqualTo(modelExecution);
        assertThat(turn.getAnswer().getResolution()).isEqualTo(PublicAnswer.Resolution.COMPLETE);
        assertThat(turn.getAnswer().getGoalResults()).extracting(AnswerGoalResult::getGoalId)
                .containsExactly("goal-general");
        assertThat(turn.getAnswer().getSourceComposition())
                .containsExactly(PublicSupport.Kind.GENERAL_KNOWLEDGE);
    }

    @Test void recommendationIsTheOnlyOrderedRecommendationAuthority() {
        PublicAnswer answer = new PublicAgentTurnProjector().project(
                UUID.randomUUID(), ProjectionTestFixtures.recommendationPlan(),
                ProjectionTestFixtures.recommendationOutcome(),
                java.util.Map.of(
                        "goal-recommendation",
                        "recommendation_context_123")).getAnswer();
        assertThat(answer.getResolution()).isEqualTo(PublicAnswer.Resolution.PARTIAL);
        PublicPresentation.Recommendation presentation =
                (PublicPresentation.Recommendation) answer.getGoalResults().getFirst().getPresentation();
        assertThat(presentation.getRequestedSize()).isEqualTo(2);
        assertThat(presentation.getActualSize()).isEqualTo(1);
        assertThat(presentation.getItems()).extracting(
                PublicPresentation.Recommendation.Item::getResultItemId)
                .containsExactly("item-goal-recommendation-1");
        SuggestedAction action = presentation.getItems().getFirst()
                .getDiscussionAction();
        assertThat(action.getLabel()).isEqualTo("与我讨论");
        assertThat(action.getContinuation().getOperation())
                .isEqualTo(
                        com.portfolio.agent.turn.continuation.ContinuationReference.Operation.ENTER_RESULT);
        assertThat(action.getContinuation().getContextHandle())
                .isEqualTo("recommendation_context_123");
        assertThat(action.getContinuation().getResultItemId())
                .isEqualTo("item-goal-recommendation-1");
        assertThat(presentation.getIncompleteReasons()).containsExactly("REQUESTED_SIZE");
        assertThat(presentation.getItems().getFirst().getReasons())
                .containsExactly("具备公开可验证材料");
    }
}
