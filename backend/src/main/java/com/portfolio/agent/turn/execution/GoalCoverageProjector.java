package com.portfolio.agent.turn.execution;

import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class GoalCoverageProjector {
    List<GoalCoverage> project(SemanticTurnPlan plan, Map<String, TaskOutcome> outcomes) {
        List<GoalCoverage> coverage = new ArrayList<>();
        for (UserGoal goal : plan.getUserGoals()) {
            TaskOutcome outcome = outcomes.get(goal.getFulfillmentTaskId());
            GoalCoverage.Coverage value = GoalCoverage.Coverage.NONE;
            if (outcome != null && outcome.getTerminal() instanceof TaskOutcome.Produced produced) {
                value = produced.getFulfillment() == TaskOutcome.Fulfillment.FULL
                        ? GoalCoverage.Coverage.FULL : GoalCoverage.Coverage.PARTIAL;
            }
            coverage.add(new GoalCoverage(goal.getGoalId(), value));
        }
        return List.copyOf(coverage);
    }
}
