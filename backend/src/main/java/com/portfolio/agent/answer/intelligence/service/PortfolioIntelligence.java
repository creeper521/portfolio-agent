package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.intelligence.domain.PortfolioDecision;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;

public interface PortfolioIntelligence {

    PortfolioDecision tryResolve(PortfolioTurn turn);

    /** Executes an already-classified Portfolio task without re-routing its semantic intent. */
    default PortfolioDecision resolveTypedTask(PortfolioTask task) {
        throw new UnsupportedOperationException("typed portfolio task execution is unavailable");
    }
}
