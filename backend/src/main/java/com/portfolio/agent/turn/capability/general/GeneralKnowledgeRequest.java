package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Typed and bounded input for the General knowledge provider. */
public final class GeneralKnowledgeRequest {
    private final Kind kind;
    private final String topic;
    private final List<String> subjects;
    private final Set<String> dimensions;
    private final UserGoalProposal.Depth depth;
    private final Audience audience;
    private final String expectedContentVersion;
    private final TurnDeadline deadline;

    private GeneralKnowledgeRequest(
            Kind kind, String topic, List<String> subjects, Set<String> dimensions,
            UserGoalProposal.Depth depth, Audience audience,
            String expectedContentVersion, TurnDeadline deadline) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.topic = topic == null ? null : requireText(topic, "topic");
        this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        this.dimensions = Set.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
        this.depth = Objects.requireNonNull(depth, "depth");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.expectedContentVersion = requireText(expectedContentVersion, "expectedContentVersion");
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        if (kind == Kind.EXPLANATION && (this.topic == null
                || !this.subjects.isEmpty() || !this.dimensions.isEmpty())) {
            throw new IllegalArgumentException("explanation request shape is invalid");
        }
        if (kind == Kind.COMPARISON && (this.topic != null
                || this.subjects.size() < 2 || this.subjects.size() > 5
                || this.dimensions.isEmpty())) {
            throw new IllegalArgumentException("comparison request shape is invalid");
        }
        this.subjects.forEach(value -> requireText(value, "subject"));
        this.dimensions.forEach(value -> requireClosedName(value, "dimension"));
    }

    public static GeneralKnowledgeRequest explanation(
            String topic, UserGoalProposal.Depth depth, Audience audience,
            String expectedContentVersion, TurnDeadline deadline) {
        return new GeneralKnowledgeRequest(
                Kind.EXPLANATION, topic, List.of(), Set.of(), depth, audience,
                expectedContentVersion, deadline);
    }

    public static GeneralKnowledgeRequest comparison(
            List<String> subjects, Set<String> dimensions, Audience audience,
            String expectedContentVersion, TurnDeadline deadline) {
        return new GeneralKnowledgeRequest(
                Kind.COMPARISON, null, subjects, dimensions,
                UserGoalProposal.Depth.STANDARD, audience,
                expectedContentVersion, deadline);
    }

    public Kind getKind() { return kind; }
    public String getTopic() { return topic; }
    public List<String> getSubjects() { return subjects; }
    public Set<String> getDimensions() { return dimensions; }
    public UserGoalProposal.Depth getDepth() { return depth; }
    public Audience getAudience() { return audience; }
    public String getExpectedContentVersion() { return expectedContentVersion; }
    public TurnDeadline getDeadline() { return deadline; }

    public enum Kind { EXPLANATION, COMPARISON }
    public enum Audience { INTERVIEWER, MENTOR, HR, GUEST }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }

    private static String requireClosedName(String value, String name) {
        if (value == null || !value.matches("[A-Z_]{1,64}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
