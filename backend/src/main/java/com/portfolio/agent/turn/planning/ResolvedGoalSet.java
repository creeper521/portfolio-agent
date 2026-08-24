package com.portfolio.agent.turn.planning;

import java.util.Objects;
import java.util.Optional;

public final class ResolvedGoalSet {
    private final Kind kind;
    private final UserGoalProposal goalProposal;
    private final ClarificationProposal clarification;
    private final String message;
    private final MessageSource messageSource;

    private ResolvedGoalSet(
            Kind kind,
            UserGoalProposal goalProposal,
            ClarificationProposal clarification,
            String message,
            MessageSource messageSource) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.goalProposal = goalProposal;
        this.clarification = clarification;
        this.message = message;
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
    }

    public static ResolvedGoalSet goals(UserGoalProposal proposal) {
        return new ResolvedGoalSet(
                Kind.GOALS, Objects.requireNonNull(proposal, "proposal"), null, null,
                MessageSource.NONE);
    }

    public static ResolvedGoalSet clarification(ClarificationProposal proposal) {
        return new ResolvedGoalSet(Kind.CLARIFICATION, null,
                Objects.requireNonNull(proposal, "proposal"), null, MessageSource.NONE);
    }

    public static ResolvedGoalSet conversational(String message) {
        return message(Kind.CONVERSATIONAL, message, MessageSource.SERVER_FIXED);
    }

    public static ResolvedGoalSet providerConversational(String message) {
        return message(Kind.CONVERSATIONAL, message, MessageSource.PROVIDER_DERIVED);
    }

    public static ResolvedGoalSet boundary(String message) {
        return message(Kind.BOUNDARY, message, MessageSource.SERVER_FIXED);
    }

    public static ResolvedGoalSet capabilityUnavailable(String message) {
        return message(Kind.CAPABILITY_UNAVAILABLE, message, MessageSource.SERVER_FIXED);
    }

    public static ResolvedGoalSet invalidInput(String message) {
        return message(Kind.INVALID_INPUT, message, MessageSource.SERVER_FIXED);
    }

    private static ResolvedGoalSet message(
            Kind kind, String message, MessageSource messageSource) {
        if (message == null || message.isBlank() || message.length() > 400) {
            throw new IllegalArgumentException("resolved goal message is required and bounded");
        }
        return new ResolvedGoalSet(kind, null, null, message, messageSource);
    }

    public Kind getKind() { return kind; }
    public Optional<UserGoalProposal> getGoalProposal() { return Optional.ofNullable(goalProposal); }
    public Optional<ClarificationProposal> getClarification() {
        return Optional.ofNullable(clarification);
    }
    public Optional<String> getMessage() { return Optional.ofNullable(message); }
    public MessageSource getMessageSource() { return messageSource; }

    public enum Kind {
        GOALS, CLARIFICATION, CONVERSATIONAL, BOUNDARY, CAPABILITY_UNAVAILABLE, INVALID_INPUT
    }

    public enum MessageSource {
        NONE, SERVER_FIXED, PROVIDER_DERIVED
    }
}
