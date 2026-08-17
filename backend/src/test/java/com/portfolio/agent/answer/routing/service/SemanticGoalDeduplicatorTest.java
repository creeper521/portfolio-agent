package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticGoalDeduplicatorTest {

    @Test
    void preservesFirstOccurrenceOfAnEquivalentGoal() {
        SubjectReference project = SubjectReference.project("project-a", "content-v1");
        SemanticSignals.GoalCandidate goal = new SemanticSignals.GoalCandidate(
                SemanticSignals.Intent.PORTFOLIO_FACT, List.of(project));

        assertThat(SemanticGoalDeduplicator.distinctGoals(List.of(goal, goal))).containsExactly(goal);
    }

    @Test
    void keepsGoalsWithDifferentSubjectsDistinct() {
        SemanticSignals.GoalCandidate first = new SemanticSignals.GoalCandidate(
                SemanticSignals.Intent.PORTFOLIO_FACT,
                List.of(SubjectReference.project("project-a", "content-v1")));
        SemanticSignals.GoalCandidate second = new SemanticSignals.GoalCandidate(
                SemanticSignals.Intent.PORTFOLIO_FACT,
                List.of(new SubjectReference(
                        SubjectType.PROJECT, "project-b",
                        com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource.EXPLICIT_TEXT,
                        "content-v1")));

        assertThat(SemanticGoalDeduplicator.distinctGoals(List.of(first, second)))
                .containsExactly(first, second);
    }
}
