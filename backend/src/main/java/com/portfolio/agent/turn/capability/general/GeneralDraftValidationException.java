package com.portfolio.agent.turn.capability.general;

/** Closed semantic rejection reason; provider output and exception text stay excluded. */
public final class GeneralDraftValidationException extends IllegalArgumentException {
    private final Reason reason;

    public GeneralDraftValidationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        TOPIC_MISMATCH,
        EXPLANATION_ROLES_INVALID,
        EXPLANATION_ROLE_ASPECTS_INVALID,
        EXPLANATION_COVERAGE_INVALID,
        COMPARISON_ROLE_INVALID,
        COMPARISON_ASPECTS_INVALID,
        COMPARISON_DUPLICATE_PAIR,
        COMPARISON_COVERAGE_INVALID,
        CAVEAT_DUPLICATE,
        SENTENCE_BOUNDARY_INVALID,
        LANGUAGE_OR_SENTENCE_COUNT_INVALID
    }
}
