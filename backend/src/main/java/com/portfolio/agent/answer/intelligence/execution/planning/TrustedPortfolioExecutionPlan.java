package com.portfolio.agent.answer.intelligence.execution.planning;

import java.util.Objects;

/** Marker wrapper that can only be created by {@link PortfolioPlanValidator}. */
public final class TrustedPortfolioExecutionPlan {

    private final PortfolioExecutionPlan plan;

    TrustedPortfolioExecutionPlan(PortfolioExecutionPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public PortfolioExecutionPlan getPlan() {
        return plan;
    }

    public PortfolioExecutionPlan.PlannedInvocation getInvocation() {
        return plan.getInvocation();
    }

    @Override
    public String toString() {
        return "TrustedPortfolioExecutionPlan{invocationCount=1}";
    }
}
