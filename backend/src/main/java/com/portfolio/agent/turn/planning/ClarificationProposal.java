package com.portfolio.agent.turn.planning;

import java.util.Objects;

public final class ClarificationProposal {
    private final Field field;
    private final String prompt;
    private final UserGoalProposal.InputAnchor inputAnchor;

    public ClarificationProposal(
            Field field, String prompt, UserGoalProposal.InputAnchor inputAnchor) {
        this.field = Objects.requireNonNull(field, "field");
        if (prompt == null || prompt.isBlank() || prompt.length() > 400) {
            throw new IllegalArgumentException("clarification prompt is required and bounded");
        }
        this.prompt = prompt;
        this.inputAnchor = Objects.requireNonNull(inputAnchor, "inputAnchor");
    }

    public Field getField() { return field; }
    public String getPrompt() { return prompt; }
    public UserGoalProposal.InputAnchor getInputAnchor() { return inputAnchor; }

    public enum Field { SUBJECT, GOAL, OUTPUT }
}
