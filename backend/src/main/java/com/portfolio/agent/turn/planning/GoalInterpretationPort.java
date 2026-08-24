package com.portfolio.agent.turn.planning;

import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.turn.execution.TurnDeadline;

public interface GoalInterpretationPort {
    GoalInterpretationResult interpret(
            GoalInterpretationInput input,
            TurnDeadline deadline,
            ResolvedModelExecution modelExecution);
}
