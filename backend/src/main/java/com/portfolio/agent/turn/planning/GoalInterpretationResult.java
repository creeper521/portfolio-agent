package com.portfolio.agent.turn.planning;

import java.util.Objects;
import java.util.Optional;

public final class GoalInterpretationResult {
    private final Kind kind;
    private final SemanticRouteProposal routeProposal;
    private final String message;

    private GoalInterpretationResult(
            Kind kind,
            SemanticRouteProposal routeProposal,
            String message) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.routeProposal = routeProposal;
        this.message = message;
    }

    public static GoalInterpretationResult semanticRoute(
            SemanticRouteProposal proposal) {
        return new GoalInterpretationResult(
                Kind.SEMANTIC_ROUTE,
                Objects.requireNonNull(proposal, "proposal"),
                null);
    }

    public static GoalInterpretationResult conversational(String message) {
        if (message == null || message.isBlank() || message.length() > 400) {
            throw new IllegalArgumentException(
                    "conversational message is required and bounded");
        }
        return new GoalInterpretationResult(
                Kind.CONVERSATIONAL, null, message);
    }

    public Kind getKind() { return kind; }
    public Optional<SemanticRouteProposal> getRouteProposal() {
        return Optional.ofNullable(routeProposal);
    }
    public Optional<String> getMessage() {
        return Optional.ofNullable(message);
    }

    public enum Kind {
        SEMANTIC_ROUTE,
        CONVERSATIONAL
    }
}
