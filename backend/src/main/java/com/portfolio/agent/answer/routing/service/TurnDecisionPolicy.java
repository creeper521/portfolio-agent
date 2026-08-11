package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.ExecutionSelection;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Converts a trusted plan plus clarification state into exactly one disposition. */
public final class TurnDecisionPolicy {

    public SemanticTurnDecision decide(PlanValidationResult validation, ClarificationRequest clarification) {
        Objects.requireNonNull(validation, "validation");
        if (!validation.isValid()) {
            return SemanticTurnDecision.rejected(Set.of("ROUTING_PLAN_INVALID"));
        }
        return decide(validation.getValidatedPlan().orElseThrow(), clarification);
    }

    SemanticTurnDecision decide(ValidatedSemanticTurnPlan plan, ClarificationRequest clarification) {
        Objects.requireNonNull(plan, "plan");
        if (clarification != null) {
            if (clarification.getScope() != ClarificationRequest.Scope.LOCAL) {
                return SemanticTurnDecision.clarificationRequired(clarification);
            }
            return SemanticTurnDecision.partialReady(plan, allExecutable(plan), clarification);
        }
        if (plan.getConfirmationPolicy().isConfirmationRequired()) {
            return SemanticTurnDecision.confirmationRequired(plan);
        }
        return SemanticTurnDecision.ready(plan, allExecutable(plan));
    }

    private ExecutionSelection allExecutable(ValidatedSemanticTurnPlan plan) {
        Set<String> taskIds = new LinkedHashSet<>();
        plan.getTasks().forEach(task -> taskIds.add(task.getTaskId()));
        return ExecutionSelection.allExecutable(taskIds);
    }
}
