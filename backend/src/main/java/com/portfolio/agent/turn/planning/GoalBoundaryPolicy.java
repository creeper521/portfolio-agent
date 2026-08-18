package com.portfolio.agent.turn.planning;

import java.util.Objects;

public final class GoalBoundaryPolicy {

    public ResolvedGoalSet apply(UserGoalProposal proposal) {
        Objects.requireNonNull(proposal, "proposal");
        boolean highRisk = proposal.getGoals().stream().anyMatch(goal ->
                goal.getKnowledgeRequirement() == GoalKnowledgeRequirement.HIGH_RISK_ADVICE);
        if (highRisk) {
            return ResolvedGoalSet.boundary("当前能力不提供高风险专业建议。");
        }
        boolean current = proposal.getGoals().stream().anyMatch(goal ->
                goal.getKnowledgeRequirement() == GoalKnowledgeRequirement.CURRENT_EXTERNAL_INFORMATION);
        if (current) {
            return ResolvedGoalSet.boundary("当前能力不查询实时外部信息。");
        }
        return ResolvedGoalSet.goals(proposal);
    }
}
