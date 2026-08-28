package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.planning.UserGoalProposal.GeneralComparisonParameters;

import java.util.Objects;

/**
 * 目标边界裁决器：按知识需求把目标提案挡在能力边界之外。
 *
 * <p>任一目标要求高风险专业建议或实时外部信息时，直接返回 BOUNDARY 终态
 * 与服务端固定文案；General Comparison 在完成独立能力认证前一律在 Plan 和
 * Provider 之前返回固定 BOUNDARY 终态；否则原样放行为 GOALS 终态。</p>
 */
public final class GoalBoundaryPolicy {
    /** Comparison 独立认证时沿用的历史容量上限；当前生产路径在此前已固定拒绝。 */
    static final int MAX_GENERAL_COMPARISON_PAIRS = 20;

    /**
     * 对目标提案做边界裁决。
     *
     * @return 含 HIGH_RISK_ADVICE 目标时返回高风险边界文案；含
     *         CURRENT_EXTERNAL_INFORMATION 目标时返回实时信息边界文案；
     *         General Comparison 返回固定的暂不支持边界文案；
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
        boolean comparison = proposal.getGoals().stream().anyMatch(goal ->
                goal.getGoalKind() == GoalKind.GENERAL_COMPARISON
                && goal.getParameters() instanceof GeneralComparisonParameters);
        if (comparison) {
            return ResolvedGoalSet.boundary(
                    "当前暂不支持直接比较；请分别询问这些概念。");
        }
        return ResolvedGoalSet.goals(proposal);
    }
}
