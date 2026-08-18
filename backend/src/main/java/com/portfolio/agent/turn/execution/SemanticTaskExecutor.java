package com.portfolio.agent.turn.execution;

import com.portfolio.agent.turn.planning.SemanticTask;

public interface SemanticTaskExecutor {
    SemanticTask.SourceDomain getSourceDomain();
    TaskExecutionResult execute(TaskExecutionContext context);
}
