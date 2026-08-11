package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.ExecutionSelection;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnOutcome;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskDependency;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.routing.domain.TaskResultProvenance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SemanticTurnCoordinatorTest {

    @Test
    void blocksRequiredDependentsButContinuesIndependentTasks() {
        RecordingExecutor executor = new RecordingExecutor(Set.of("task-01"));
        SemanticTurnCoordinator coordinator = new SemanticTurnCoordinator(List.of(executor));
        ValidatedSemanticTurnPlan plan = validated(plan(
                List.of(fact("task-01"), fact("task-02"), fact("task-03")),
                List.of(requiredDependency("task-01", "task-02"))));

        SemanticTurnOutcome outcome = coordinator.execute(plan,
                ExecutionSelection.allExecutable(Set.of("task-01", "task-02", "task-03")));

        assertEquals(TaskOutcome.TaskExecutionStatus.FAILED,
                outcome.getTask("task-01").orElseThrow().getExecutionStatus());
        assertEquals(TaskOutcome.TaskExecutionStatus.BLOCKED,
                outcome.getTask("task-02").orElseThrow().getExecutionStatus());
        assertEquals(TaskOutcome.TaskExecutionStatus.SUCCEEDED,
                outcome.getTask("task-03").orElseThrow().getExecutionStatus());
        assertEquals(SemanticTurnOutcome.PlanOutcome.PARTIAL, outcome.getPlanOutcome());
        assertEquals(List.of("task-01", "task-03"), executor.getExecutedTaskIds());
    }

    @Test
    void stableTopologicalOrderingProducesRepeatableOutcomeAndExecutorOrder() {
        RecordingExecutor firstExecutor = new RecordingExecutor(Set.of());
        RecordingExecutor secondExecutor = new RecordingExecutor(Set.of());
        SemanticTurnCoordinator first = new SemanticTurnCoordinator(List.of(firstExecutor));
        SemanticTurnCoordinator second = new SemanticTurnCoordinator(List.of(secondExecutor));
        ValidatedSemanticTurnPlan plan = validated(plan(
                List.of(fact("task-03"), fact("task-01"), fact("task-02")),
                List.of(
                        requiredDependency("task-01", "task-03"),
                        requiredDependency("task-02", "task-03"))));
        ExecutionSelection selection = ExecutionSelection.allExecutable(
                Set.of("task-01", "task-02", "task-03"));

        SemanticTurnOutcome firstOutcome = first.execute(plan, selection);
        SemanticTurnOutcome secondOutcome = second.execute(plan, selection);

        assertEquals(firstOutcome, secondOutcome);
        assertEquals(List.of("task-01", "task-02", "task-03"), firstExecutor.getExecutedTaskIds());
        assertEquals(firstExecutor.getExecutedTaskIds(), secondExecutor.getExecutedTaskIds());
    }

    @Test
    void sameLayerReadyTasksFollowOriginalPlanOrderNotDependencyListOrder() {
        RecordingExecutor executor = new RecordingExecutor(Set.of());
        SemanticTurnCoordinator coordinator = new SemanticTurnCoordinator(List.of(executor));
        SemanticTask firstReady = fact("task-04");
        SemanticTask secondReady = fact("task-03");
        SemanticTask prerequisite = fact("task-01");
        ValidatedSemanticTurnPlan plan = validated(plan(
                List.of(firstReady, secondReady, prerequisite),
                List.of(
                        requiredDependency("task-01", "task-03"),
                        requiredDependency("task-01", "task-04"))));

        coordinator.execute(plan, ExecutionSelection.allExecutable(
                Set.of("task-01", "task-03", "task-04")));

        assertEquals(List.of("task-01", "task-04", "task-03"), executor.getExecutedTaskIds());
    }

    @Test
    void orderAfterOnlyOrdersAndDoesNotPassUpstreamOutcomeToExecutor() {
        RecordingExecutor executor = new RecordingExecutor(Set.of());
        SemanticTurnCoordinator coordinator = new SemanticTurnCoordinator(List.of(executor));
        ValidatedSemanticTurnPlan plan = validated(plan(
                List.of(fact("task-02"), fact("task-01")),
                List.of(new TaskDependency(
                        "task-01",
                        "task-02",
                        SemanticRoutingTypes.TaskDependencyType.ORDER_AFTER,
                        SemanticRoutingTypes.DependencyOrigin.USER_EXPLICIT))));

        coordinator.execute(plan, ExecutionSelection.allExecutable(Set.of("task-01", "task-02")));

        assertEquals(List.of("task-01", "task-02"), executor.getExecutedTaskIds());
        assertEquals(List.of(), executor.getAvailableTaskIds("task-02"));
    }

    @Test
    void selectionMustCoverEveryPlanTask() {
        RecordingExecutor executor = new RecordingExecutor(Set.of());
        SemanticTurnCoordinator coordinator = new SemanticTurnCoordinator(List.of(executor));
        ValidatedSemanticTurnPlan plan = validated(plan(
                List.of(fact("task-01"), fact("task-02")), List.of()));

        assertThrows(IllegalArgumentException.class, () -> coordinator.execute(
                plan, ExecutionSelection.allExecutable(Set.of("task-01"))));
    }

    @Test
    void deferredAndBlockedTasksRequireControlledReasonCodes() {
        assertThrows(IllegalArgumentException.class, () -> ExecutionSelection.partition(
                Set.of("task-01"), Set.of("task-02"), Set.of(), java.util.Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ExecutionSelection.partition(
                Set.of("task-01"), Set.of(), Set.of("task-02"), java.util.Map.of()));
    }

    @Test
    void usesAvailableResultsCanContinueWhenAnotherUpstreamTaskFails() {
        RecordingExecutor executor = new RecordingExecutor(Set.of("task-01"));
        SemanticTurnCoordinator coordinator = new SemanticTurnCoordinator(List.of(executor));
        ValidatedSemanticTurnPlan plan = validated(plan(
                List.of(fact("task-01"), fact("task-02"), fact("task-03")),
                List.of(
                        availableDependency("task-01", "task-03"),
                        availableDependency("task-02", "task-03"))));

        SemanticTurnOutcome outcome = coordinator.execute(plan,
                ExecutionSelection.allExecutable(Set.of("task-01", "task-02", "task-03")));

        assertEquals(TaskOutcome.TaskExecutionStatus.SUCCEEDED,
                outcome.getTask("task-03").orElseThrow().getExecutionStatus());
        assertEquals(List.of("task-01", "task-02", "task-03"), executor.getExecutedTaskIds());
    }

    @Test
    void selectionProducesCancelledAndBlockedOutcomesWithoutCallingExecutor() {
        RecordingExecutor executor = new RecordingExecutor(Set.of());
        SemanticTurnCoordinator coordinator = new SemanticTurnCoordinator(List.of(executor));
        ValidatedSemanticTurnPlan plan = validated(plan(
                List.of(fact("task-01"), fact("task-02"), fact("task-03")), List.of()));
        ExecutionSelection selection = ExecutionSelection.partition(
                Set.of("task-01"),
                Set.of("task-02"),
                Set.of("task-03"),
                java.util.Map.of(
                        "task-02", "ROUTING_DEFERRED_FOR_CLARIFICATION",
                        "task-03", "ROUTING_BLOCKED_FOR_CLARIFICATION"));

        SemanticTurnOutcome outcome = coordinator.execute(plan, selection);

        assertEquals(TaskOutcome.TaskExecutionStatus.CANCELLED,
                outcome.getTask("task-02").orElseThrow().getExecutionStatus());
        assertEquals(TaskOutcome.TaskExecutionStatus.BLOCKED,
                outcome.getTask("task-03").orElseThrow().getExecutionStatus());
        assertEquals(List.of("task-01"), executor.getExecutedTaskIds());
    }

    private static ValidatedSemanticTurnPlan validated(SemanticTurnPlan plan) {
        SemanticPlanValidator validator = new SemanticPlanValidator(new PlanFingerprintService());
        return validator.validate(plan, "stp-v1").getValidatedPlan().orElseThrow();
    }

    private static SemanticTurnPlan plan(List<SemanticTask> tasks, List<TaskDependency> dependencies) {
        return new SemanticTurnPlan(
                "plan-private-id",
                "public-v1",
                SemanticTurnPlan.PlanSource.RULE,
                tasks,
                dependencies,
                List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation());
    }

    private static SemanticTask fact(String taskId) {
        SubjectReference subject = SubjectReference.project("project-" + taskId, "public-v1");
        SemanticTaskParameters.PortfolioFact parameters = new SemanticTaskParameters.PortfolioFact(
                subject, Set.of(), "INTERVIEWER");
        return SemanticTask.create(
                taskId,
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                "Describe the public project",
                parameters,
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(),
                List.of(subject));
    }

    private static TaskDependency requiredDependency(String fromTaskId, String toTaskId) {
        return new TaskDependency(
                fromTaskId,
                toTaskId,
                SemanticRoutingTypes.TaskDependencyType.REQUIRES_SUCCESS,
                SemanticRoutingTypes.DependencyOrigin.USER_EXPLICIT);
    }

    private static TaskDependency availableDependency(String fromTaskId, String toTaskId) {
        return new TaskDependency(
                fromTaskId,
                toTaskId,
                SemanticRoutingTypes.TaskDependencyType.USES_AVAILABLE_RESULTS,
                SemanticRoutingTypes.DependencyOrigin.COMPILER_INFERRED);
    }

    private static final class RecordingExecutor implements SemanticTaskExecutor {

        private final Set<String> failedTaskIds;
        private final List<String> executedTaskIds = new ArrayList<>();
        private final java.util.Map<String, List<String>> availableTaskIdsByTask = new java.util.LinkedHashMap<>();

        private RecordingExecutor(Set<String> failedTaskIds) {
            this.failedTaskIds = Set.copyOf(failedTaskIds);
        }

        @Override
        public SemanticRoutingTypes.TaskSourceDomain getSourceDomain() {
            return SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO;
        }

        @Override
        public TaskOutcome execute(SemanticTask task, List<TaskOutcome> availableDependencyOutcomes) {
            executedTaskIds.add(task.getTaskId());
            List<String> availableTaskIds = new ArrayList<>();
            for (TaskOutcome outcome : availableDependencyOutcomes) {
                availableTaskIds.add(outcome.getTaskId());
            }
            availableTaskIdsByTask.put(task.getTaskId(), List.copyOf(availableTaskIds));
            if (failedTaskIds.contains(task.getTaskId())) {
                return TaskOutcome.failed(task.getTaskId(), task.getSourceDomain(), "EXECUTION_PROVIDER_FAILURE");
            }
            return TaskOutcome.answered(
                    task.getTaskId(),
                    task.getSourceDomain(),
                    new TaskResultPayload.SectionResultPayload(List.of("Verified output"), null),
                    TaskResultProvenance.direct(task.getSourceDomain(), List.of(), List.of()),
                    false);
        }

        private List<String> getExecutedTaskIds() {
            return List.copyOf(executedTaskIds);
        }

        private List<String> getAvailableTaskIds(String taskId) {
            return availableTaskIdsByTask.getOrDefault(taskId, List.of());
        }
    }
}
