package com.portfolio.agent.turn.planning;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GoalResolutionContext {
    private final List<GoalInterpretationInput.PublicSubjectDescriptor> publicSubjects;
    private final Set<GoalKind> allowedGoalKinds;
    private final Set<String> allowedRecommendationConstraints;

    public GoalResolutionContext(
            List<GoalInterpretationInput.PublicSubjectDescriptor> publicSubjects,
            Set<GoalKind> allowedGoalKinds) {
        this(publicSubjects, allowedGoalKinds, Set.of());
    }

    public GoalResolutionContext(
            List<GoalInterpretationInput.PublicSubjectDescriptor> publicSubjects,
            Set<GoalKind> allowedGoalKinds,
            Set<String> allowedRecommendationConstraints) {
        this.publicSubjects = List.copyOf(Objects.requireNonNull(publicSubjects, "publicSubjects"));
        this.allowedGoalKinds = Set.copyOf(Objects.requireNonNull(allowedGoalKinds, "allowedGoalKinds"));
        this.allowedRecommendationConstraints = Set.copyOf(Objects.requireNonNull(
                allowedRecommendationConstraints, "allowedRecommendationConstraints"));
    }

    public List<GoalInterpretationInput.PublicSubjectDescriptor> getPublicSubjects() {
        return publicSubjects;
    }

    public Set<GoalKind> getAllowedGoalKinds() {
        return allowedGoalKinds;
    }

    public Set<String> getAllowedRecommendationConstraints() {
        return allowedRecommendationConstraints;
    }

    public boolean matchesHint(com.portfolio.agent.turn.lifecycle.AgentTurnCommand.SubjectHint hint) {
        return hint == null || resolveHint(hint) != null;
    }

    public GoalInterpretationInput.PublicSubjectDescriptor resolveHint(
            com.portfolio.agent.turn.lifecycle.AgentTurnCommand.SubjectHint hint) {
        if (hint == null) return null;
        return publicSubjects.stream().filter(subject ->
                subject.getKind().name().equals(hint.getKind().name())
                        && subject.matchesAlias(hint.getSlug()))
                .findFirst().orElse(null);
    }
}
