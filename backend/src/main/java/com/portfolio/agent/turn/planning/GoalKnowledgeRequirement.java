package com.portfolio.agent.turn.planning;

/**
 * 目标知识需求：目标提案声明的知识来源类别。
 *
 * <p>{@link GoalBoundaryPolicy} 依据本枚举裁决：前两类可放行；后两类超出
 * 公开作品集 Agent 的能力边界，直接返回 BOUNDARY 终态。</p>
 */
public enum GoalKnowledgeRequirement {
    /** 只依赖已审核的公开作品集证据。 */
    PUBLIC_PORTFOLIO_EVIDENCE,
    /** 只依赖稳定的通用知识解释。 */
    STABLE_GENERAL_EXPLANATION,
    /** 需要实时外部信息（边界外，放行为固定拒绝文案）。 */
    CURRENT_EXTERNAL_INFORMATION,
    /** 需要高风险专业建议（边界外，放行为固定拒绝文案）。 */
    HIGH_RISK_ADVICE
}
