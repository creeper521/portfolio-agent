package com.portfolio.agent.turn.capability.portfolio.knowledge;

/** claim 的成果状态（回答层枚举）：从已交付到仅规划的程度分级，UNKNOWN 表示快照未提供。 */
public enum AnswerAchievementStatus {
    DELIVERED,
    IMPLEMENTED_TESTED,
    PROTOTYPE,
    DESIGNED,
    INVESTIGATED,
    LEARNING,
    PLANNED,
    UNKNOWN
}
