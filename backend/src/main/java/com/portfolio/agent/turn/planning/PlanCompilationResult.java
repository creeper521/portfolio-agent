package com.portfolio.agent.turn.planning;

import java.util.Objects;
import java.util.Optional;

public final class PlanCompilationResult {
    private final Kind kind;
    private final ValidatedSemanticTurnPlan plan;
    private final String reason;

    private PlanCompilationResult(Kind kind, ValidatedSemanticTurnPlan plan, String reason) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.plan = plan;
        this.reason = reason;
    }

    public static PlanCompilationResult compiled(ValidatedSemanticTurnPlan plan) {
        return new PlanCompilationResult(Kind.COMPILED, Objects.requireNonNull(plan, "plan"), null);
    }

    public static PlanCompilationResult clarificationRequired(String reason) {
        return new PlanCompilationResult(Kind.CLARIFICATION_REQUIRED, null, reason);
    }

    public static PlanCompilationResult rejected(String reason) {
        return new PlanCompilationResult(Kind.REJECTED, null, reason);
    }

    public Kind getKind() { return kind; }
    public Optional<ValidatedSemanticTurnPlan> getPlan() { return Optional.ofNullable(plan); }
    public Optional<String> getReason() { return Optional.ofNullable(reason); }

    public enum Kind { COMPILED, CLARIFICATION_REQUIRED, REJECTED }
}
