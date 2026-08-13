package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import com.portfolio.agent.answer.routing.domain.SemanticTaskExecutionContext;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;

/** Source-domain adapter boundary for executing one already-validated semantic task. */
public interface SemanticTaskExecutor {

    TaskSourceDomain getSourceDomain();

    TaskOutcome execute(SemanticTaskExecutionContext context);
}
