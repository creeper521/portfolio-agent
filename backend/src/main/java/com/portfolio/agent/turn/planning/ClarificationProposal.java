package com.portfolio.agent.turn.planning;

import java.util.Objects;

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

    public enum Field { SUBJECT, GOAL, OUTPUT, REQUESTED_SIZE, CONSTRAINT }
}
