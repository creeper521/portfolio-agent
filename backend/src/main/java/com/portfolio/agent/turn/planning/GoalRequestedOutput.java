package com.portfolio.agent.turn.planning;

/**
 * 目标请求输出：每条目标最终要交付的公开输出类别。
 *
 * <p>OVERVIEW..STATUS 六项由 PORTFOLIO_FACT 的侧面同名派生；
 * COMPARISON、RECOMMENDATION、EXPLANATION、RELATION 分别对应比较、
 * 推荐、通用解释与概念关联四类目标的单一固定输出。</p>
 */
public enum GoalRequestedOutput {
    /** 项目/案例概览。 */
    OVERVIEW,
    /** 背景与上下文。 */
    BACKGROUND,
    /** 职责分工。 */
    RESPONSIBILITY,
    /** 方案与实现。 */
    SOLUTION,
    /** 验证与效果。 */
    VERIFICATION,
    /** 当前状态。 */
    STATUS,
    /** 比较结论。 */
    COMPARISON,
    /** 推荐结论。 */
    RECOMMENDATION,
    /** 通用解释。 */
    EXPLANATION,
    /** 概念与项目的关联结论。 */
    RELATION
}
