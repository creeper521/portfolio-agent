package com.portfolio.agent.turn.planning;

import java.util.Objects;

/**
 * 澄清提案：Goal 解析判定需要补充信息时产出的单字段澄清请求。
 *
 * <p>由待澄清字段、面向访客的提问文案与可持久化的
 * {@link BlockedGoalTemplate} 组成；字段必须与被阻塞目标的未解析字段一致，
 * 提问文案必填且不超过 400 字符。</p>
 */
public final class ClarificationProposal {
    private final Field field;
    private final String prompt;
    private final BlockedGoalTemplate blockedGoal;

    public ClarificationProposal(
            Field field, String prompt, BlockedGoalTemplate blockedGoal) {
        this.field = Objects.requireNonNull(field, "field");
        if (prompt == null || prompt.isBlank() || prompt.length() > 400) {
            throw new IllegalArgumentException("clarification prompt is required and bounded");
        }
        this.prompt = prompt;
        this.blockedGoal = Objects.requireNonNull(blockedGoal, "blockedGoal");
        if (blockedGoal.getUnresolvedField() != field) {
            throw new IllegalArgumentException("clarification field must match blocked goal");
        }
    }

    public Field getField() { return field; }
    public String getPrompt() { return prompt; }
    public BlockedGoalTemplate getBlockedGoal() { return blockedGoal; }

    /** 待澄清字段：主体/目标/输出/推荐数量/约束。 */
    public enum Field { SUBJECT, GOAL, OUTPUT, REQUESTED_SIZE, CONSTRAINT }
}
