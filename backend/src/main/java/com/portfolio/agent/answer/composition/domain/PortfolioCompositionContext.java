package com.portfolio.agent.answer.composition.domain;

import java.util.Objects;

public final class PortfolioCompositionContext {
    private final ExpressionIntent expressionIntent;
    private final ExpressionAllowance expressionAllowance;
    public PortfolioCompositionContext(ExpressionIntent expressionIntent,
            ExpressionAllowance expressionAllowance) {
        this.expressionIntent = Objects.requireNonNull(expressionIntent, "expressionIntent");
        this.expressionAllowance = Objects.requireNonNull(expressionAllowance, "expressionAllowance");
    }
    public ExpressionIntent getExpressionIntent() { return expressionIntent; }
    public ExpressionAllowance getExpressionAllowance() { return expressionAllowance; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof PortfolioCompositionContext that
                && expressionIntent.equals(that.expressionIntent)
                && expressionAllowance.equals(that.expressionAllowance);
    }
    @Override public int hashCode() { return Objects.hash(expressionIntent, expressionAllowance); }
    @Override public String toString() {
        return "PortfolioCompositionContext{intent=" + expressionIntent
                + ", allowance=" + expressionAllowance + '}';
    }
}
