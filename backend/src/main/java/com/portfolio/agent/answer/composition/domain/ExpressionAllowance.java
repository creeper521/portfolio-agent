package com.portfolio.agent.answer.composition.domain;

import java.time.Instant;
import java.util.Objects;

public final class ExpressionAllowance {
    private final boolean attemptAllowed;
    private final Instant absoluteDeadline;
    private final int characterLimit;
    private final int statementLimit;
    private final int requestLocalAttemptOrdinal;

    public ExpressionAllowance(boolean attemptAllowed, Instant absoluteDeadline, int characterLimit,
            int statementLimit, int requestLocalAttemptOrdinal) {
        if (characterLimit < 0 || statementLimit < 0 || requestLocalAttemptOrdinal < 0) {
            throw new IllegalArgumentException("limits must be nonnegative");
        }
        this.attemptAllowed = attemptAllowed;
        this.absoluteDeadline = Objects.requireNonNull(absoluteDeadline, "absoluteDeadline");
        this.characterLimit = characterLimit;
        this.statementLimit = statementLimit;
        this.requestLocalAttemptOrdinal = requestLocalAttemptOrdinal;
    }

    public boolean isAttemptAllowed() { return attemptAllowed; }
    public Instant getAbsoluteDeadline() { return absoluteDeadline; }
    public int getCharacterLimit() { return characterLimit; }
    public int getStatementLimit() { return statementLimit; }
    public int getRequestLocalAttemptOrdinal() { return requestLocalAttemptOrdinal; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExpressionAllowance that)) return false;
        return attemptAllowed == that.attemptAllowed && characterLimit == that.characterLimit
                && statementLimit == that.statementLimit
                && requestLocalAttemptOrdinal == that.requestLocalAttemptOrdinal
                && absoluteDeadline.equals(that.absoluteDeadline);
    }
    @Override public int hashCode() {
        return Objects.hash(attemptAllowed, absoluteDeadline, characterLimit,
                statementLimit, requestLocalAttemptOrdinal);
    }
    @Override public String toString() {
        return "ExpressionAllowance{attemptAllowed=" + attemptAllowed
                + ", characterLimit=" + characterLimit
                + ", statementLimit=" + statementLimit
                + ", requestLocalAttemptOrdinal=" + requestLocalAttemptOrdinal + '}';
    }
}
