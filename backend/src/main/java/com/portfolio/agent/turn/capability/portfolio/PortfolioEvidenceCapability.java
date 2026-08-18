package com.portfolio.agent.turn.capability.portfolio;

import com.portfolio.agent.answer.intelligence.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.evidence.EvidencePromotionValidator;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceBundle;
import com.portfolio.agent.turn.capability.portfolio.retrieval.PortfolioRetrieverPort;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalAttemptFailure;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalAttemptResult;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalFallbackPolicy;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalRequest;
import com.portfolio.agent.turn.execution.TurnDeadline;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns exactly one primary attempt, at most one classified fallback, and one promotion. */
public final class PortfolioEvidenceCapability {
    private final Map<CorpusBackend, PortfolioRetrieverPort> retrievers;
    private final RetrievalFallbackPolicy fallbackPolicy;
    private final EvidencePromotionValidator promotionValidator;

    public PortfolioEvidenceCapability(
            Map<CorpusBackend, PortfolioRetrieverPort> retrievers,
            RetrievalFallbackPolicy fallbackPolicy,
            EvidencePromotionValidator promotionValidator) {
        EnumMap<CorpusBackend, PortfolioRetrieverPort> copy = new EnumMap<>(CorpusBackend.class);
        copy.putAll(Objects.requireNonNull(retrievers, "retrievers"));
        this.retrievers = Map.copyOf(copy);
        this.fallbackPolicy = Objects.requireNonNull(fallbackPolicy, "fallbackPolicy");
        this.promotionValidator = Objects.requireNonNull(promotionValidator, "promotionValidator");
    }

    public ValidatedEvidenceBundle execute(
            PortfolioEvidenceInvocation invocation, TurnDeadline deadline) {
        if (deadline.isExpired()) throw new PortfolioCapabilityException(
                RetrievalAttemptFailure.CANCELLED);
        RetrievalRequest primary = new RetrievalRequest(
                invocation.getPrimaryBackend(), invocation.getPrimaryStrategy());
        RetrievalAttemptResult first = attempt(invocation, primary, deadline);
        if (first.isSuccessful()) return promote(first, invocation);
        RetrievalAttemptFailure failure = first.getFailure().orElseThrow();
        java.util.Optional<RetrievalRequest> fallback = fallbackPolicy.fallbackFor(invocation, failure);
        if (fallback.isEmpty() || deadline.isExpired()) {
            throw new PortfolioCapabilityException(failure);
        }
        RetrievalAttemptResult second = attempt(invocation, fallback.orElseThrow(), deadline);
        if (!second.isSuccessful()) {
            throw new PortfolioCapabilityException(second.getFailure().orElseThrow());
        }
        return promote(second, invocation);
    }

    private RetrievalAttemptResult attempt(
            PortfolioEvidenceInvocation invocation,
            RetrievalRequest request,
            TurnDeadline deadline) {
        PortfolioRetrieverPort retriever = retrievers.get(request.getBackend());
        if (retriever == null) {
            return RetrievalAttemptResult.failure(
                    RetrievalAttemptFailure.BACKEND_CONNECTION_UNAVAILABLE);
        }
        return Objects.requireNonNull(
                retriever.retrieve(invocation, request, deadline), "retrieval result");
    }

    private ValidatedEvidenceBundle promote(
            RetrievalAttemptResult result, PortfolioEvidenceInvocation invocation) {
        return promotionValidator.promote(
                result.getCandidateSet().orElseThrow(), invocation.getContentReleaseId());
    }

    public static final class PortfolioCapabilityException extends RuntimeException {
        private final RetrievalAttemptFailure failure;
        public PortfolioCapabilityException(RetrievalAttemptFailure failure) {
            super(Objects.requireNonNull(failure, "failure").name());
            this.failure = failure;
        }
        public RetrievalAttemptFailure getFailure() { return failure; }
    }
}
