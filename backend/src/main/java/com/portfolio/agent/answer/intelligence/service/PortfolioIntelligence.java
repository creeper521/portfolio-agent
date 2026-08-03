package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.intelligence.domain.PortfolioDecision;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;

public interface PortfolioIntelligence {

    PortfolioDecision tryResolve(PortfolioTurn turn);
}
