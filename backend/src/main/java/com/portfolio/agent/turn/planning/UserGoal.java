package com.portfolio.agent.turn.planning;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class UserGoal {
    private final String goalId;
    private final String label;
    private final GoalKind kind;
    private final List<GoalSubjectReference> subjects;
    private final Set<GoalRequestedOutput> requestedOutputs;
    private final String fulfillmentTaskId;

    public UserGoal(
            String goalId, String label, GoalKind kind,
            List<GoalSubjectReference> subjects,
            Set<GoalRequestedOutput> requestedOutputs,
            String fulfillmentTaskId) {
        this.goalId = requireId(goalId, "goalId");
        if (label == null || label.isBlank() || label.length() > 200) {
            throw new IllegalArgumentException("goal label is required and bounded");
        }
        this.label = label;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        this.requestedOutputs = Set.copyOf(Objects.requireNonNull(requestedOutputs, "requestedOutputs"));
        this.fulfillmentTaskId = requireId(fulfillmentTaskId, "fulfillmentTaskId");
    }

    public String getGoalId() { return goalId; }
    public String getLabel() { return label; }
    public GoalKind getKind() { return kind; }
    public List<GoalSubjectReference> getSubjects() { return subjects; }
    public Set<GoalRequestedOutput> getRequestedOutputs() { return requestedOutputs; }
    public String getFulfillmentTaskId() { return fulfillmentTaskId; }

    static String requireId(String value, String name) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9-]{1,95}")) {
            throw new IllegalArgumentException(name + " format is invalid");
        }
        return value;
    }
}
