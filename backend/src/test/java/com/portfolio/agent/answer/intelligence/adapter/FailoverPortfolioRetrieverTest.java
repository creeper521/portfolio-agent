package com.portfolio.agent.answer.intelligence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalSource;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalException;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FailoverPortfolioRetrieverTest {

    @Test
    void returnsThePrimaryRetrievalResultWhenPostgresIsAvailable() {
        PortfolioRetrievalResult primaryResult = result("POSTGRES_PGVECTOR", false, null);
        AtomicInteger fallbackCalls = new AtomicInteger();
        PortfolioRetriever retriever = new FailoverPortfolioRetriever(
                request -> primaryResult,
                request -> {
                    fallbackCalls.incrementAndGet();
                    return result("BUNDLE", false, null);
                });

        PortfolioRetrievalResult result = retriever.retrieve(request());

        assertThat(result).isSameAs(primaryResult);
        assertThat(fallbackCalls).hasValue(0);
    }

    @Test
    void fallsBackToBundleAndMarksTheResultDegradedWhenPostgresIsUnavailable() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        PortfolioRetriever retriever = new FailoverPortfolioRetriever(
                request -> { throw new PortfolioRetrievalException("postgres unavailable", null); },
                request -> {
                    fallbackCalls.incrementAndGet();
                    return result("BUNDLE", false, null);
                });

        PortfolioRetrievalResult result = retriever.retrieve(request());

        assertThat(fallbackCalls).hasValue(1);
        assertThat(result.getSource().getAdapterId()).isEqualTo("BUNDLE");
        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getNoticeCode()).isEqualTo("POSTGRES_RETRIEVAL_UNAVAILABLE");
    }

    @Test
    void propagatesNonInfrastructureFailuresWithoutCallingTheFallback() {
        IllegalArgumentException expected = new IllegalArgumentException("invalid request state");
        AtomicInteger fallbackCalls = new AtomicInteger();
        PortfolioRetriever retriever = new FailoverPortfolioRetriever(
                request -> { throw expected; },
                request -> {
                    fallbackCalls.incrementAndGet();
                    return result("BUNDLE", false, null);
                });

        assertThatThrownBy(() -> retriever.retrieve(request())).isSameAs(expected);
        assertThat(fallbackCalls).hasValue(0);
    }

    private PortfolioRetrievalRequest request() {
        return new PortfolioRetrievalRequest(
                "PostgreSQL", PortfolioTaskMode.FACT_LOOKUP, PortfolioConditions.empty());
    }

    private PortfolioRetrievalResult result(String source, boolean degraded, String noticeCode) {
        return new PortfolioRetrievalResult(
                "public-2026-07-31", List.of(), List.of(), new PortfolioRetrievalSource(source),
                degraded, noticeCode);
    }
}
