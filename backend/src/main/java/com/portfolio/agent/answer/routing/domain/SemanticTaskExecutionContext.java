package com.portfolio.agent.answer.routing.domain;

import java.util.List;
import java.util.Objects;

/** The only input seam from P2 into a task executor. */
public final class SemanticTaskExecutionContext {

    private final SemanticTask semanticTask;
    private final List<PlanExclusion> applicableExclusions;
    private final List<TaskOutcome> dependencyOutcomes;
    private final String expectedContentVersion;
    private final TaskExecutionAllowance taskExecutionAllowance;
    private final List<AuthorizedContextReference> authorizedContextReferences;
    private final boolean modelExpressionAttemptAllowed;
    private final boolean presetRequest;

    public SemanticTaskExecutionContext(
            SemanticTask semanticTask,
            List<PlanExclusion> applicableExclusions,
            List<TaskOutcome> dependencyOutcomes,
            String expectedContentVersion,
            TaskExecutionAllowance taskExecutionAllowance,
            List<AuthorizedContextReference> authorizedContextReferences) {
        this(semanticTask, applicableExclusions, dependencyOutcomes, expectedContentVersion,
                taskExecutionAllowance, authorizedContextReferences, false, false);
    }

    public SemanticTaskExecutionContext(
            SemanticTask semanticTask,
            List<PlanExclusion> applicableExclusions,
            List<TaskOutcome> dependencyOutcomes,
            String expectedContentVersion,
            TaskExecutionAllowance taskExecutionAllowance,
            List<AuthorizedContextReference> authorizedContextReferences,
            boolean modelExpressionAttemptAllowed,
            boolean presetRequest) {
        this.semanticTask = Objects.requireNonNull(semanticTask, "semanticTask");
        this.applicableExclusions = List.copyOf(
                Objects.requireNonNull(applicableExclusions, "applicableExclusions"));
        this.dependencyOutcomes = List.copyOf(
                Objects.requireNonNull(dependencyOutcomes, "dependencyOutcomes"));
        this.expectedContentVersion = requireText(expectedContentVersion, "expectedContentVersion");
        this.taskExecutionAllowance = Objects.requireNonNull(
                taskExecutionAllowance, "taskExecutionAllowance");
        this.authorizedContextReferences = List.copyOf(
                Objects.requireNonNull(authorizedContextReferences, "authorizedContextReferences"));
        this.modelExpressionAttemptAllowed = modelExpressionAttemptAllowed;
        this.presetRequest = presetRequest;
    }

    public SemanticTask getSemanticTask() {
        return semanticTask;
    }

    public List<PlanExclusion> getApplicableExclusions() {
        return applicableExclusions;
    }

    public List<TaskOutcome> getDependencyOutcomes() {
        return dependencyOutcomes;
    }

    public String getExpectedContentVersion() {
        return expectedContentVersion;
    }

    public TaskExecutionAllowance getTaskExecutionAllowance() {
        return taskExecutionAllowance;
    }

    public List<AuthorizedContextReference> getAuthorizedContextReferences() {
        return authorizedContextReferences;
    }

    /** Request-local P4 allowance assigned once in stable topological order. */
    public boolean isModelExpressionAttemptAllowed() {
        return modelExpressionAttemptAllowed;
    }

    public boolean isPresetRequest() {
        return presetRequest;
    }

    @Override
    public String toString() {
        return "SemanticTaskExecutionContext{taskType=" + semanticTask.getTaskType()
                + ", exclusionCount=" + applicableExclusions.size()
                + ", dependencyOutcomeCount=" + dependencyOutcomes.size()
                + ", authorizedContextReferenceCount=" + authorizedContextReferences.size() + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
