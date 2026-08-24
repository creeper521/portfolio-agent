package com.portfolio.agent.turn.planning;

import java.util.Objects;

/** Validates model semantics against backend-owned typed scope. */
public final class SemanticRouteValidator {

    public SemanticRouteProposal validate(
            SemanticRouteProposal proposal,
            GoalInterpretationInput input) {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(input, "input");
        if (!input.getAllowedRoutes().contains(proposal.getRoute())) {
            throw new IllegalArgumentException("semantic route is not allowed");
        }
        proposal.getCandidateKey().ifPresent(candidateKey -> {
            if (input.getRouteCandidates().stream().noneMatch(
                    candidate -> candidate.getCandidateKey().equals(candidateKey))) {
                throw new IllegalArgumentException(
                        "semantic route candidate is outside typed scope");
            }
        });
        SemanticRouteProposal validated = switch (proposal.getRoute()) {
            case CONTINUE_CURRENT_PROJECT -> lockDiscussionGoal(proposal, input);
            case STANDARD_GOAL -> bindDefaultSubject(proposal, input);
            default -> proposal;
        };
        validated.getGoalProposal().ifPresent(goalProposal ->
                validateGoals(goalProposal, input));
        return validated;
    }

    private SemanticRouteProposal bindDefaultSubject(
            SemanticRouteProposal proposal,
            GoalInterpretationInput input) {
        GoalInterpretationInput.PublicSubjectDescriptor defaultSubject =
                input.getDefaultSubject();
        if (input.getInterpretationMode()
                != GoalInterpretationInput.InterpretationMode.STANDARD
                || defaultSubject == null
                || proposal.getGoalProposal().isEmpty()) {
            return proposal;
        }
        UserGoalProposal source = proposal.getGoalProposal().orElseThrow();
        java.util.List<UserGoalProposal.ProposedGoal> goals = source.getGoals().stream()
                .map(goal -> bindDefaultSubject(goal, defaultSubject)).toList();
        return SemanticRouteProposal.standardGoal(new UserGoalProposal(goals));
    }

    private UserGoalProposal.ProposedGoal bindDefaultSubject(
            UserGoalProposal.ProposedGoal goal,
            GoalInterpretationInput.PublicSubjectDescriptor defaultSubject) {
        if (!goal.getSubjectCandidates().isEmpty()
                || goal.getGoalKind() != GoalKind.PORTFOLIO_FACT
                && goal.getGoalKind() != GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO) {
            return goal;
        }
        return new UserGoalProposal.ProposedGoal(
                goal.getGoalKey(), goal.getGoalKind(), goal.getInputAnchor(),
                java.util.List.of(new GoalSubjectReference(
                        defaultSubject.getKind(), defaultSubject.getReference(),
                        GoalSubjectReference.Basis.SURFACE_HINT, null)),
                goal.getRequestedOutputs(), goal.getKnowledgeRequirement(),
                goal.getParameters());
    }

    private SemanticRouteProposal lockDiscussionGoal(
            SemanticRouteProposal proposal,
            GoalInterpretationInput input) {
        if (input.getInterpretationMode()
                != GoalInterpretationInput.InterpretationMode.DISCUSSION
                || input.getDiscussionState()
                != GoalInterpretationInput.DiscussionState.ACTIVE
                || input.getLockedSubject() == null) {
            throw new IllegalArgumentException(
                    "discussion route requires an active locked subject");
        }
        UserGoalProposal source =
                proposal.getGoalProposal().orElseThrow();
        if (source.getGoals().size() != 1) {
            throw new IllegalArgumentException(
                    "discussion route requires exactly one goal");
        }
        UserGoalProposal.ProposedGoal goal =
                source.getGoals().getFirst();
        if (!goal.getSubjectCandidates().isEmpty()
                || goal.getGoalKind() != GoalKind.PORTFOLIO_FACT
                && goal.getGoalKind()
                != GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO) {
            throw new IllegalArgumentException(
                    "discussion goal cannot propose its own subject");
        }
        GoalInterpretationInput.PublicSubjectDescriptor locked =
                input.getLockedSubject();
        UserGoalProposal.ProposedGoal bound =
                new UserGoalProposal.ProposedGoal(
                        goal.getGoalKey(),
                        goal.getGoalKind(),
                        goal.getInputAnchor(),
                        java.util.List.of(
                                new GoalSubjectReference(
                                        locked.getKind(),
                                        locked.getReference(),
                                        GoalSubjectReference.Basis.CONTINUATION,
                                        null)),
                        goal.getRequestedOutputs(),
                        goal.getKnowledgeRequirement(),
                        goal.getParameters());
        return SemanticRouteProposal.discussion(
                proposal.getRoute(), null,
                new UserGoalProposal(java.util.List.of(bound)));
    }

    private void validateGoals(
            UserGoalProposal proposal,
            GoalInterpretationInput input) {
        for (UserGoalProposal.ProposedGoal goal : proposal.getGoals()) {
            if (!input.getAllowedGoalKinds().contains(goal.getGoalKind())) {
                throw new IllegalArgumentException(
                        "semantic route goal kind is not allowed");
            }
            for (GoalSubjectReference subject : goal.getSubjectCandidates()) {
                if (subject.getKind() == GoalSubjectReference.Kind.RESULT
                        || !input.containsPublicSubject(
                        subject.getKind(), subject.getReference())) {
                    throw new IllegalArgumentException(
                            "semantic route subject is outside public scope");
                }
            }
            if (goal.getParameters()
                    instanceof UserGoalProposal.PortfolioRecommendationParameters parameters
                    && (parameters.getRequestedSize() < 1
                    || parameters.getRequestedSize() > 5)) {
                throw new IllegalArgumentException(
                        "semantic route recommendation size is invalid");
            }
        }
    }
}
