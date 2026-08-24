package com.portfolio.agent.turn.capability.portfolio;

import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.List;
import java.util.Objects;

public final class PortfolioEvidenceInvocation {
    private final SemanticTask.Type taskType;
    private final AuthorizedSubjectScope subjectScope;
    private final List<FacetProfile> facets;
    private final List<String> dimensions;
    private final UserGoalProposal.Depth depth;
    private final String contentReleaseId;
    private final CorpusBackend primaryBackend;
    private final SearchStrategy primaryStrategy;
    private final CorpusBackend fallbackBackend;
    private final SearchStrategy fallbackStrategy;

    public PortfolioEvidenceInvocation(
            SemanticTask.Type taskType, AuthorizedSubjectScope subjectScope,
            List<FacetProfile> facets, List<String> dimensions,
            UserGoalProposal.Depth depth,
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
                UserGoalProposal.Depth.STANDARD, contentReleaseId,
                primaryBackend, primaryStrategy, fallbackBackend, fallbackStrategy);
    }

    public SemanticTask.Type getTaskType() { return taskType; }
    public AuthorizedSubjectScope getSubjectScope() { return subjectScope; }
    public List<FacetProfile> getFacets() { return facets; }
    public List<String> getDimensions() { return dimensions; }
    public UserGoalProposal.Depth getDepth() { return depth; }
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
