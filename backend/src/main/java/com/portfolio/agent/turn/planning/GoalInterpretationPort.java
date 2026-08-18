package com.portfolio.agent.turn.planning;

public interface GoalInterpretationPort {
    GoalInterpretationResult interpret(GoalInterpretationInput input);
}
