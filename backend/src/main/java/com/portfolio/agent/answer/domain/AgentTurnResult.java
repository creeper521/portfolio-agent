package com.portfolio.agent.answer.domain;

import com.portfolio.agent.answer.routing.domain.PlanConfirmation;
import com.portfolio.agent.answer.routing.domain.SemanticTurnOutcome;
import com.portfolio.agent.answer.routing.domain.SemanticTurnInput;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.service.ClarificationRequest;
import com.portfolio.agent.answer.routing.service.SemanticTurnDecision;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Cohesive answer-core representation of the phase-two semantic turn. */
public final class AgentTurnResult {

    public enum Disposition {
        READY,
        PARTIAL_READY,
        CONFIRMATION_REQUIRED,
        CLARIFICATION_REQUIRED,
        BOUNDARY,
        REJECTED,
        PLAN_INVALIDATED
    }

    private final Disposition disposition;
    private final SemanticTurnPlan plan;
    private final PlanConfirmation.Challenge planConfirmation;
    private final ClarificationRequest clarification;
    private final SemanticTurnOutcome outcome;
    private final PlanConfirmation.PlanInvalidationReason invalidationReason;
    private final SemanticTurnInput.InvalidatedPlanReference invalidatedPlanReference;
    private final Set<String> reasonCodes;
    private final boolean requestUsesStpV1;

    private AgentTurnResult(
            Disposition disposition,
            SemanticTurnPlan plan,
            PlanConfirmation.Challenge planConfirmation,
            ClarificationRequest clarification,
            SemanticTurnOutcome outcome,
            PlanConfirmation.PlanInvalidationReason invalidationReason,
            SemanticTurnInput.InvalidatedPlanReference invalidatedPlanReference,
            Set<String> reasonCodes,
            boolean requestUsesStpV1) {
        this.disposition = Objects.requireNonNull(disposition, "disposition");
        this.plan = plan;
        this.planConfirmation = planConfirmation;
        this.clarification = clarification;
        this.outcome = outcome;
        this.invalidationReason = invalidationReason;
        this.invalidatedPlanReference = invalidatedPlanReference;
        this.reasonCodes = Set.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes"));
        this.requestUsesStpV1 = requestUsesStpV1;
        validateInvariant();
    }

    public static AgentTurnResult confirmationRequired(
            SemanticTurnPlan plan,
            PlanConfirmation.Challenge planConfirmation,
            boolean requestUsesStpV1) {
        return new AgentTurnResult(Disposition.CONFIRMATION_REQUIRED,
                Objects.requireNonNull(plan, "plan"),
                Objects.requireNonNull(planConfirmation, "planConfirmation"), null, null, null,
                null, Set.of(), requestUsesStpV1);
    }

    public static AgentTurnResult ready(SemanticTurnPlan plan, SemanticTurnOutcome outcome) {
        return new AgentTurnResult(Disposition.READY, Objects.requireNonNull(plan, "plan"), null,
                null, Objects.requireNonNull(outcome, "outcome"), null, null, Set.of(), true);
    }

    public static AgentTurnResult partialReady(
            SemanticTurnPlan plan,
            ClarificationRequest clarification,
            SemanticTurnOutcome outcome) {
        return new AgentTurnResult(Disposition.PARTIAL_READY,
                Objects.requireNonNull(plan, "plan"), null,
                Objects.requireNonNull(clarification, "clarification"),
                Objects.requireNonNull(outcome, "outcome"), null, null, Set.of(), true);
    }

    public static AgentTurnResult clarificationRequired(ClarificationRequest clarification) {
        return new AgentTurnResult(Disposition.CLARIFICATION_REQUIRED, null, null,
                Objects.requireNonNull(clarification, "clarification"), null, null, null, Set.of(), true);
    }

    public static AgentTurnResult boundary(Set<String> reasonCodes) {
        return new AgentTurnResult(Disposition.BOUNDARY, null, null, null, null, null,
                null, reasonCodes, true);
    }

    public static AgentTurnResult rejected(Set<String> reasonCodes) {
        return new AgentTurnResult(Disposition.REJECTED, null, null, null, null, null,
                null, reasonCodes, true);
    }

