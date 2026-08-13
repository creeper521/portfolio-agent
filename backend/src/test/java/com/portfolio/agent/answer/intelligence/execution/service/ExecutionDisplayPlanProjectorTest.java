package com.portfolio.agent.answer.intelligence.execution.service;

import com.portfolio.agent.answer.intelligence.execution.domain.ExecutionDisplayPlan;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnOutcome;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.routing.domain.TaskResultProvenance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionDisplayPlanProjectorTest {

    @Test
    void projectsOnlyFinalSafeStagesAndStableDisplayIndexes() {
        SemanticTask first = task("task-01");
        SemanticTask second = task("task-02");
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "plan-private-id", "public-v1", SemanticTurnPlan.PlanSource.RULE,
                List.of(first, second), List.of(), List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation());
        SemanticTurnOutcome outcome = new SemanticTurnOutcome(List.of(
                TaskOutcome.answered(first.getTaskId(), first.getSourceDomain(),
                        new TaskResultPayload.SectionResultPayload(List.of("safe"), null),
                        TaskResultProvenance.direct(first.getSourceDomain(), List.of(), List.of()), false),
                TaskOutcome.notExecutedBudget(second.getTaskId(), second.getSourceDomain())));

        ExecutionDisplayPlan display = new ExecutionDisplayPlanProjector().project(plan, outcome);

        assertEquals("p3-display-v1", display.getContractVersion());
        assertEquals(ExecutionDisplayPlan.OverallStatus.PARTIAL, display.getOverallStatus());
        assertEquals(4, display.getTasks().getFirst().getStages().size());
        assertEquals(ExecutionDisplayPlan.TaskDisplayStatus.COMPLETED,
                display.getTasks().getFirst().getFinalStatus());
        assertEquals(ExecutionDisplayPlan.TaskDisplayStatus.SKIPPED,
                display.getTasks().get(1).getFinalStatus());
        assertEquals(ExecutionDisplayPlan.StageStatus.SKIPPED,
                display.getTasks().get(1).getStages().getFirst().getStatus());
    }

    private static SemanticTask task(String taskId) {
        SubjectReference subject = SubjectReference.project("project-" + taskId, "public-v1");
        return SemanticTask.create(
                taskId,
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                "Describe the public project",
                new SemanticTaskParameters.PortfolioFact(subject, Set.of(), "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(), List.of(subject));
    }
}
