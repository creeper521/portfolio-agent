package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.domain.AgentTurnResult;

import java.util.List;
import java.util.Objects;

/** Versioned semantic-turn wire response. Internal routing dispositions are normalized at the mapper boundary. */
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
    private final ExecutionDisplayPlanResponse execution;

    public AgentTurnResponse(
            AgentTurnResult.Disposition disposition,
            DisplayPlanResponse plan,
            PlanChangeResponse planChange,
            PlanConfirmationResponse planConfirmation,
            ClarificationResponse clarification,
            AgentTurnOutcomeResponse outcome,
            List<CompletedTaskResponse> completedTasks) {
        this(disposition, plan, planChange, planConfirmation, clarification, outcome, completedTasks, null);
    }

    public AgentTurnResponse(
            AgentTurnResult.Disposition disposition,
            DisplayPlanResponse plan,
            PlanChangeResponse planChange,
            PlanConfirmationResponse planConfirmation,
            ClarificationResponse clarification,
            AgentTurnOutcomeResponse outcome,
            List<CompletedTaskResponse> completedTasks,
            ExecutionDisplayPlanResponse execution) {
        this(AgentTurnResponse.CONTRACT_VERSION, disposition, plan, planChange, planConfirmation,
                clarification, outcome, completedTasks, execution);
    }

    public AgentTurnResponse(
            String contractVersion,
            AgentTurnResult.Disposition disposition,
            DisplayPlanResponse plan,
            PlanChangeResponse planChange,
            PlanConfirmationResponse planConfirmation,
            ClarificationResponse clarification,
            AgentTurnOutcomeResponse outcome,
            List<CompletedTaskResponse> completedTasks,
            ExecutionDisplayPlanResponse execution) {
        this.contractVersion = requireContract(contractVersion);
        this.disposition = Objects.requireNonNull(disposition, "disposition");
        this.plan = plan;
        this.planChange = planChange;
        this.planConfirmation = planConfirmation;
        this.clarification = clarification;
        this.outcome = outcome;
        this.completedTasks = completedTasks == null ? null : List.copyOf(completedTasks);
        this.execution = execution;
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ExecutionDisplayPlanResponse getExecution() { return execution; }

    private static String requireContract(String value) {
        if (!"stp-v1".equals(value) && !"stp-v2".equals(value)) {
            throw new IllegalArgumentException("unsupported agent turn contract");
        }
        return value;
    }
}
