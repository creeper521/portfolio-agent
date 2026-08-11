package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.routing.domain.SemanticTurnOutcome;

import java.util.Objects;

/** Outcome envelope required for READY/PARTIAL_READY responses. */
public final class AgentTurnOutcomeResponse {

    private final SemanticTurnOutcome.PlanOutcome planOutcome;
    private final TaskSummaryResponse taskSummary;

    public AgentTurnOutcomeResponse(
            SemanticTurnOutcome.PlanOutcome planOutcome,
            TaskSummaryResponse taskSummary) {
        this.planOutcome = Objects.requireNonNull(planOutcome, "planOutcome");
        this.taskSummary = taskSummary;
    }

    public SemanticTurnOutcome.PlanOutcome getPlanOutcome() { return planOutcome; }
    public TaskSummaryResponse getTaskSummary() { return taskSummary; }
}
