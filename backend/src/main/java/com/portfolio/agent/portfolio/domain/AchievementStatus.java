package com.portfolio.agent.portfolio.domain;

/**
 * 成果落地程度：断言或案例所描述工作的实际完成状态。
 *
 * <ul>
 *   <li>DELIVERED：已交付上线</li>
 *   <li>IMPLEMENTED_TESTED：已实现并通过测试</li>
 *   <li>PROTOTYPE：原型验证</li>
 *   <li>DESIGNED：完成设计</li>
 *   <li>INVESTIGATED：完成调研</li>
 *   <li>LEARNING：学习练习</li>
 *   <li>PLANNED：仅有计划</li>
 *   <li>UNKNOWN：未知</li>
 * </ul>
 *
 * <p>快照校验器把前四项视为"成果"（achievement）：此类断言必须至少有一条
 * APPROVED 的 DIRECT 证据关联。
 */
public enum AchievementStatus {
    DELIVERED,
    IMPLEMENTED_TESTED,
    PROTOTYPE,
    DESIGNED,
    INVESTIGATED,
    LEARNING,
    PLANNED,
    UNKNOWN
}
