package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;

import java.util.Objects;
import java.util.Optional;

public final class RetrievalFallbackPolicy {
    public Optional<RetrievalRequest> fallbackFor(
            PortfolioEvidenceInvocation invocation, RetrievalAttemptFailure failure) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(failure, "failure");
        if (failure == RetrievalAttemptFailure.VECTOR_UNAVAILABLE
                && invocation.getPrimaryStrategy() == SearchStrategy.HYBRID) {
            return Optional.of(new RetrievalRequest(
                    invocation.getPrimaryBackend(), SearchStrategy.KEYWORD));
        }
        if ((failure == RetrievalAttemptFailure.BACKEND_CONNECTION_UNAVAILABLE
                || failure == RetrievalAttemptFailure.BACKEND_TIMEOUT)
                && invocation.getFallbackBackend() != null) {
            return Optional.of(new RetrievalRequest(
                    invocation.getFallbackBackend(), invocation.getFallbackStrategy()));
        }
        return Optional.empty();
    }
}
