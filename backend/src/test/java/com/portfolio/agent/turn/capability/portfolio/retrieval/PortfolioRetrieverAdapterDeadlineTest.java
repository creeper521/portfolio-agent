package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalSource;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import com.portfolio.agent.answer.intelligence.retrieval.CorpusBackend;
import com.portfolio.agent.answer.intelligence.retrieval.SearchStrategy;
import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.SemanticTask;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortfolioRetrieverAdapterDeadlineTest {
    @Test
    void bundleAndPostgresAdaptersPreserveReleaseAndReturnRawCandidatesOnly() {
        PortfolioRetriever retriever = mock(PortfolioRetriever.class);
        when(retriever.retrieve(org.mockito.ArgumentMatchers.any())).thenReturn(
                new PortfolioRetrievalResult(
                        "public-1", List.of(), List.of(),
                        new PortfolioRetrievalSource("bundle"), false, null));
        RetrievalRequest request = new RetrievalRequest(CorpusBackend.BUNDLE, SearchStrategy.EXACT);
        TurnDeadline deadline = new TurnDeadline(
                Instant.parse("2026-08-18T00:01:00Z"), clock());

        assertThat(new BundlePortfolioRetrieverAdapter(retriever)
                .retrieve(invocation(), request, deadline).getCandidateSet().orElseThrow()
                .getContentReleaseId()).isEqualTo("public-1");
        assertThat(new PostgresPortfolioRetrieverAdapter(retriever)
                .retrieve(invocation(), request, deadline).getCandidateSet().orElseThrow()
                .getContentReleaseId()).isEqualTo("public-1");
    }

    @Test
    void expiredDeadlinePreventsIoAndReleaseMismatchIsClosedIntegrityFailure() {
        PortfolioRetriever untouched = mock(PortfolioRetriever.class);
        RetrievalAttemptResult cancelled = new BundlePortfolioRetrieverAdapter(untouched).retrieve(
                invocation(), new RetrievalRequest(CorpusBackend.BUNDLE, SearchStrategy.EXACT),
                new TurnDeadline(Instant.parse("2026-08-18T00:00:00Z"), clock()));
        assertThat(cancelled.getFailure()).contains(RetrievalAttemptFailure.CANCELLED);
        verifyNoInteractions(untouched);

        PortfolioRetriever mismatched = mock(PortfolioRetriever.class);
        when(mismatched.retrieve(org.mockito.ArgumentMatchers.any())).thenReturn(
                new PortfolioRetrievalResult(
                        "public-2", List.of(), List.of(),
                        new PortfolioRetrievalSource("bundle"), false, null));
        RetrievalAttemptResult failed = new BundlePortfolioRetrieverAdapter(mismatched).retrieve(
                invocation(), new RetrievalRequest(CorpusBackend.BUNDLE, SearchStrategy.EXACT),
                new TurnDeadline(Instant.parse("2026-08-18T00:01:00Z"), clock()));
        assertThat(failed.getFailure()).contains(RetrievalAttemptFailure.INTEGRITY_FAILURE);
    }

    private PortfolioEvidenceInvocation invocation() {
        return new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_FACT,
                AuthorizedSubjectScope.allPublished("public-1"),
                List.of(PortfolioEvidenceInvocation.FacetProfile.BACKGROUND), List.of(),
                "public-1", CorpusBackend.BUNDLE, SearchStrategy.EXACT, null, null);
    }
    private Clock clock() {
        return Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
    }
}
