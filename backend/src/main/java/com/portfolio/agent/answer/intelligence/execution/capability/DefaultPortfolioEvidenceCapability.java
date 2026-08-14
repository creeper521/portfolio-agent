package com.portfolio.agent.answer.intelligence.execution.capability;

import com.portfolio.agent.answer.intelligence.execution.domain.CapabilityExecutionConstraints;
import com.portfolio.agent.answer.intelligence.execution.domain.PortfolioEvidenceInvocation;
import com.portfolio.agent.answer.intelligence.execution.domain.SafeReasonCode;
import com.portfolio.agent.answer.intelligence.execution.validation.EvidencePromotionValidator;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle;
import com.portfolio.agent.answer.intelligence.retrieval.RetrievalAttemptFailure;
import com.portfolio.agent.answer.intelligence.retrieval.RetrievalFallbackPolicy;

import java.time.Instant;
import java.util.Objects;

/** Executes one primary attempt and at most one atomic fallback attempt. */
public final class DefaultPortfolioEvidenceCapability implements PortfolioEvidenceCapability {
    private final PortfolioCandidateRetrievalPort primary;
    private final PortfolioCandidateRetrievalPort fallback;
    private final EvidencePromotionValidator promotionValidator;
    private final RetrievalFallbackPolicy fallbackPolicy;

    public DefaultPortfolioEvidenceCapability(
            PortfolioCandidateRetrievalPort primary,
            PortfolioCandidateRetrievalPort fallback) {
        this(primary, fallback, new EvidencePromotionValidator());
    }

    public DefaultPortfolioEvidenceCapability(
            PortfolioCandidateRetrievalPort primary,
            PortfolioCandidateRetrievalPort fallback,
            EvidencePromotionValidator promotionValidator) {
        this(primary, fallback, promotionValidator, null);
    }

    public DefaultPortfolioEvidenceCapability(
            PortfolioCandidateRetrievalPort primary,
            PortfolioCandidateRetrievalPort fallback,
            EvidencePromotionValidator promotionValidator,
            RetrievalFallbackPolicy fallbackPolicy) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.promotionValidator = Objects.requireNonNull(promotionValidator, "promotionValidator");
        this.fallbackPolicy = fallbackPolicy;
    }

    @Override
    public CapabilityExecutionResult execute(
            PortfolioEvidenceInvocation invocation, CapabilityExecutionConstraints constraints) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(constraints, "constraints");
        if (!canStart(constraints)) return CapabilityExecutionResult.unavailable(
                SafeReasonCode.TURN_BUDGET_UNAVAILABLE);
        CapabilityExecutionResult primaryResult = executeAttempt(primary, invocation, constraints, 1);
        if (primaryResult.getStatus() == CapabilityExecutionResult.Status.SUCCESS
                || primaryResult.getStatus() == CapabilityExecutionResult.Status.EMPTY
                || primaryResult.getStatus() == CapabilityExecutionResult.Status.INTEGRITY_FAILED) {
            return primaryResult;
        }
        PortfolioEvidenceInvocation fallbackInvocation = invocation;
        PortfolioCandidateRetrievalPort fallbackPort = fallback;
        if (fallbackPolicy != null) {
            java.util.Optional<com.portfolio.agent.answer.intelligence.retrieval.EffectiveRetrievalPlan>
                    fallbackPlan = fallbackPlan(invocation, primaryResult);
            if (fallbackPlan.isEmpty()) return primaryResult;
            com.portfolio.agent.answer.intelligence.retrieval.EffectiveRetrievalPlan selected =
                    fallbackPlan.orElseThrow();
            fallbackInvocation = invocation.withRetrievalPlan(selected);
            fallbackPort = selected.getPrimaryBackend()
                    == invocation.getRetrievalPlan().getPrimaryBackend() ? primary : fallback;
        }
        if (constraints.getAllowance().getBackendAttemptLimit() < 2 || !canStart(constraints)) {
            return primaryResult;
        }
        CapabilityExecutionResult fallbackResult = executeAttempt(
                fallbackPort, fallbackInvocation, constraints, 2);
        if (fallbackResult.getStatus() == CapabilityExecutionResult.Status.SUCCESS
                || fallbackResult.getStatus() == CapabilityExecutionResult.Status.EMPTY) {
            return fallbackResult.asDegraded();
        }
        return fallbackResult;
    }

    private CapabilityExecutionResult executeAttempt(
            PortfolioCandidateRetrievalPort port, PortfolioEvidenceInvocation invocation,
            CapabilityExecutionConstraints constraints, int attempt) {
        CapabilityExecutionResult result = port.retrieve(invocation, constraints, attempt);
        if (result == null) return CapabilityExecutionResult.unavailable(
                SafeReasonCode.CAPABILITY_TEMPORARILY_UNAVAILABLE);
        if (result.getStatus() != CapabilityExecutionResult.Status.SUCCESS
                && result.getStatus() != CapabilityExecutionResult.Status.EMPTY) return result;
        try {
            ValidatedEvidenceBundle bundle = promotionValidator.promote(
                    result.getCandidateSet().orElseThrow(), invocation.getExpectedContentVersion());
            if (result.getStatus() == CapabilityExecutionResult.Status.EMPTY) {
                return CapabilityExecutionResult.empty(result.getCandidateSet().orElseThrow(), bundle);
            }
            return CapabilityExecutionResult.success(result.getCandidateSet().orElseThrow(), bundle);
        } catch (IllegalArgumentException exception) {
            return CapabilityExecutionResult.integrityFailed();
        }
    }

    private boolean canStart(CapabilityExecutionConstraints constraints) {
        return constraints.getAllowance().getLogicalRetrievalLimit() >= 1
                && !constraints.getAllowance().isExpired(Instant.now())
                && constraints.getAllowance().hasMinimumStartWindow(Instant.now());
    }

    private java.util.Optional<com.portfolio.agent.answer.intelligence.retrieval.EffectiveRetrievalPlan>
            fallbackPlan(
            PortfolioEvidenceInvocation invocation, CapabilityExecutionResult result) {
        RetrievalAttemptFailure failure = result.getAttemptFailure()
                .orElse(RetrievalAttemptFailure.BACKEND_CONNECTION_UNAVAILABLE);
        return fallbackPolicy.fallbackFor(invocation.getRetrievalPlan(), failure);
    }
}
