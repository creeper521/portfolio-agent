package com.portfolio.agent.turn.execution;

import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 覆盖度投影器：把任务终态折叠为每个 UserGoal 的覆盖结论。 */
final class GoalCoverageProjector {
    /**
     * 按每个 goal 绑定的 fulfillment 任务投影覆盖度：任务为 Produced 且
     * FULL 满足度时为 FULL，为 Produced 且 PARTIAL 时为 PARTIAL，其余终态
     * （未执行、失败、取消、超时等）一律为 NONE。
     */
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
