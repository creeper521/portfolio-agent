package com.portfolio.agent.answer.intelligence.adapter.postgres;

import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;

@FunctionalInterface
public interface PostgresKnowledgeQuery {

    PostgresKnowledgeQueryResult retrieve(PortfolioRetrievalRequest request);
}