    public static AgentTurnResult planInvalidated(
            PlanConfirmation.PlanInvalidationReason invalidationReason) {
        return planInvalidated(invalidationReason, null);
    }

    public static AgentTurnResult planInvalidated(
            PlanConfirmation.PlanInvalidationReason invalidationReason,
            SemanticTurnInput.InvalidatedPlanReference invalidatedPlanReference) {
        if (invalidationReason == null
                || invalidationReason == PlanConfirmation.PlanInvalidationReason.NONE) {
            throw new IllegalArgumentException("an invalidated result requires an invalidation reason");
        }
        return new AgentTurnResult(Disposition.PLAN_INVALIDATED, null, null, null, null,
                invalidationReason, invalidatedPlanReference, Set.of(), true);
    }

    public static AgentTurnResult fromDecision(
            SemanticTurnDecision decision,
            PlanConfirmation.Challenge challenge,
            SemanticTurnOutcome outcome,
            boolean requestUsesStpV1) {
        Objects.requireNonNull(decision, "decision");
        SemanticTurnPlan plan = decision.getValidatedPlan().map(value -> value.getPlan()).orElse(null);
        return switch (decision.getDisposition()) {
            case READY -> ready(plan, Objects.requireNonNull(outcome, "outcome"));
            case PARTIAL_READY -> partialReady(plan,
                    decision.getClarification().orElseThrow(), Objects.requireNonNull(outcome, "outcome"));
            case CONFIRMATION_REQUIRED -> confirmationRequired(plan,
                    Objects.requireNonNull(challenge, "challenge"), requestUsesStpV1);
            case CLARIFICATION_REQUIRED -> clarificationRequired(decision.getClarification().orElseThrow());
            case BOUNDARY -> boundary(decision.getReasonCodes());
            case REJECTED -> rejected(decision.getReasonCodes());
        };
    }

    public Disposition getDisposition() { return disposition; }
    public Optional<SemanticTurnPlan> getPlan() { return Optional.ofNullable(plan); }
    public Optional<PlanConfirmation.Challenge> getPlanConfirmation() {
        return Optional.ofNullable(planConfirmation);
    }
    public Optional<ClarificationRequest> getClarification() { return Optional.ofNullable(clarification); }
    public Optional<SemanticTurnOutcome> getOutcome() { return Optional.ofNullable(outcome); }
    public Optional<PlanConfirmation.PlanInvalidationReason> getInvalidationReason() {
        return Optional.ofNullable(invalidationReason);
    }
    public Optional<SemanticTurnInput.InvalidatedPlanReference> getInvalidatedPlanReference() {
        return Optional.ofNullable(invalidatedPlanReference);
    }
    public Set<String> getReasonCodes() { return reasonCodes; }
    public boolean isRequestUsesStpV1() { return requestUsesStpV1; }
    public boolean isConfirmationRequired() { return disposition == Disposition.CONFIRMATION_REQUIRED; }

    private void validateInvariant() {
        switch (disposition) {
            case READY -> require(plan != null && outcome != null && clarification == null
                    && planConfirmation == null, "ready result must carry plan and outcome only");
            case PARTIAL_READY -> require(plan != null && outcome != null && clarification != null
                    && planConfirmation == null, "partial result must carry plan, outcome and clarification");
            case CONFIRMATION_REQUIRED -> require(plan != null && planConfirmation != null && outcome == null
                    && clarification == null, "confirmation result must carry plan and challenge only");
            case CLARIFICATION_REQUIRED -> require(plan == null && clarification != null && outcome == null
                    && planConfirmation == null, "clarification result must carry clarification only");
            case BOUNDARY, REJECTED -> require(plan == null && clarification == null && outcome == null
                    && planConfirmation == null && !reasonCodes.isEmpty(),
                    "boundary and rejected results require safe reason codes only");
            case PLAN_INVALIDATED -> require(plan == null && clarification == null && outcome == null
                    && planConfirmation == null && invalidationReason != null,
                    "invalidated result must carry an invalidation reason only");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
