package com.portfolio.agent.answer.intelligence.adapter.postgres;

import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.selection.domain.CandidateRetrievalResult;

@FunctionalInterface
public interface PostgresKnowledgeQuery {

    CandidateRetrievalResult retrieve(PortfolioRetrievalRequest request);
}
