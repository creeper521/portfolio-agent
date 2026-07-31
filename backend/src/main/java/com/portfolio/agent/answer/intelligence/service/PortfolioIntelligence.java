package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;

public interface PortfolioIntelligence {

    PortfolioIntelligenceResult resolve(PortfolioTask task);
}
