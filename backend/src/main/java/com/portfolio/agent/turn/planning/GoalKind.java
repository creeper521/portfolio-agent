package com.portfolio.agent.turn.planning;

/**
 * 目标语义类别：Goal 解析产出的封闭枚举，界定单条用户目标所属的知识域。
 *
 * <p>PORTFOLIO_* 三类只依赖已审核的公开作品集快照；GENERAL_* 两类只依赖
 * 稳定的通用知识；APPLY_* 表示先解释通用概念再关联到公开项目的跨域目标。</p>
 */
public enum GoalKind {
    /** 查询单个公开主体的作品集事实。 */
    PORTFOLIO_FACT,
    /** 在多个公开主体之间做比较。 */
    PORTFOLIO_COMPARE,
    /** 按约束从公开项目中做推荐。 */
    PORTFOLIO_RECOMMEND,
    /** 解释与作品集无关的通用概念。 */
    GENERAL_EXPLANATION,
    /** 比较通用概念或技术选型。 */
    GENERAL_COMPARISON,
    /** 把通用概念应用到某个公开项目上做关联分析。 */
    APPLY_GENERAL_CONCEPT_TO_PORTFOLIO
}
