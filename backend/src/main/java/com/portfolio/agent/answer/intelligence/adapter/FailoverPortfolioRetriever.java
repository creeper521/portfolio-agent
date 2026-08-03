package com.portfolio.agent.answer.intelligence.adapter;

import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalException;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalFailureKind;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import java.util.Objects;

public final class FailoverPortfolioRetriever implements PortfolioRetriever {

    public static final String POSTGRES_RETRIEVAL_UNAVAILABLE = "POSTGRES_RETRIEVAL_UNAVAILABLE";

    private final PortfolioRetriever primaryRetriever;
    private final PortfolioRetriever fallbackRetriever;

    public FailoverPortfolioRetriever(
            PortfolioRetriever primaryRetriever,
            PortfolioRetriever fallbackRetriever) {
        this.primaryRetriever = Objects.requireNonNull(primaryRetriever, "primaryRetriever");
        this.fallbackRetriever = Objects.requireNonNull(fallbackRetriever, "fallbackRetriever");
    }

    @Override
    public PortfolioRetrievalResult retrieve(PortfolioRetrievalRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            return primaryRetriever.retrieve(request);
        } catch (PortfolioRetrievalException exception) {
            if (exception.getKind() != PortfolioRetrievalFailureKind.CONNECTION_UNAVAILABLE
                    && exception.getKind() != PortfolioRetrievalFailureKind.TIMEOUT) {
                throw exception;
            }
            PortfolioRetrievalResult fallbackResult = fallbackRetriever.retrieve(request);
            return new PortfolioRetrievalResult(
                    fallbackResult.getContentVersion(),
                    fallbackResult.getSubjects(),
                    fallbackResult.getPassages(),
                    fallbackResult.getSource(),
                    true,
                    POSTGRES_RETRIEVAL_UNAVAILABLE);
        }
    }
}
