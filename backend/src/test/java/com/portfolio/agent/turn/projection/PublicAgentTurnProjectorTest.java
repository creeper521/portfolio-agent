package com.portfolio.agent.turn.projection;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublicAgentTurnProjectorTest {
    @Test void projectsOnlyOrderedFulfillmentGoalsIntoOneAnswerAuthority() {
        PublicAgentTurn.Answer turn = new PublicAgentTurnProjector().project(
                UUID.fromString("10000000-0000-4000-8000-000000000001"),
                ProjectionTestFixtures.generalPlan(), ProjectionTestFixtures.generalOutcome());
        assertThat(turn.getKind()).isEqualTo(PublicAgentTurn.Kind.ANSWER);
        assertThat(turn.getAnswer().getResolution()).isEqualTo(PublicAnswer.Resolution.COMPLETE);
        assertThat(turn.getAnswer().getGoalResults()).extracting(AnswerGoalResult::getGoalId)
                .containsExactly("goal-general");
        assertThat(turn.getAnswer().getSourceComposition())
                .containsExactly(PublicSupport.Kind.GENERAL_KNOWLEDGE);
    }

    @Test void recommendationIsTheOnlyOrderedRecommendationAuthority() {
        PublicAnswer answer = new PublicAgentTurnProjector().project(
                UUID.randomUUID(), ProjectionTestFixtures.recommendationPlan(),
                ProjectionTestFixtures.recommendationOutcome()).getAnswer();
        assertThat(answer.getResolution()).isEqualTo(PublicAnswer.Resolution.PARTIAL);
        PublicPresentation.Recommendation presentation =
                (PublicPresentation.Recommendation) answer.getGoalResults().getFirst().getPresentation();
        assertThat(presentation.getRequestedSize()).isEqualTo(2);
        assertThat(presentation.getActualSize()).isEqualTo(1);
        assertThat(presentation.getItems()).extracting(
                PublicPresentation.Recommendation.Item::getResultItemId)
                .containsExactly("item-goal-recommendation-1");
        assertThat(presentation.getIncompleteReasons()).containsExactly("REQUESTED_SIZE");
    }
}
