package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.intelligence.execution.domain.ExecutionDisplayPlan;

import java.util.List;

/** Public final-only execution snapshot; no tool, adapter, budget, or internal id is exposed. */
public final class ExecutionDisplayPlanResponse {
    private final String contractVersion;
    private final String snapshotType;
    private final String overallStatus;
    private final List<Task> tasks;

    public ExecutionDisplayPlanResponse(
            String contractVersion, String snapshotType, String overallStatus, List<Task> tasks) {
        this.contractVersion = contractVersion;
        this.snapshotType = snapshotType;
        this.overallStatus = overallStatus;
        this.tasks = List.copyOf(tasks);
    }

    public static ExecutionDisplayPlanResponse from(ExecutionDisplayPlan plan) {
        return new ExecutionDisplayPlanResponse(
                plan.getContractVersion(), plan.getSnapshotType().name(), plan.getOverallStatus().name(),
                plan.getTasks().stream().map(Task::from).toList());
    }

    public String getContractVersion() { return contractVersion; }
    public String getSnapshotType() { return snapshotType; }
    public String getOverallStatus() { return overallStatus; }
    public List<Task> getTasks() { return tasks; }

    public static final class Task {
        private final String displayIndex;
        private final String finalStatus;
        private final List<Stage> stages;

        private Task(String displayIndex, String finalStatus, List<Stage> stages) {
            this.displayIndex = displayIndex;
            this.finalStatus = finalStatus;
            this.stages = List.copyOf(stages);
        }

        private static Task from(ExecutionDisplayPlan.TaskDisplay task) {
            return new Task(String.format("%02d", task.getDisplayIndex() + 1),
                    task.getFinalStatus().name(), task.getStages().stream().map(Stage::from).toList());
        }

        public String getDisplayIndex() { return displayIndex; }
        public String getFinalStatus() { return finalStatus; }
        public List<Stage> getStages() { return stages; }
    }

    public static final class Stage {
        private final String code;
        private final String label;
        private final String status;

        private Stage(String code, String label, String status) {
            this.code = code;
            this.label = label;
            this.status = status;
        }

        private static Stage from(ExecutionDisplayPlan.Stage stage) {
            return new Stage(stage.getCode().name(), stage.getLabel(), stage.getStatus().name());
        }

        public String getCode() { return code; }
        public String getLabel() { return label; }
        public String getStatus() { return status; }
    }
}
