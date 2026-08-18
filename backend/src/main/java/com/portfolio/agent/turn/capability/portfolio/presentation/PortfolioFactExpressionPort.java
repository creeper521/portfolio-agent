package com.portfolio.agent.turn.capability.portfolio.presentation;

import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import com.portfolio.agent.turn.execution.TurnDeadline;

public interface PortfolioFactExpressionPort {
    String express(
            PortfolioSemanticResult.Fact result,
            PortfolioPresentation canonical,
            TurnDeadline deadline);
}
