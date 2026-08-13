package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.context.domain.ConversationContinuationStatus;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;

import java.util.List;
import java.util.Objects;

/** Typed runtime result for Context-aware execution and its asymmetric persistence outcome. */
public final class ConversationTurnExecutionResult {
    public enum Status { SUCCEEDED, FAILED }

    private final Status status;
    private final List<TaskOutcome> taskOutcomes;
    private final ConversationContinuationStatus continuationStatus;
    private final String reasonCode;

    public ConversationTurnExecutionResult(
            Status status,
            List<TaskOutcome> taskOutcomes,
            ConversationContinuationStatus continuationStatus,
            String reasonCode) {
        this.status = Objects.requireNonNull(status, "status");
        this.taskOutcomes = List.copyOf(Objects.requireNonNull(taskOutcomes, "taskOutcomes"));
        this.continuationStatus = Objects.requireNonNull(continuationStatus, "continuationStatus");
        this.reasonCode = reasonCode;
    }

    public Status getStatus() { return status; }
    public List<TaskOutcome> getTaskOutcomes() { return taskOutcomes; }
    public ConversationContinuationStatus getContinuationStatus() { return continuationStatus; }
    public String getReasonCode() { return reasonCode; }
}
