package com.portfolio.agent.answer.intelligence.execution.planning;

import com.portfolio.agent.answer.intelligence.execution.domain.PortfolioEvidenceInvocation;
import com.portfolio.agent.answer.routing.domain.SemanticTaskExecutionContext;

import java.time.Instant;
import java.util.Objects;

/** Single issuer of the trusted P3 plan marker. */
public final class PortfolioPlanValidator {

    private final PortfolioCapabilityCatalog catalog;

    public PortfolioPlanValidator(PortfolioCapabilityCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public TrustedPortfolioExecutionPlan validate(
            PortfolioExecutionPlan plan, SemanticTaskExecutionContext context) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        if (!plan.getTaskId().equals(context.getSemanticTask().getTaskId())) {
            throw new IllegalArgumentException("plan task does not match execution context");
        }
        if (plan.getInvocations().size() != 1) {
            throw new IllegalArgumentException("plan must contain exactly one invocation");
        }
        PortfolioExecutionPlan.PlannedInvocation planned = plan.getInvocation();
        if (!catalog.contains(planned.getCapabilityId())) {
            throw new IllegalArgumentException("plan capability is not in the startup catalog");
        }
        PortfolioEvidenceInvocation invocation = planned.getInvocation();
        if (!context.getExpectedContentVersion().equals(invocation.getExpectedContentVersion())
                || !context.getExpectedContentVersion().equals(
                invocation.getAuthorizedSubjectScope().getContentVersion())) {
            throw new IllegalArgumentException("plan content version is not current");
        }
        if (context.getTaskExecutionAllowance().getLogicalRetrievalLimit() != 1
                || context.getTaskExecutionAllowance().getBackendAttemptLimit() < 1
                || context.getTaskExecutionAllowance().isExpired(Instant.now())) {
            throw new IllegalArgumentException("plan allowance is invalid");
        }
        return new TrustedPortfolioExecutionPlan(plan);
    }
}
