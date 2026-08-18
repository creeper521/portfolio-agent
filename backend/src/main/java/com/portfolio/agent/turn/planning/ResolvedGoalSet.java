package com.portfolio.agent.turn.planning;

import java.util.Objects;
import java.util.Optional;

public final class ResolvedGoalSet {
    private final Kind kind;
    private final UserGoalProposal goalProposal;
    private final ClarificationProposal clarification;
    private final String message;

    private ResolvedGoalSet(
            Kind kind,
            UserGoalProposal goalProposal,
            ClarificationProposal clarification,
            String message) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.goalProposal = goalProposal;
        this.clarification = clarification;
        this.message = message;
    }

    public static ResolvedGoalSet goals(UserGoalProposal proposal) {
        return new ResolvedGoalSet(Kind.GOALS, Objects.requireNonNull(proposal, "proposal"), null, null);
    }

    public static ResolvedGoalSet clarification(ClarificationProposal proposal) {
        return new ResolvedGoalSet(Kind.CLARIFICATION, null,
                Objects.requireNonNull(proposal, "proposal"), null);
    }

    public static ResolvedGoalSet conversational(String message) {
        return message(Kind.CONVERSATIONAL, message);
    }

    public static ResolvedGoalSet boundary(String message) {
        return message(Kind.BOUNDARY, message);
    }

    public static ResolvedGoalSet capabilityUnavailable(String message) {
        return message(Kind.CAPABILITY_UNAVAILABLE, message);
    }

    public static ResolvedGoalSet invalidInput(String message) {
        return message(Kind.INVALID_INPUT, message);
    }

    private static ResolvedGoalSet message(Kind kind, String message) {
        if (message == null || message.isBlank() || message.length() > 400) {
            throw new IllegalArgumentException("resolved goal message is required and bounded");
        }
        return new ResolvedGoalSet(kind, null, null, message);
    }

    public Kind getKind() { return kind; }
    public Optional<UserGoalProposal> getGoalProposal() { return Optional.ofNullable(goalProposal); }
    public Optional<ClarificationProposal> getClarification() {
        return Optional.ofNullable(clarification);
    }
    public Optional<String> getMessage() { return Optional.ofNullable(message); }

    public enum Kind {
        GOALS, CLARIFICATION, CONVERSATIONAL, BOUNDARY, CAPABILITY_UNAVAILABLE, INVALID_INPUT
    }
}
