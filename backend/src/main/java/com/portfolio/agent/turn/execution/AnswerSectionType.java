package com.portfolio.agent.turn.execution;

/**
 * 公开回答分节的封闭类型词汇：Execution 层产物到 Presentation 的统一节语义。
 *
 * <p>由各 Capability 的展示组装器标注节的类型，回答组装与渲染只按该类型
 * 组织结构，不解析自由文本来猜测分节。
 */
public enum AnswerSectionType {
    BACKGROUND,
    RESPONSIBILITY,
    SOLUTION,
    VERIFICATION,
    STATUS,
    BOUNDARY,
    GENERAL_PRINCIPLE,
    PORTFOLIO_EXAMPLE,
    RELATION,
    REJECTED
}
