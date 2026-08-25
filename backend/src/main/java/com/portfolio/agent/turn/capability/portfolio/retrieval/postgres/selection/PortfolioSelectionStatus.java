package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

/** 候选选择结果状态：READY 为成功产出候选，INSUFFICIENT 为命中不足，TEMPORARILY_UNAVAILABLE 为后端暂不可用。 */
public enum PortfolioSelectionStatus {
    READY,
    INSUFFICIENT,
    TEMPORARILY_UNAVAILABLE
}
