package com.portfolio.agent.answer.intelligence.execution.service;

import com.portfolio.agent.answer.intelligence.execution.domain.ExecutionDisplayPlan;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTurnOutcome;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Projects only the safe final state of a semantic plan for the public API. */
public final class ExecutionDisplayPlanProjector {

    public ExecutionDisplayPlan project(SemanticTurnPlan plan, SemanticTurnOutcome outcome) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(outcome, "outcome");
        List<ExecutionDisplayPlan.TaskDisplay> tasks = new ArrayList<>();
        for (int index = 0; index < plan.getTasks().size(); index++) {
            SemanticTask task = plan.getTasks().get(index);
            TaskOutcome taskOutcome = outcome.getTask(task.getTaskId()).orElse(null);
            tasks.add(new ExecutionDisplayPlan.TaskDisplay(
                    index, task.getTaskType(), taskStatus(taskOutcome), stages(taskOutcome)));
        }
        return new ExecutionDisplayPlan(overallStatus(outcome, tasks), tasks);
    }

    private static ExecutionDisplayPlan.OverallStatus overallStatus(
            SemanticTurnOutcome outcome, List<ExecutionDisplayPlan.TaskDisplay> tasks) {
        if (tasks.stream().anyMatch(task -> task.getFinalStatus()
                == ExecutionDisplayPlan.TaskDisplayStatus.FAILED)) {
            return ExecutionDisplayPlan.OverallStatus.FAILED;
        }
        if (tasks.stream().anyMatch(task -> task.getFinalStatus()
                == ExecutionDisplayPlan.TaskDisplayStatus.PARTIAL)) {
            return ExecutionDisplayPlan.OverallStatus.PARTIAL;
        }
        if (tasks.stream().anyMatch(task -> task.getFinalStatus()
                == ExecutionDisplayPlan.TaskDisplayStatus.COMPLETED)) {
            return outcome.getPlanOutcome() == SemanticTurnOutcome.PlanOutcome.PARTIAL
                    ? ExecutionDisplayPlan.OverallStatus.PARTIAL
                    : ExecutionDisplayPlan.OverallStatus.COMPLETED;
        }
        return ExecutionDisplayPlan.OverallStatus.SKIPPED;
    }

    private static ExecutionDisplayPlan.TaskDisplayStatus taskStatus(TaskOutcome outcome) {
        if (outcome == null) {
            return ExecutionDisplayPlan.TaskDisplayStatus.SKIPPED;
        }
        if (outcome.getExecutionStatus() == TaskOutcome.TaskExecutionStatus.FAILED) {
            return ExecutionDisplayPlan.TaskDisplayStatus.FAILED;
        }
        if (outcome.hasRenderablePayload()) {
            return outcome.getResolution() == TaskOutcome.TaskResolution.PARTIALLY_ANSWERED
                    ? ExecutionDisplayPlan.TaskDisplayStatus.PARTIAL
                    : ExecutionDisplayPlan.TaskDisplayStatus.COMPLETED;
        }
        return switch (outcome.getResolution()) {
            case PARTIALLY_ANSWERED -> ExecutionDisplayPlan.TaskDisplayStatus.PARTIAL;
            case PRESENTATION_BLOCKED -> ExecutionDisplayPlan.TaskDisplayStatus.SKIPPED;
            case REJECTED, NOT_SUPPORTED, EMPTY, CAPABILITY_UNAVAILABLE,
                    DEPENDENCY_UNAVAILABLE, NOT_EXECUTED_BUDGET, BOUNDARY, NOT_APPLICABLE
                    -> ExecutionDisplayPlan.TaskDisplayStatus.SKIPPED;
            case ANSWERED -> outcome.getExecutionStatus() == TaskOutcome.TaskExecutionStatus.SUCCEEDED
                    ? ExecutionDisplayPlan.TaskDisplayStatus.COMPLETED
                    : ExecutionDisplayPlan.TaskDisplayStatus.SKIPPED;
        };
    }

    private static List<ExecutionDisplayPlan.Stage> stages(TaskOutcome outcome) {
        ExecutionDisplayPlan.TaskDisplayStatus status = taskStatus(outcome);
        ExecutionDisplayPlan.StageStatus scope = outcome == null
                ? ExecutionDisplayPlan.StageStatus.SKIPPED : stage(status);
        ExecutionDisplayPlan.StageStatus terminal = outcome == null || !outcome.hasRenderablePayload()
                ? (status == ExecutionDisplayPlan.TaskDisplayStatus.FAILED
                    ? ExecutionDisplayPlan.StageStatus.FAILED : ExecutionDisplayPlan.StageStatus.SKIPPED)
                : stage(status);
        return List.of(
                new ExecutionDisplayPlan.Stage(ExecutionDisplayPlan.StageCode.SCOPE_CONFIRMED,
                        "范围已确认", scope),
                new ExecutionDisplayPlan.Stage(ExecutionDisplayPlan.StageCode.MATERIALS_RETRIEVED,
                        "材料已取得", terminal),
                new ExecutionDisplayPlan.Stage(ExecutionDisplayPlan.StageCode.EVIDENCE_VALIDATED,
                        "证据已核验", terminal),
                new ExecutionDisplayPlan.Stage(ExecutionDisplayPlan.StageCode.RESULT_COMPOSED,
                        "结果已整理", terminal));
    }

    private static ExecutionDisplayPlan.StageStatus stage(
            ExecutionDisplayPlan.TaskDisplayStatus status) {
        return switch (status) {
            case COMPLETED -> ExecutionDisplayPlan.StageStatus.COMPLETED;
            case PARTIAL -> ExecutionDisplayPlan.StageStatus.PARTIAL;
            case SKIPPED -> ExecutionDisplayPlan.StageStatus.SKIPPED;
            case FAILED -> ExecutionDisplayPlan.StageStatus.FAILED;
        };
    }
}
