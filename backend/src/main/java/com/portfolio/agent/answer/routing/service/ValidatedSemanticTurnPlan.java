package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.PlanExclusion;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.TaskDependency;

import java.util.List;
import java.util.Set;

/**
 * Read-only trusted-plan view. The only permitted implementation is the
 * private implementation owned by {@link SemanticPlanValidator}.
 */
public sealed interface ValidatedSemanticTurnPlan
        permits SemanticPlanValidator.ValidatedPlan {

    SemanticTurnPlan getPlan();

    String getPlanId();

    String getContentVersion();

    SemanticTurnPlan.PlanSource getSource();

    List<SemanticTask> getTasks();

    List<TaskDependency> getDependencies();

    List<PlanExclusion> getExclusions();

    Set<SemanticRoutingTypes.RequestedOutput> getRequestedOutputs();

    SemanticTurnPlan.PlanConfirmationPolicy getConfirmationPolicy();

    String getPlanFingerprint();
}
