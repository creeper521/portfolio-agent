package com.portfolio.agent.answer.intelligence.execution.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;

import java.util.List;
import java.util.Objects;

/** Safe final-only execution snapshot projected for the public response. */
public final class ExecutionDisplayPlan {

    public static final String CONTRACT_VERSION = "p3-display-v1";

    public enum SnapshotType {
        FINAL
    }

    public enum OverallStatus {
        COMPLETED,
        PARTIAL,
        SKIPPED,
        FAILED
    }

    public enum StageCode {
        SCOPE_CONFIRMED,
        MATERIALS_RETRIEVED,
        EVIDENCE_VALIDATED,
        RESULT_COMPOSED
    }

    public enum StageStatus {
        COMPLETED,
        PARTIAL,
        SKIPPED,
        FAILED
    }

    public enum TaskDisplayStatus {
        COMPLETED,
        PARTIAL,
        SKIPPED,
        FAILED
    }

    private final SnapshotType snapshotType;
    private final OverallStatus overallStatus;
    private final List<TaskDisplay> tasks;

    public ExecutionDisplayPlan(OverallStatus overallStatus, List<TaskDisplay> tasks) {
        this.snapshotType = SnapshotType.FINAL;
        this.overallStatus = Objects.requireNonNull(overallStatus, "overallStatus");
        this.tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
    }

    public String getContractVersion() {
        return CONTRACT_VERSION;
    }

    public SnapshotType getSnapshotType() {
        return snapshotType;
    }

    public OverallStatus getOverallStatus() {
        return overallStatus;
    }

    public List<TaskDisplay> getTasks() {
        return tasks;
    }

    @Override
    public String toString() {
        return "ExecutionDisplayPlan{snapshotType=FINAL, overallStatus=" + overallStatus
                + ", taskCount=" + tasks.size() + '}';
    }

    public static final class TaskDisplay {

        private final int displayIndex;
        private final SemanticTaskType taskType;
        private final TaskDisplayStatus finalStatus;
        private final List<Stage> stages;

        public TaskDisplay(
                int displayIndex, SemanticTaskType taskType,
                TaskDisplayStatus finalStatus, List<Stage> stages) {
            if (displayIndex < 0) {
                throw new IllegalArgumentException("displayIndex must not be negative");
            }
            this.displayIndex = displayIndex;
            this.taskType = Objects.requireNonNull(taskType, "taskType");
            this.finalStatus = Objects.requireNonNull(finalStatus, "finalStatus");
            this.stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
            if (this.stages.size() != 4) {
                throw new IllegalArgumentException("a task must contain four stages");
            }
        }

        public int getDisplayIndex() {
            return displayIndex;
        }

        public SemanticTaskType getTaskType() {
            return taskType;
        }

        public TaskDisplayStatus getFinalStatus() {
            return finalStatus;
        }

        public List<Stage> getStages() {
            return stages;
        }
    }

    public static final class Stage {

        private final StageCode code;
        private final String label;
        private final StageStatus status;

        public Stage(StageCode code, String label, StageStatus status) {
            this.code = Objects.requireNonNull(code, "code");
            this.label = requireText(label, "label");
            this.status = Objects.requireNonNull(status, "status");
        }

        public StageCode getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        public StageStatus getStatus() {
            return status;
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " is required");
            }
            return value.trim();
        }
    }
}
