package com.portfolio.agent.answer.composition.domain;

import com.portfolio.agent.answer.domain.PortfolioAnswerPlan;
import java.util.Objects;

public final class PortfolioCompositionResult {
    private final PortfolioAnswerPlan plan;
    private final CompositionMode compositionMode;
    private final ExpressionDisposition expressionDisposition;
    private final boolean expressionDegraded;
    public PortfolioCompositionResult(PortfolioAnswerPlan plan, CompositionMode compositionMode,
            ExpressionDisposition expressionDisposition, boolean expressionDegraded) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.compositionMode = Objects.requireNonNull(compositionMode, "compositionMode");
        this.expressionDisposition = Objects.requireNonNull(expressionDisposition, "expressionDisposition");
        this.expressionDegraded = expressionDegraded;
    }
    public PortfolioAnswerPlan getPlan() { return plan; }
    public CompositionMode getCompositionMode() { return compositionMode; }
    public ExpressionDisposition getExpressionDisposition() { return expressionDisposition; }
    public boolean isExpressionDegraded() { return expressionDegraded; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PortfolioCompositionResult that)) return false;
        return expressionDegraded == that.expressionDegraded && plan.equals(that.plan)
                && compositionMode == that.compositionMode
                && expressionDisposition == that.expressionDisposition;
    }
    @Override public int hashCode() {
        return Objects.hash(plan, compositionMode, expressionDisposition, expressionDegraded);
    }
    @Override public String toString() {
        return "PortfolioCompositionResult{compositionMode=" + compositionMode
                + ", expressionDisposition=" + expressionDisposition
                + ", expressionDegraded=" + expressionDegraded + '}';
    }
}
