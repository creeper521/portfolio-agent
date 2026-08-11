package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.domain.AgentTurnResult;

import java.util.List;
import java.util.Objects;

/** Stable stp-v1 wire response. Internal routing dispositions are normalized at the mapper boundary. */
public final class AgentTurnResponse {

    private static final String CONTRACT_VERSION = "stp-v1";

    private final String contractVersion;
    private final AgentTurnResult.Disposition disposition;
    private final DisplayPlanResponse plan;
    private final PlanChangeResponse planChange;
    private final PlanConfirmationResponse planConfirmation;
    private final ClarificationResponse clarification;
    private final AgentTurnOutcomeResponse outcome;
    private final List<CompletedTaskResponse> completedTasks;

    public AgentTurnResponse(
            AgentTurnResult.Disposition disposition,
            DisplayPlanResponse plan,
            PlanChangeResponse planChange,
            PlanConfirmationResponse planConfirmation,
            ClarificationResponse clarification,
            AgentTurnOutcomeResponse outcome,
            List<CompletedTaskResponse> completedTasks) {
        this.contractVersion = CONTRACT_VERSION;
        this.disposition = Objects.requireNonNull(disposition, "disposition");
        this.plan = plan;
        this.planChange = planChange;
        this.planConfirmation = planConfirmation;
        this.clarification = clarification;
        this.outcome = outcome;
        this.completedTasks = completedTasks == null ? null : List.copyOf(completedTasks);
    }

    public String getContractVersion() { return contractVersion; }
    public AgentTurnResult.Disposition getDisposition() { return disposition; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public DisplayPlanResponse getPlan() { return plan; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public PlanChangeResponse getPlanChange() { return planChange; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public PlanConfirmationResponse getPlanConfirmation() { return planConfirmation; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ClarificationResponse getClarification() { return clarification; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public AgentTurnOutcomeResponse getOutcome() { return outcome; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<CompletedTaskResponse> getCompletedTasks() { return completedTasks; }
}
