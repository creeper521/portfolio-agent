package com.portfolio.agent.turn.planning;

import java.util.Objects;
import java.util.Optional;

/**
 * Model-proposed closed semantics. This value never carries state handles,
 * tokens, tasks, providers or evidence.
 */
public final class SemanticRouteProposal {
    private final Route route;
    private final String candidateKey;
    private final UserGoalProposal goalProposal;
    private final ClarificationProposal clarification;

    private SemanticRouteProposal(
            Route route,
            String candidateKey,
            UserGoalProposal goalProposal,
            ClarificationProposal clarification) {
        this.route = Objects.requireNonNull(route, "route");
        this.candidateKey = candidateKey == null
                ? null : requireCandidateKey(candidateKey);
        this.goalProposal = goalProposal;
        this.clarification = clarification;
        validateShape();
    }

    public static SemanticRouteProposal standardGoal(UserGoalProposal goalProposal) {
        return new SemanticRouteProposal(
                Route.STANDARD_GOAL, null,
                Objects.requireNonNull(goalProposal, "goalProposal"), null);
    }

    public static SemanticRouteProposal needsClarification(
            ClarificationProposal clarification) {
        return new SemanticRouteProposal(
                Route.NEEDS_CLARIFICATION, null, null,
                Objects.requireNonNull(clarification, "clarification"));
    }

    public static SemanticRouteProposal needsClarification() {
        return new SemanticRouteProposal(
                Route.NEEDS_CLARIFICATION, null, null, null);
    }

    public static SemanticRouteProposal enterRecommendedResult(String candidateKey) {
        return new SemanticRouteProposal(
                Route.ENTER_RECOMMENDED_RESULT, candidateKey, null, null);
    }

    public static SemanticRouteProposal discussion(
            Route route, String candidateKey, UserGoalProposal goalProposal) {
        return new SemanticRouteProposal(route, candidateKey, goalProposal, null);
    }

    public static SemanticRouteProposal stateRoute(Route route) {
        if (route != Route.START_NEW_TOPIC
                && route != Route.REENTER_PROJECT) {
            throw new IllegalArgumentException(
                    "route is not a state-only route");
        }
        return new SemanticRouteProposal(route, null, null, null);
    }

    public Route getRoute() { return route; }
    public Optional<String> getCandidateKey() {
        return Optional.ofNullable(candidateKey);
    }
    public Optional<UserGoalProposal> getGoalProposal() {
        return Optional.ofNullable(goalProposal);
    }
    public Optional<ClarificationProposal> getClarification() {
        return Optional.ofNullable(clarification);
    }

    private void validateShape() {
        switch (route) {
            case STANDARD_GOAL -> require(
                    goalProposal != null && candidateKey == null && clarification == null);
            case ENTER_RECOMMENDED_RESULT, SWITCH_PROJECT -> require(
                    candidateKey != null && goalProposal == null && clarification == null);
            case NEEDS_CLARIFICATION -> require(
                    candidateKey == null && goalProposal == null);
            case CONTINUE_CURRENT_PROJECT -> require(
                    candidateKey == null && goalProposal != null && clarification == null);
            case START_NEW_TOPIC, REENTER_PROJECT -> require(
                    candidateKey == null && goalProposal == null && clarification == null);
        }
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException(
                    "semantic route fields do not match route");
        }
    }

    private static String requireCandidateKey(String value) {
        if (!value.matches("C[1-5]")) {
            throw new IllegalArgumentException("candidate key is invalid");
        }
        return value;
    }

    public enum Route {
        STANDARD_GOAL,
        ENTER_RECOMMENDED_RESULT,
        CONTINUE_CURRENT_PROJECT,
        START_NEW_TOPIC,
        SWITCH_PROJECT,
        REENTER_PROJECT,
        NEEDS_CLARIFICATION
    }
}
