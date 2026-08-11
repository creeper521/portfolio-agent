package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable final execution result for one internal semantic task. */
public final class TaskOutcome {

    public enum TaskExecutionStatus {
        NOT_STARTED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        BLOCKED,
        CANCELLED
    }

    public enum TaskResolution {
        ANSWERED,
        NOT_SUPPORTED,
        EMPTY,
        REJECTED,
        CAPABILITY_UNAVAILABLE,
        BOUNDARY,
        NOT_APPLICABLE
    }

    public enum TaskEvidenceState {
        SUFFICIENT,
        PARTIAL,
        INSUFFICIENT,
        NOT_APPLICABLE
    }

    private final String taskId;
    private final TaskExecutionStatus executionStatus;
    private final TaskResolution resolution;
    private final TaskEvidenceState evidenceState;
    private final boolean degraded;
    private final Set<String> reasonCodes;
    private final String resultReference;
    private final TaskSourceDomain sourceDomain;
    private final TaskResultProvenance provenance;
    private final TaskResultPayload resultPayload;

    private TaskOutcome(
            String taskId,
            TaskExecutionStatus executionStatus,
            TaskResolution resolution,
            TaskEvidenceState evidenceState,
            boolean degraded,
            Set<String> reasonCodes,
            String resultReference,
            TaskSourceDomain sourceDomain,
            TaskResultProvenance provenance,
            TaskResultPayload resultPayload) {
        this.taskId = requireText(taskId, "taskId");
        this.executionStatus = Objects.requireNonNull(executionStatus, "executionStatus");
        this.resolution = Objects.requireNonNull(resolution, "resolution");
        this.evidenceState = Objects.requireNonNull(evidenceState, "evidenceState");
        this.degraded = degraded;
        this.reasonCodes = copyReasonCodes(reasonCodes);
        this.resultReference = optionalText(resultReference);
        this.sourceDomain = Objects.requireNonNull(sourceDomain, "sourceDomain");
        this.provenance = provenance;
        this.resultPayload = resultPayload;
        validate();
    }

    public static TaskOutcome create(
            String taskId,
            TaskExecutionStatus executionStatus,
            TaskResolution resolution,
            TaskEvidenceState evidenceState,
            boolean degraded,
            Set<String> reasonCodes,
            String resultReference,
            TaskSourceDomain sourceDomain,
            TaskResultProvenance provenance,
            TaskResultPayload resultPayload) {
        return new TaskOutcome(
                taskId, executionStatus, resolution, evidenceState, degraded, reasonCodes,
                resultReference, sourceDomain, provenance, resultPayload);
    }

    public static TaskOutcome answered(
            String taskId,
            TaskSourceDomain sourceDomain,
            TaskResultPayload resultPayload,
            TaskResultProvenance provenance,
            boolean degraded) {
        return create(
                taskId,
                TaskExecutionStatus.SUCCEEDED,
                TaskResolution.ANSWERED,
                TaskEvidenceState.SUFFICIENT,
                degraded,
                Set.of(),
                null,
                sourceDomain,
                provenance,
                resultPayload);
    }

    public static TaskOutcome notSupported(
            String taskId,
            TaskSourceDomain sourceDomain,
            boolean degraded,
            String reasonCode) {
        return create(
                taskId,
                TaskExecutionStatus.SUCCEEDED,
                TaskResolution.NOT_SUPPORTED,
                TaskEvidenceState.INSUFFICIENT,
                degraded,
                Set.of(reasonCode),
                null,
                sourceDomain,
                null,
                null);
    }

    public static TaskOutcome capabilityUnavailable(
            String taskId, TaskSourceDomain sourceDomain, String reasonCode) {
        return create(
                taskId,
                TaskExecutionStatus.SUCCEEDED,
                TaskResolution.CAPABILITY_UNAVAILABLE,
                TaskEvidenceState.NOT_APPLICABLE,
                false,
                Set.of(reasonCode),
                null,
                sourceDomain,
                null,
                null);
    }

    public static TaskOutcome failed(String taskId, TaskSourceDomain sourceDomain, String reasonCode) {
        return create(
                taskId,
                TaskExecutionStatus.FAILED,
                TaskResolution.NOT_APPLICABLE,
                TaskEvidenceState.NOT_APPLICABLE,
                false,
                Set.of(reasonCode),
                null,
                sourceDomain,
                null,
                null);
    }

    public static TaskOutcome blocked(String taskId, TaskSourceDomain sourceDomain, String reasonCode) {
        return create(
                taskId,
                TaskExecutionStatus.BLOCKED,
                TaskResolution.NOT_APPLICABLE,
                TaskEvidenceState.NOT_APPLICABLE,
                false,
                Set.of(reasonCode),
                null,
                sourceDomain,
                null,
                null);
    }

