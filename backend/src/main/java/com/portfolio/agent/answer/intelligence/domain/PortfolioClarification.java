package com.portfolio.agent.answer.intelligence.domain;

import java.util.Objects;

public final class PortfolioClarification {

    private final String question;
    private final String missingCondition;

    public PortfolioClarification(String question, String missingCondition) {
        this.question = requireText(question, "question");
        this.missingCondition = requireText(missingCondition, "missingCondition");
    }

    public String getQuestion() { return question; }
    public String getMissingCondition() { return missingCondition; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioClarification that)) { return false; }
        return Objects.equals(question, that.question)
                && Objects.equals(missingCondition, that.missingCondition);
    }

    @Override
    public int hashCode() { return Objects.hash(question, missingCondition); }

    @Override
    public String toString() {
        return "PortfolioClarification{" + "missingCondition='" + missingCondition + '\'' + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(name + " is required"); }
        return value.trim();
    }
}
