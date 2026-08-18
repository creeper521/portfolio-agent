package com.portfolio.agent.turn.planning;

public final class GoalInterpretationUnavailableException extends RuntimeException {
    public GoalInterpretationUnavailableException() {
        super("goal interpretation is unavailable");
    }

    public GoalInterpretationUnavailableException(Throwable cause) {
        super("goal interpretation is unavailable", cause);
    }
}
