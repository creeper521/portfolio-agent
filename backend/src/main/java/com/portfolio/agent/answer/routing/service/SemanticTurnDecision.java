package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.ExecutionSelection;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Mutually exclusive routing result. Diagnostics remain controlled reason codes only. */
public final class SemanticTurnDecision {

    private final Disposition disposition;
    private final ValidatedSemanticTurnPlan validatedPlan;
    private final ExecutionSelection executionSelection;
    private final ClarificationRequest clarification;
    private final Set<String> reasonCodes;

    private SemanticTurnDecision(
            Disposition disposition,
            ValidatedSemanticTurnPlan validatedPlan,
            ExecutionSelection executionSelection,
            ClarificationRequest clarification,
            Set<String> reasonCodes) {
        this.disposition = Objects.requireNonNull(disposition, "disposition");
        this.validatedPlan = validatedPlan;
        this.executionSelection = executionSelection;
        this.clarification = clarification;
        this.reasonCodes = copyReasonCodes(reasonCodes);
        validateInvariant();
    }

    static SemanticTurnDecision ready(ValidatedSemanticTurnPlan plan, ExecutionSelection selection) {
        return new SemanticTurnDecision(Disposition.READY, plan, selection, null, Set.of());
    }

    static SemanticTurnDecision partialReady(
            ValidatedSemanticTurnPlan plan, ExecutionSelection selection, ClarificationRequest clarification) {
        return new SemanticTurnDecision(Disposition.PARTIAL_READY, plan, selection, clarification, Set.of());
    }

    static SemanticTurnDecision confirmationRequired(ValidatedSemanticTurnPlan plan) {
        return new SemanticTurnDecision(Disposition.CONFIRMATION_REQUIRED, plan, null, null, Set.of());
    }

    static SemanticTurnDecision clarificationRequired(ClarificationRequest clarification) {
        return new SemanticTurnDecision(Disposition.CLARIFICATION_REQUIRED, null, null, clarification, Set.of());
    }

    static SemanticTurnDecision boundary(Set<String> reasonCodes) {
        return new SemanticTurnDecision(Disposition.BOUNDARY, null, null, null, reasonCodes);
    }

    static SemanticTurnDecision rejected(Set<String> reasonCodes) {
        return new SemanticTurnDecision(Disposition.REJECTED, null, null, null, reasonCodes);
    }

    public Disposition getDisposition() { return disposition; }
    public Optional<ValidatedSemanticTurnPlan> getValidatedPlan() { return Optional.ofNullable(validatedPlan); }
    public Optional<ExecutionSelection> getExecutionSelection() { return Optional.ofNullable(executionSelection); }
    public Optional<ClarificationRequest> getClarification() { return Optional.ofNullable(clarification); }
    public Set<String> getReasonCodes() { return reasonCodes; }

    @Override
    public String toString() {
        return "SemanticTurnDecision{disposition=" + disposition + ", hasPlan=" + (validatedPlan != null)
                + ", hasSelection=" + (executionSelection != null) + ", hasClarification="
                + (clarification != null) + ", reasonCodeCount=" + reasonCodes.size() + '}';
    }

    public enum Disposition {
        READY,
        PARTIAL_READY,
        CONFIRMATION_REQUIRED,
        CLARIFICATION_REQUIRED,
        BOUNDARY,
        REJECTED
    }

    private void validateInvariant() {
        switch (disposition) {
            case READY -> require(validatedPlan != null && executionSelection != null && clarification == null
                    && reasonCodes.isEmpty(), "ready decision must contain only a plan and selection");
            case PARTIAL_READY -> require(validatedPlan != null && executionSelection != null && clarification != null
                    && clarification.getScope() == ClarificationRequest.Scope.LOCAL && reasonCodes.isEmpty(),
                    "partial decision must contain a local clarification and a safe selection");
            case CONFIRMATION_REQUIRED -> require(validatedPlan != null && executionSelection == null && clarification == null
                    && reasonCodes.isEmpty(), "confirmation decision must contain only a plan");
            case CLARIFICATION_REQUIRED -> require(validatedPlan == null && executionSelection == null && clarification != null
                    && clarification.getScope() == ClarificationRequest.Scope.CRITICAL && reasonCodes.isEmpty(),
                    "critical clarification cannot contain executable work");
            case BOUNDARY, REJECTED -> require(validatedPlan == null && executionSelection == null && clarification == null
                    && !reasonCodes.isEmpty(), "boundary and rejection decisions require a controlled reason code only");
        }
    }

    private static Set<String> copyReasonCodes(Set<String> values) {
        Objects.requireNonNull(values, "reasonCodes");
        Set<String> copied = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || !value.matches("[A-Z]+_[A-Z0-9_]+")) {
                throw new IllegalArgumentException("reasonCodes must be public uppercase codes");
            }
            copied.add(value);
        }
        return Set.copyOf(copied);
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }
}
