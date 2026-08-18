package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

public interface ReviewedGoalSource {
    UserGoalProposal resolve(AgentTurnCommand command);
}
