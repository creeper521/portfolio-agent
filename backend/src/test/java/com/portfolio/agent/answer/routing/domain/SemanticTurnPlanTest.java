package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SemanticTurnPlanTest {

    @Test
    void copiesCollectionsAndDoesNotExposeSensitivePlanValuesInToString() {
        SemanticTask task = fact("task-01", "project-a");
        List<SemanticTask> tasks = new ArrayList<>(List.of(task));
        List<TaskDependency> dependencies = new ArrayList<>();
        List<PlanExclusion> exclusions = new ArrayList<>();
        Set<RequestedOutput> outputs = new LinkedHashSet<>(Set.of(RequestedOutput.SUMMARY));

        SemanticTurnPlan plan = new SemanticTurnPlan(
                "plan-private-id",
                "public-v1",
                SemanticTurnPlan.PlanSource.RULE,
                tasks,
                dependencies,
                exclusions,
                outputs,
                SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation(),
                null);
        tasks.clear();
        outputs.clear();

        assertEquals(1, plan.getTasks().size());
        assertEquals(Set.of(RequestedOutput.SUMMARY), plan.getRequestedOutputs());
        assertThrows(UnsupportedOperationException.class, () -> plan.getTasks().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.getDependencies().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.getExclusions().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.getRequestedOutputs().clear());
        assertFalse(plan.toString().contains("plan-private-id"));
        assertFalse(plan.toString().contains("project-a"));
    }

    private static SemanticTask fact(String taskId, String projectId) {
        SubjectReference subject = SubjectReference.project(projectId, "public-v1");
        SemanticTaskParameters.PortfolioFact parameters = new SemanticTaskParameters.PortfolioFact(
                subject, Set.of(), "INTERVIEWER");
        return SemanticTask.create(
                taskId,
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                "Describe the project",
                parameters,
                Set.of(RequestedOutput.SUMMARY),
                TaskConfidence.highRule(),
                List.of(subject));
    }
}
