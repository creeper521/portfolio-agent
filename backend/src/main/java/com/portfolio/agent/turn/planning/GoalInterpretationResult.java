package com.portfolio.agent.turn.planning;

import java.util.Objects;
import java.util.Optional;

public final class GoalInterpretationResult {
    private final Kind kind;
    private final UserGoalProposal goalProposal;
    private final ClarificationProposal clarification;
    private final String message;

    private GoalInterpretationResult(
            Kind kind,
            UserGoalProposal goalProposal,
            ClarificationProposal clarification,
            String message) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.goalProposal = goalProposal;
        this.clarification = clarification;
        this.message = message;
    }

    public static GoalInterpretationResult goals(UserGoalProposal proposal) {
        return new GoalInterpretationResult(Kind.GOALS,
                Objects.requireNonNull(proposal, "proposal"), null, null);
    }

    public static GoalInterpretationResult clarification(ClarificationProposal proposal) {
        return new GoalInterpretationResult(Kind.CLARIFICATION, null,
                Objects.requireNonNull(proposal, "proposal"), null);
    }

    public static GoalInterpretationResult conversational(String message) {
        if (message == null || message.isBlank() || message.length() > 400) {
            throw new IllegalArgumentException("conversational message is required and bounded");
        }
        return new GoalInterpretationResult(Kind.CONVERSATIONAL, null, null, message);
    }

    public Kind getKind() { return kind; }
    public Optional<UserGoalProposal> getGoalProposal() { return Optional.ofNullable(goalProposal); }
    public Optional<ClarificationProposal> getClarification() {
        return Optional.ofNullable(clarification);
    }
    public Optional<String> getMessage() { return Optional.ofNullable(message); }

    public enum Kind { GOALS, CLARIFICATION, CONVERSATIONAL }
}
