package com.portfolio.agent.turn.planning;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GoalResolutionContext {
    private final List<GoalInterpretationInput.PublicSubjectDescriptor> publicSubjects;
    private final Set<GoalKind> allowedGoalKinds;

    public GoalResolutionContext(
            List<GoalInterpretationInput.PublicSubjectDescriptor> publicSubjects,
            Set<GoalKind> allowedGoalKinds) {
        this.publicSubjects = List.copyOf(Objects.requireNonNull(publicSubjects, "publicSubjects"));
        this.allowedGoalKinds = Set.copyOf(Objects.requireNonNull(allowedGoalKinds, "allowedGoalKinds"));
    }

    public List<GoalInterpretationInput.PublicSubjectDescriptor> getPublicSubjects() {
        return publicSubjects;
    }

    public Set<GoalKind> getAllowedGoalKinds() {
        return allowedGoalKinds;
    }

    public boolean matchesHint(com.portfolio.agent.turn.lifecycle.AgentTurnCommand.SubjectHint hint) {
        return hint == null || publicSubjects.stream().anyMatch(subject ->
                subject.getKind().name().equals(hint.getKind().name())
                        && subject.matchesAlias(hint.getSlug()));
    }
}
