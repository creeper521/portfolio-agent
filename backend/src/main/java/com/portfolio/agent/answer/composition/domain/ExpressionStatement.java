package com.portfolio.agent.answer.composition.domain;

import com.portfolio.agent.answer.domain.AnswerSectionType;
import java.util.Objects;

public final class ExpressionStatement {
    private final GroundedStatement statement;
    private final PresentationRole presentationRole;
    private final AnswerSectionType allowedSection;
    private final int stableOrder;

    public ExpressionStatement(GroundedStatement statement, PresentationRole presentationRole,
            AnswerSectionType allowedSection, int stableOrder) {
        this.statement = Objects.requireNonNull(statement, "statement");
        this.presentationRole = Objects.requireNonNull(presentationRole, "presentationRole");
        this.allowedSection = Objects.requireNonNull(allowedSection, "allowedSection");
        if (allowedSection == AnswerSectionType.BOUNDARY || allowedSection == AnswerSectionType.REJECTED) {
            throw new IllegalArgumentException("model expression cannot target a server-owned section");
        }
        if (stableOrder < 0) throw new IllegalArgumentException("stableOrder must be nonnegative");
        this.stableOrder = stableOrder;
    }

    public GroundedStatement getStatement() { return statement; }
    public PresentationRole getPresentationRole() { return presentationRole; }
    public AnswerSectionType getAllowedSection() { return allowedSection; }
    public int getStableOrder() { return stableOrder; }

    @Override
    public String toString() {
        return "ExpressionStatement{role=" + presentationRole
                + ", section=" + allowedSection + ", order=" + stableOrder + '}';
    }
}