    public static TaskOutcome cancelled(String taskId, TaskSourceDomain sourceDomain, String reasonCode) {
        return create(
                taskId,
                TaskExecutionStatus.CANCELLED,
                TaskResolution.NOT_APPLICABLE,
                TaskEvidenceState.NOT_APPLICABLE,
                false,
                Set.of(reasonCode),
                null,
                sourceDomain,
                null,
                null);
    }

    public String getTaskId() {
        return taskId;
    }

    public TaskExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public TaskResolution getResolution() {
        return resolution;
    }

    public TaskEvidenceState getEvidenceState() {
        return evidenceState;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public Set<String> getReasonCodes() {
        return reasonCodes;
    }

    public Optional<String> getResultReference() {
        return Optional.ofNullable(resultReference);
    }

    public TaskSourceDomain getSourceDomain() {
        return sourceDomain;
    }

    public Optional<TaskResultProvenance> getProvenance() {
        return Optional.ofNullable(provenance);
    }

    public Optional<TaskResultPayload> getResultPayload() {
        return Optional.ofNullable(resultPayload);
    }

    public boolean hasRenderablePayload() {
        return executionStatus == TaskExecutionStatus.SUCCEEDED
                && resolution == TaskResolution.ANSWERED
                && evidenceState != TaskEvidenceState.INSUFFICIENT
                && resultPayload != null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaskOutcome that)) {
            return false;
        }
        return degraded == that.degraded
                && Objects.equals(taskId, that.taskId)
                && executionStatus == that.executionStatus
                && resolution == that.resolution
                && evidenceState == that.evidenceState
                && Objects.equals(reasonCodes, that.reasonCodes)
                && Objects.equals(resultReference, that.resultReference)
                && sourceDomain == that.sourceDomain
                && Objects.equals(provenance, that.provenance)
                && Objects.equals(resultPayload, that.resultPayload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, executionStatus, resolution, evidenceState, degraded, reasonCodes,
                resultReference, sourceDomain, provenance, resultPayload);
    }

    @Override
    public String toString() {
        return "TaskOutcome{executionStatus=" + executionStatus
                + ", resolution=" + resolution
                + ", evidenceState=" + evidenceState
                + ", degraded=" + degraded
                + ", reasonCount=" + reasonCodes.size()
                + ", sourceDomain=" + sourceDomain
                + ", hasPayload=" + (resultPayload != null) + '}';
    }

    private void validate() {
        if (resultPayload != null
                && (executionStatus != TaskExecutionStatus.SUCCEEDED || resolution != TaskResolution.ANSWERED)) {
            throw new IllegalArgumentException("renderable payload requires succeeded answered outcome");
        }
        if (resultPayload != null && provenance == null) {
            throw new IllegalArgumentException("renderable payload requires provenance");
        }
        if (resultPayload != null && evidenceState == TaskEvidenceState.INSUFFICIENT) {
            throw new IllegalArgumentException("evidence-insufficient outcome must not carry renderable payload");
        }
        if (executionStatus == TaskExecutionStatus.BLOCKED
                && evidenceState != TaskEvidenceState.NOT_APPLICABLE) {
            throw new IllegalArgumentException("blocked outcome requires not applicable evidence state");
        }
        if ((executionStatus == TaskExecutionStatus.FAILED
                || executionStatus == TaskExecutionStatus.BLOCKED
                || executionStatus == TaskExecutionStatus.CANCELLED)
                && resolution != TaskResolution.NOT_APPLICABLE) {
            throw new IllegalArgumentException("terminal non-success outcome requires not applicable resolution");
        }
        if (executionStatus == TaskExecutionStatus.FAILED && resolution == TaskResolution.EMPTY) {
            throw new IllegalArgumentException("failed outcome must not be empty");
        }
        if (sourceDomain == TaskSourceDomain.SYNTHESIS && provenance != null
                && provenance.getDerivationType() != TaskResultProvenance.DerivationType.SYNTHESIZED) {
            throw new IllegalArgumentException("synthesis outcome requires synthesized provenance");
        }
        if (sourceDomain != TaskSourceDomain.SYNTHESIS && provenance != null
                && provenance.getDerivationType() != TaskResultProvenance.DerivationType.DIRECT) {
            throw new IllegalArgumentException("non-synthesis outcome requires direct provenance");
        }
    }

    private static Set<String> copyReasonCodes(Set<String> reasonCodes) {
        Objects.requireNonNull(reasonCodes, "reasonCodes");
        Set<String> copied = new LinkedHashSet<>();
        for (String reasonCode : reasonCodes) {
            String normalized = requireText(reasonCode, "reasonCode");
            if (!normalized.matches("[A-Z]+_[A-Z0-9_]+")) {
                throw new IllegalArgumentException("reasonCode must be a public uppercase code");
            }
            copied.add(normalized);
        }
        return Set.copyOf(copied);
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireText(String value, String name) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }
}
