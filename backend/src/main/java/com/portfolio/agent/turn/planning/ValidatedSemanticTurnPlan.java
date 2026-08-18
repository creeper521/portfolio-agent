package com.portfolio.agent.turn.planning;

import java.util.Objects;

public final class ValidatedSemanticTurnPlan {
    private final SemanticTurnPlan plan;

    ValidatedSemanticTurnPlan(SemanticTurnPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public SemanticTurnPlan getPlan() { return plan; }
}
