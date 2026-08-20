package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres;

import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalRequest;

@FunctionalInterface
public interface PostgresKnowledgeQuery {

    PostgresKnowledgeQueryResult retrieve(
            PortfolioEvidenceInvocation invocation,
            RetrievalRequest request);
}
