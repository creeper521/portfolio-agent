package com.portfolio.agent.selection.benchmark;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.agent.selection.domain.SelectionTarget;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PortfolioSelectionBenchmarkCase {
    private final String id;
    private final BenchmarkSplit split;
    private final SelectionTarget target;
    private final List<Set<String>> acceptableSubjectSets;
    private final Set<String> requiredCapabilities;

    @JsonCreator
    public PortfolioSelectionBenchmarkCase(
            @JsonProperty("id") String id,
            @JsonProperty("split") BenchmarkSplit split,
            @JsonProperty("target") SelectionTarget target,
            @JsonProperty("acceptableSubjectSets") List<Set<String>> acceptableSubjectSets,
            @JsonProperty("requiredCapabilities") Set<String> requiredCapabilities) {
        this.id = requireText(id, "id");
        this.split = Objects.requireNonNull(split, "split");
        this.target = Objects.requireNonNull(target, "target");
        Objects.requireNonNull(acceptableSubjectSets, "acceptableSubjectSets");
        if (acceptableSubjectSets.isEmpty() || acceptableSubjectSets.stream().anyMatch(Set::isEmpty)) {
            throw new IllegalArgumentException("acceptableSubjectSets must contain non-empty sets");
        }
        this.acceptableSubjectSets = acceptableSubjectSets.stream().map(Set::copyOf).toList();
        this.requiredCapabilities = Set.copyOf(
                Objects.requireNonNull(requiredCapabilities, "requiredCapabilities"));
    }

    public String getId() { return id; }
    public BenchmarkSplit getSplit() { return split; }
    public SelectionTarget getTarget() { return target; }
    public List<Set<String>> getAcceptableSubjectSets() { return acceptableSubjectSets; }
    public Set<String> getRequiredCapabilities() { return requiredCapabilities; }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
