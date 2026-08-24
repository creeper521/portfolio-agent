package com.portfolio.agent.turn.capability.portfolio;

import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PortfolioEvidenceInvocation {
    private final SemanticTask.Type taskType;
    private final AuthorizedSubjectScope subjectScope;
    private final List<FacetProfile> facets;
    private final List<String> dimensions;
    private final UserGoalProposal.Depth depth;
    private final int requestedSize;
    private final Set<String> recommendationConstraints;
    private final String contentReleaseId;
    private final CorpusBackend primaryBackend;
    private final SearchStrategy primaryStrategy;
    private final CorpusBackend fallbackBackend;
    private final SearchStrategy fallbackStrategy;

    public PortfolioEvidenceInvocation(
            SemanticTask.Type taskType, AuthorizedSubjectScope subjectScope,
            List<FacetProfile> facets, List<String> dimensions,
            UserGoalProposal.Depth depth,
            int requestedSize, Set<String> recommendationConstraints,
            String contentReleaseId, CorpusBackend primaryBackend,
            SearchStrategy primaryStrategy, CorpusBackend fallbackBackend,
            SearchStrategy fallbackStrategy) {
        this.taskType = Objects.requireNonNull(taskType, "taskType");
        this.subjectScope = Objects.requireNonNull(subjectScope, "subjectScope");
        this.facets = List.copyOf(Objects.requireNonNull(facets, "facets"));
        this.dimensions = List.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
        for (String dimension : this.dimensions) {
            try {
                UserGoalProposal.PortfolioComparisonDimension.valueOf(dimension);
            } catch (IllegalArgumentException | NullPointerException unsupported) {
                throw new IllegalArgumentException("unsupported portfolio comparison dimension");
            }
        }
        this.depth = Objects.requireNonNull(depth, "depth");
        if (requestedSize < 0 || requestedSize > 5
                || taskType == SemanticTask.Type.PORTFOLIO_RECOMMEND && requestedSize < 1) {
            throw new IllegalArgumentException("recommendation requestedSize is invalid");
        }
        this.requestedSize = requestedSize;
        this.recommendationConstraints = Set.copyOf(Objects.requireNonNull(
                recommendationConstraints, "recommendationConstraints"));
        if (this.recommendationConstraints.stream().anyMatch(value ->
                !value.matches("(?:CAREER_TRACK|CAPABILITY)_[A-Z0-9_]{1,64}"))) {
            throw new IllegalArgumentException("recommendation constraint is invalid");
        }
        if (taskType != SemanticTask.Type.PORTFOLIO_RECOMMEND
                && (!this.recommendationConstraints.isEmpty() || requestedSize != 0)) {
            throw new IllegalArgumentException("recommendation inputs require recommendation task");
        }
        this.contentReleaseId = Objects.requireNonNull(contentReleaseId, "contentReleaseId");
        this.primaryBackend = Objects.requireNonNull(primaryBackend, "primaryBackend");
        this.primaryStrategy = Objects.requireNonNull(primaryStrategy, "primaryStrategy");
        this.fallbackBackend = fallbackBackend;
        this.fallbackStrategy = fallbackStrategy;
        if (!contentReleaseId.equals(subjectScope.getContentReleaseId())) {
            throw new IllegalArgumentException("subject scope release mismatch");
        }
        if (facets.isEmpty() && dimensions.isEmpty()) {
            throw new IllegalArgumentException("at least one retrieval profile is required");
        }
        if ((fallbackBackend == null) != (fallbackStrategy == null)) {
            throw new IllegalArgumentException("fallback backend and strategy must be paired");
        }
    }

    public PortfolioEvidenceInvocation(
            SemanticTask.Type taskType, AuthorizedSubjectScope subjectScope,
            List<FacetProfile> facets, List<String> dimensions,
            String contentReleaseId, CorpusBackend primaryBackend,
            SearchStrategy primaryStrategy, CorpusBackend fallbackBackend,
            SearchStrategy fallbackStrategy) {
        this(taskType, subjectScope, facets, dimensions,
                UserGoalProposal.Depth.STANDARD,
                taskType == SemanticTask.Type.PORTFOLIO_RECOMMEND ? 3 : 0,
                Set.of(), contentReleaseId,
                primaryBackend, primaryStrategy, fallbackBackend, fallbackStrategy);
    }

    public PortfolioEvidenceInvocation(
            SemanticTask.Type taskType, AuthorizedSubjectScope subjectScope,
            List<FacetProfile> facets, List<String> dimensions,
            UserGoalProposal.Depth depth,
            String contentReleaseId, CorpusBackend primaryBackend,
            SearchStrategy primaryStrategy, CorpusBackend fallbackBackend,
            SearchStrategy fallbackStrategy) {
        this(taskType, subjectScope, facets, dimensions, depth,
                taskType == SemanticTask.Type.PORTFOLIO_RECOMMEND ? 3 : 0,
                Set.of(),
                contentReleaseId, primaryBackend, primaryStrategy,
                fallbackBackend, fallbackStrategy);
    }

    public SemanticTask.Type getTaskType() { return taskType; }
    public AuthorizedSubjectScope getSubjectScope() { return subjectScope; }
    public List<FacetProfile> getFacets() { return facets; }
    public List<String> getDimensions() { return dimensions; }
    public UserGoalProposal.Depth getDepth() { return depth; }
    public int getRequestedSize() { return requestedSize; }
    public Set<String> getRecommendationConstraints() { return recommendationConstraints; }
    public String getRecommendationCareerTrack() {
        return recommendationConstraints.stream()
                .filter(value -> value.startsWith("CAREER_TRACK_"))
                .map(value -> value.substring("CAREER_TRACK_".length()))
                .findFirst().orElse(null);
    }
    public Set<String> getRecommendationCapabilityCodes() {
        return recommendationConstraints.stream()
                .filter(value -> value.startsWith("CAPABILITY_"))
                .map(value -> value.substring("CAPABILITY_".length()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public int getMaximumEvidenceUnitsPerSubject() {
        return switch (depth) {
            case CONCISE -> 2;
            case STANDARD -> 6;
            case DETAILED -> 12;
        };
    }
    public String getContentReleaseId() { return contentReleaseId; }
    public CorpusBackend getPrimaryBackend() { return primaryBackend; }
    public SearchStrategy getPrimaryStrategy() { return primaryStrategy; }
    public CorpusBackend getFallbackBackend() { return fallbackBackend; }
    public SearchStrategy getFallbackStrategy() { return fallbackStrategy; }

    public enum FacetProfile {
        BACKGROUND, RESPONSIBILITY, IMPLEMENTATION, TECHNICAL_DECISION,
        VERIFICATION, OUTCOME, LIMITATION, RECOMMENDATION
    }
}
