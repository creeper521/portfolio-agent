package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;

import java.util.List;

/** Source-domain adapter boundary for executing one already-validated semantic task. */
public interface SemanticTaskExecutor {

    TaskSourceDomain getSourceDomain();

    TaskOutcome execute(SemanticTask task, List<TaskOutcome> availableDependencyOutcomes);
}
