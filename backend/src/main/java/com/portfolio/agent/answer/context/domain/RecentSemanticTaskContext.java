package com.portfolio.agent.answer.context.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Recent task context: only typed public scope and routing state. */
public final class RecentSemanticTaskContext {
    private final SemanticRoutingTypes.SemanticTaskType taskType;
    private final List<SubjectReference> publicSubjects;
    private final Set<String> facets;
    private final Set<String> dimensions;
    private final String contentVersion;
    private final String sourceTaskId;

    public RecentSemanticTaskContext(
            SemanticRoutingTypes.SemanticTaskType taskType, List<SubjectReference> publicSubjects,
            Set<String> facets, Set<String> dimensions, String contentVersion, String sourceTaskId) {
        this.taskType = Objects.requireNonNull(taskType, "taskType");
        this.publicSubjects = List.copyOf(Objects.requireNonNull(publicSubjects, "publicSubjects"));
        this.facets = normalizedSet(facets, "facets");
        this.dimensions = normalizedSet(dimensions, "dimensions");
        this.contentVersion = requireText(contentVersion, "contentVersion");
        this.sourceTaskId = requireText(sourceTaskId, "sourceTaskId");
        if (this.publicSubjects.isEmpty()) throw new IllegalArgumentException("publicSubjects are required");
    }
    public SemanticRoutingTypes.SemanticTaskType getTaskType() { return taskType; }
    public List<SubjectReference> getPublicSubjects() { return publicSubjects; }
    public Set<String> getFacets() { return facets; }
    public Set<String> getDimensions() { return dimensions; }
    public String getContentVersion() { return contentVersion; }
    public String getSourceTaskId() { return sourceTaskId; }
    @Override public String toString() { return "RecentSemanticTaskContext{taskType=" + taskType + ", subjectCount=" + publicSubjects.size() + '}'; }
    private static Set<String> normalizedSet(Set<String> values, String name) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : Objects.requireNonNull(values, name)) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " contains blank");
            result.add(value.trim().toUpperCase(java.util.Locale.ROOT));
        }
        return Set.copyOf(result);
    }
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
