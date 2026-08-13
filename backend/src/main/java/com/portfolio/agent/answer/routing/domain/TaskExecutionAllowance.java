package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Immutable, P2-assigned execution budget for one semantic task. */
public final class TaskExecutionAllowance {

    public static final int PORTFOLIO_LOGICAL_RETRIEVAL_LIMIT = 1;
    public static final int PORTFOLIO_BACKEND_ATTEMPT_LIMIT = 2;
    public static final int PORTFOLIO_EVIDENCE_UNIT_LIMIT = 128;
    public static final int PORTFOLIO_PUBLIC_REFERENCE_LIMIT = 96;
    public static final int PORTFOLIO_CHARACTER_LIMIT = 4000;
    public static final Duration MINIMUM_START_WINDOW = Duration.ofMillis(250);

    private final int logicalRetrievalLimit;
    private final int backendAttemptLimit;
    private final int evidenceUnitLimit;
    private final int publicReferenceLimit;
    private final int characterLimit;
    private final Instant absoluteDeadline;

    public TaskExecutionAllowance(
            int logicalRetrievalLimit,
            int backendAttemptLimit,
            int evidenceUnitLimit,
            int publicReferenceLimit,
            int characterLimit,
            Instant absoluteDeadline) {
        this.logicalRetrievalLimit = requireNonNegative(logicalRetrievalLimit, "logicalRetrievalLimit");
        this.backendAttemptLimit = requireNonNegative(backendAttemptLimit, "backendAttemptLimit");
        this.evidenceUnitLimit = requireNonNegative(evidenceUnitLimit, "evidenceUnitLimit");
        this.publicReferenceLimit = requireNonNegative(publicReferenceLimit, "publicReferenceLimit");
        this.characterLimit = requireNonNegative(characterLimit, "characterLimit");
        this.absoluteDeadline = Objects.requireNonNull(absoluteDeadline, "absoluteDeadline");
    }

    public static TaskExecutionAllowance portfolio(Instant absoluteDeadline) {
        return new TaskExecutionAllowance(
                PORTFOLIO_LOGICAL_RETRIEVAL_LIMIT,
                PORTFOLIO_BACKEND_ATTEMPT_LIMIT,
                PORTFOLIO_EVIDENCE_UNIT_LIMIT,
                PORTFOLIO_PUBLIC_REFERENCE_LIMIT,
                PORTFOLIO_CHARACTER_LIMIT,
                absoluteDeadline);
    }

    public static TaskExecutionAllowance none(Instant absoluteDeadline) {
        return new TaskExecutionAllowance(0, 0, 0, 0, 0, absoluteDeadline);
    }

    public static TaskExecutionAllowance forTask(
            SemanticTaskType taskType, int characterLimit, Instant absoluteDeadline) {
        Objects.requireNonNull(taskType, "taskType");
        if (taskType == SemanticTaskType.PORTFOLIO_FACT
                || taskType == SemanticTaskType.PORTFOLIO_COMPARE
                || taskType == SemanticTaskType.PORTFOLIO_RECOMMEND
                || taskType == SemanticTaskType.PORTFOLIO_REFINE_RECOMMENDATION) {
            return new TaskExecutionAllowance(
                    PORTFOLIO_LOGICAL_RETRIEVAL_LIMIT,
                    PORTFOLIO_BACKEND_ATTEMPT_LIMIT,
                    PORTFOLIO_EVIDENCE_UNIT_LIMIT,
                    PORTFOLIO_PUBLIC_REFERENCE_LIMIT,
                    Math.min(PORTFOLIO_CHARACTER_LIMIT,
                            requireNonNegative(characterLimit, "characterLimit")),
                    absoluteDeadline);
        }
        return none(absoluteDeadline);
    }

    public boolean hasMinimumStartWindow(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.plus(MINIMUM_START_WINDOW).isAfter(absoluteDeadline);
    }

    public boolean isExpired(Instant now) {
        return !Objects.requireNonNull(now, "now").isBefore(absoluteDeadline);
    }

    public int getLogicalRetrievalLimit() {
        return logicalRetrievalLimit;
    }

    public int getBackendAttemptLimit() {
        return backendAttemptLimit;
    }

    public int getEvidenceUnitLimit() {
        return evidenceUnitLimit;
    }

    public int getPublicReferenceLimit() {
        return publicReferenceLimit;
    }

    public int getCharacterLimit() {
        return characterLimit;
    }

    public Instant getAbsoluteDeadline() {
        return absoluteDeadline;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaskExecutionAllowance that)) {
            return false;
        }
        return logicalRetrievalLimit == that.logicalRetrievalLimit
                && backendAttemptLimit == that.backendAttemptLimit
                && evidenceUnitLimit == that.evidenceUnitLimit
                && publicReferenceLimit == that.publicReferenceLimit
                && characterLimit == that.characterLimit
                && absoluteDeadline.equals(that.absoluteDeadline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logicalRetrievalLimit, backendAttemptLimit, evidenceUnitLimit,
                publicReferenceLimit, characterLimit, absoluteDeadline);
    }

    @Override
    public String toString() {
        return "TaskExecutionAllowance{logicalRetrievalLimit=" + logicalRetrievalLimit
                + ", backendAttemptLimit=" + backendAttemptLimit
                + ", evidenceUnitLimit=" + evidenceUnitLimit
                + ", publicReferenceLimit=" + publicReferenceLimit
                + ", characterLimit=" + characterLimit + '}';
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
