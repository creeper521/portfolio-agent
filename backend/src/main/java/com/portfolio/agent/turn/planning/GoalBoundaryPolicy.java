package com.portfolio.agent.turn.planning;

import java.util.Objects;

/**
 * 目标边界裁决器：按知识需求把目标提案挡在能力边界之外。
 *
 * <p>任一目标要求高风险专业建议或实时外部信息时，直接返回 BOUNDARY 终态
 * 与服务端固定文案，不进入 Plan 阶段；否则原样放行为 GOALS 终态。</p>
 */
public final class GoalBoundaryPolicy {

    /**
     * 对目标提案做边界裁决。
     *
     * @return 含 HIGH_RISK_ADVICE 目标时返回高风险边界文案；含
     *         CURRENT_EXTERNAL_INFORMATION 目标时返回实时信息边界文案；
     *         否则返回携带原提案的 GOALS 终态
     */
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
