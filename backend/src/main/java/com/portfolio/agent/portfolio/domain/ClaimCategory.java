package com.portfolio.agent.portfolio.domain;

/**
 * 断言类别：断言所描述内容的维度，供问题匹配（preferredClaimCategories）与叙事组织使用。
 *
 * <p>取值：BACKGROUND 背景事实、RESPONSIBILITY 职责、TECHNICAL_DECISION 技术决策、
 * IMPLEMENTATION 实现过程、VERIFICATION 验证过程、OUTCOME 结果影响、LIMITATION 局限、
 * LEARNING 学习收获、REFLECTION 反思。
 */
public enum ClaimCategory {
    BACKGROUND,
    RESPONSIBILITY,
    TECHNICAL_DECISION,
    IMPLEMENTATION,
    VERIFICATION,
    OUTCOME,
    LIMITATION,
    LEARNING,
    REFLECTION
}
