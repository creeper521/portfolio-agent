package com.portfolio.agent.turn.planning;

import java.util.List;
import java.util.Objects;

public final class SemanticTaskParameters {
    private final GoalKind sourceGoalKind;
    private final UserGoalProposal.GoalParameters parameters;
    private final List<GoalSubjectReference> subjects;

    public SemanticTaskParameters(
            GoalKind sourceGoalKind,
            UserGoalProposal.GoalParameters parameters,
            List<GoalSubjectReference> subjects) {
        this.sourceGoalKind = Objects.requireNonNull(sourceGoalKind, "sourceGoalKind");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
    }

    public GoalKind getSourceGoalKind() { return sourceGoalKind; }
    public UserGoalProposal.GoalParameters getParameters() { return parameters; }
    public List<GoalSubjectReference> getSubjects() { return subjects; }
}
