package com.portfolio.agent.release.benchmark;

import com.portfolio.agent.answer.domain.RetrievalDecisionType;

import java.util.List;

public final class RetrievalRouteEvaluation {

    private final RetrievalBenchmarkRoute route;
    private final String caseId;
    private final RetrievalBenchmarkSplit split;
    private final RetrievalBenchmarkCategory category;
    private final RetrievalDecisionType expectedDecision;
    private final RetrievalDecisionType actualDecision;
    private final Integer expectedRank;
    private final List<String> selectedClaimIds;
    private final List<String> selectedChunkIds;

    public RetrievalRouteEvaluation(
            RetrievalBenchmarkRoute route,
            String caseId,
            RetrievalBenchmarkSplit split,
            RetrievalBenchmarkCategory category,
            RetrievalDecisionType expectedDecision,
            RetrievalDecisionType actualDecision,
            Integer expectedRank,
            List<String> selectedClaimIds,
            List<String> selectedChunkIds
    ) {
        this.route = route;
        this.caseId = caseId;
        this.split = split;
        this.category = category;
        this.expectedDecision = expectedDecision;
        this.actualDecision = actualDecision;
        this.expectedRank = expectedRank;
        this.selectedClaimIds = List.copyOf(selectedClaimIds);
        this.selectedChunkIds = List.copyOf(selectedChunkIds);
    }

    public RetrievalBenchmarkRoute getRoute() { return route; }
    public String getCaseId() { return caseId; }
    public RetrievalBenchmarkSplit getSplit() { return split; }
    public RetrievalBenchmarkCategory getCategory() { return category; }
    public RetrievalDecisionType getExpectedDecision() { return expectedDecision; }
    public RetrievalDecisionType getActualDecision() { return actualDecision; }
    public Integer getExpectedRank() { return expectedRank; }
    public List<String> getSelectedClaimIds() { return selectedClaimIds; }
    public List<String> getSelectedChunkIds() { return selectedChunkIds; }
}
