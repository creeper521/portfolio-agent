package com.portfolio.agent.turn.capability.portfolio;

import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import com.portfolio.agent.turn.capability.portfolio.evidence.EvidencePromotionValidator;
import com.portfolio.agent.turn.capability.portfolio.retrieval.PortfolioCandidateSet;
import com.portfolio.agent.turn.capability.portfolio.retrieval.PortfolioRetrieverPort;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalAttemptFailure;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalAttemptResult;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalFallbackPolicy;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.SemanticTask;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioEvidenceCapabilityTest {
    @Test
    void classifiedBackendFailureUsesExactlyOneFallbackThenPromotesOnce() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        PortfolioRetrieverPort primary = (invocation, request, deadline) -> {
            primaryCalls.incrementAndGet();
            return RetrievalAttemptResult.failure(RetrievalAttemptFailure.BACKEND_TIMEOUT);
        };
        PortfolioRetrieverPort fallback = (invocation, request, deadline) -> {
            fallbackCalls.incrementAndGet();
            return RetrievalAttemptResult.success(emptyCandidates("public-1"));
        };
        PortfolioEvidenceCapability capability = capability(Map.of(
                CorpusBackend.POSTGRESQL, primary, CorpusBackend.BUNDLE, fallback));

        assertThat(capability.execute(invocation(), deadline()).getUnits()).isEmpty();
        assertThat(primaryCalls.get()).isEqualTo(1);
        assertThat(fallbackCalls.get()).isEqualTo(1);
    }

    @Test
    void businessEmptySuccessNeverFallsBack() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        PortfolioRetrieverPort primary = (invocation, request, deadline) ->
                RetrievalAttemptResult.success(emptyCandidates("public-1"));
        PortfolioEvidenceCapability capability = capability(Map.of(
                CorpusBackend.POSTGRESQL, primary,
                CorpusBackend.BUNDLE, (invocation, request, deadline) -> {
                    fallbackCalls.incrementAndGet();
                    return RetrievalAttemptResult.success(emptyCandidates("public-1"));
                }));

        assertThat(capability.execute(invocation(), deadline()).getUnits()).isEmpty();
        assertThat(fallbackCalls.get()).isZero();
    }

    @Test
    void versionIntegrityFailureDoesNotRetry() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        PortfolioEvidenceCapability capability = capability(Map.of(
                CorpusBackend.POSTGRESQL, (invocation, request, deadline) ->
                        RetrievalAttemptResult.success(emptyCandidates("public-2")),
                CorpusBackend.BUNDLE, (invocation, request, deadline) -> {
                    fallbackCalls.incrementAndGet();
                    return RetrievalAttemptResult.success(emptyCandidates("public-1"));
                }));

        assertThatThrownBy(() -> capability.execute(invocation(), deadline()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONTENT_RELEASE_MISMATCH");
        assertThat(fallbackCalls.get()).isZero();
    }

    private PortfolioEvidenceCapability capability(Map<CorpusBackend, PortfolioRetrieverPort> ports) {
        return new PortfolioEvidenceCapability(
                ports, new RetrievalFallbackPolicy(), new EvidencePromotionValidator(Clock.systemUTC()));
    }

    private PortfolioEvidenceInvocation invocation() {
        return new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_FACT,
                AuthorizedSubjectScope.allPublished("public-1"),
                Set.of(PortfolioSubjectKind.PROJECT, PortfolioSubjectKind.CASE),
                List.of(PortfolioEvidenceInvocation.FacetProfile.BACKGROUND), List.of(),
                "public-1", CorpusBackend.POSTGRESQL, SearchStrategy.EXACT,
                CorpusBackend.BUNDLE, SearchStrategy.EXACT);
    }

    private PortfolioCandidateSet emptyCandidates(String release) {
        return new PortfolioCandidateSet(
                release, AuthorizedSubjectScope.allPublished(release), List.of());
    }

    private TurnDeadline deadline() {
        return TurnDeadline.after(Duration.ofSeconds(1), Clock.systemUTC());
    }
}
