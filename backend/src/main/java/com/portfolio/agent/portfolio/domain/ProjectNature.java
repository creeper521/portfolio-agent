package com.portfolio.agent.portfolio.domain;

/**
 * 项目性质：项目的工作形态分类。
 *
 * <ul>
 *   <li>TOOL：工具类项目</li>
 *   <li>WORKSTREAM：工作流/工作线</li>
 *   <li>INTEGRATION_PROTOTYPE：集成原型</li>
 *   <li>UNCLASSIFIED：未分类（schema 4.0 起快照校验不允许项目停留在该值）</li>
 * </ul>
 */
public enum ProjectNature {
    TOOL,
    WORKSTREAM,
    INTEGRATION_PROTOTYPE,
    UNCLASSIFIED
}
