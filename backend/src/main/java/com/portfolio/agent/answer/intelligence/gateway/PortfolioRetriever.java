package com.portfolio.agent.answer.intelligence.gateway;

import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;

public interface PortfolioRetriever {

    PortfolioRetrievalResult retrieve(PortfolioRetrievalRequest request);
}
