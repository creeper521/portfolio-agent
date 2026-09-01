package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.PortfolioSubjectKind;
import com.portfolio.agent.turn.planning.SemanticTask;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalFallbackPolicyTest {
    private final RetrievalFallbackPolicy policy = new RetrievalFallbackPolicy();

    @Test
    void vectorFailureFallsBackToKeywordOnTheSameBackend() {
        RetrievalRequest fallback = policy.fallbackFor(
                invocation(CorpusBackend.POSTGRESQL, SearchStrategy.HYBRID),
                RetrievalAttemptFailure.VECTOR_UNAVAILABLE).orElseThrow();
        assertThat(fallback.getBackend()).isEqualTo(CorpusBackend.POSTGRESQL);
        assertThat(fallback.getStrategy()).isEqualTo(SearchStrategy.KEYWORD);
    }

    @Test
    void backendAvailabilityFailureFallsBackToSameReleaseBundleAtMostOnce() {
        RetrievalRequest fallback = policy.fallbackFor(
                invocation(CorpusBackend.POSTGRESQL, SearchStrategy.EXACT),
                RetrievalAttemptFailure.BACKEND_TIMEOUT).orElseThrow();
        assertThat(fallback.getBackend()).isEqualTo(CorpusBackend.BUNDLE);
        assertThat(fallback.getStrategy()).isEqualTo(SearchStrategy.EXACT);
    }

    @Test
    void integrityVersionCancellationAndInvalidRequestNeverFallback() {
        PortfolioEvidenceInvocation invocation = invocation(
                CorpusBackend.POSTGRESQL, SearchStrategy.HYBRID);
        assertThat(List.of(
                RetrievalAttemptFailure.INTEGRITY_FAILURE,
                RetrievalAttemptFailure.CONTENT_RELEASE_MISMATCH,
                RetrievalAttemptFailure.CANCELLED,
                RetrievalAttemptFailure.INVALID_REQUEST))
                .allSatisfy(failure -> assertThat(policy.fallbackFor(invocation, failure)).isEmpty());
    }

    private PortfolioEvidenceInvocation invocation(
            CorpusBackend backend, SearchStrategy strategy) {
        return new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_FACT,
                AuthorizedSubjectScope.allPublished("public-1"),
                java.util.Set.of(PortfolioSubjectKind.PROJECT),
                List.of(PortfolioEvidenceInvocation.FacetProfile.BACKGROUND), List.of(),
                "public-1", backend, strategy,
                backend == CorpusBackend.POSTGRESQL ? CorpusBackend.BUNDLE : null,
                backend == CorpusBackend.POSTGRESQL ? strategy : null);
    }
}
