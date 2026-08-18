package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.execution.TurnDeadline;

public final class PostgresPortfolioRetrieverAdapter implements PortfolioRetrieverPort {
    private final PortfolioRetrieverAdapterSupport support;
    public PostgresPortfolioRetrieverAdapter(PortfolioRetriever retriever) {
        this.support = new PortfolioRetrieverAdapterSupport(retriever);
    }
    @Override public RetrievalAttemptResult retrieve(
            PortfolioEvidenceInvocation invocation, RetrievalRequest request, TurnDeadline deadline) {
        return support.retrieve(invocation, request, deadline);
    }
}
