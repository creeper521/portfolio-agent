package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskFulfillmentRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticPlanCompilerFulfillmentRoleTest {

    @Test
    void explicitSynthesisGoalMakesUpstreamTasksSupportingAndSynthesisPrimary() {
        SubjectReference project = SubjectReference.project("project-a", "public-v1");
        SemanticSignals signals = new SemanticSignals(
                "解释乐观锁，并说明它在 Project A 中怎么使用并形成综合结论",
                List.of(
                        new SemanticSignals.GoalCandidate(
                                SemanticSignals.Intent.GENERAL_EXPLANATION, List.of()),
                        new SemanticSignals.GoalCandidate(
                                SemanticSignals.Intent.PORTFOLIO_FACT, List.of(project)),
                        new SemanticSignals.GoalCandidate(
                                SemanticSignals.Intent.SYNTHESIS, List.of())),
                List.of(), 3, SemanticSignals.ClarificationNeed.NONE,
                false, false, false);

        SemanticTurnPlan plan = new SemanticPlanCompiler(new SemanticRoutingPolicy())
                .compile(signals);

        assertThat(plan.getTasks()).extracting(SemanticTask::getFulfillmentRole)
                .containsExactly(
                        TaskFulfillmentRole.SUPPORTING,
                        TaskFulfillmentRole.SUPPORTING,
                        TaskFulfillmentRole.PRIMARY);
        assertThat(plan.getTasks()).filteredOn(task ->
                task.getTaskType() == SemanticRoutingTypes.SemanticTaskType.SYNTHESIS)
                .singleElement().extracting(SemanticTask::getFulfillmentRole)
                .isEqualTo(TaskFulfillmentRole.PRIMARY);
    }

    @Test
    void independentGoalsRemainPrimaryWhenThereIsNoSynthesisGoal() {
        SubjectReference project = SubjectReference.project("project-a", "public-v1");
        SemanticSignals signals = new SemanticSignals(
                "解释乐观锁，并介绍 Project A",
                List.of(
                        new SemanticSignals.GoalCandidate(
                                SemanticSignals.Intent.GENERAL_EXPLANATION, List.of()),
                        new SemanticSignals.GoalCandidate(
                                SemanticSignals.Intent.PORTFOLIO_FACT, List.of(project))),
                List.of(), 2, SemanticSignals.ClarificationNeed.NONE,
                false, false, false);

        SemanticTurnPlan plan = new SemanticPlanCompiler(new SemanticRoutingPolicy())
                .compile(signals);

        assertThat(plan.getTasks()).extracting(SemanticTask::getFulfillmentRole)
                .containsOnly(TaskFulfillmentRole.PRIMARY);
    }

    @Test
    void compilerKeepsOneTaskForDuplicateGoalCandidates() {
        SubjectReference project = SubjectReference.project("project-a", "public-v1");
        SemanticSignals.GoalCandidate goal = new SemanticSignals.GoalCandidate(
                SemanticSignals.Intent.PORTFOLIO_FACT, List.of(project));
        SemanticSignals signals = new SemanticSignals(
                "介绍 Project A", List.of(goal, goal), List.of(), 2,
                SemanticSignals.ClarificationNeed.NONE, false, false, false);

        SemanticTurnPlan plan = new SemanticPlanCompiler(new SemanticRoutingPolicy()).compile(signals);

        assertThat(plan.getTasks()).hasSize(1);
        assertThat(plan.getTasks().getFirst().getFulfillmentRole())
                .isEqualTo(TaskFulfillmentRole.PRIMARY);
    }
}
