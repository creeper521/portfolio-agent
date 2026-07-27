package com.portfolio.agent.release.benchmark;

import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RetrievalRouteEvaluation {

    private final RetrievalBenchmarkRoute route;
    private final String caseId;
    private final RetrievalBenchmarkSplit split;
    private final RetrievalBenchmarkCategory category;
    private final ClaimSubjectType subjectType;
    private final String subjectSlug;
    private final RetrievalDecisionType expectedDecision;
    private final RetrievalDecisionType actualDecision;
    private final Integer expectedRank;
    private final List<RetrievalExpectedRank> expectedClaimRanks;
    private final List<RetrievalExpectedRank> expectedChunkRanks;
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
        this(
                route,
                caseId,
                split,
                category,
                null,
                null,
                expectedDecision,
                actualDecision,
                expectedRank,
                List.of(),
                List.of(),
                selectedClaimIds,
                selectedChunkIds
        );
    }

    public RetrievalRouteEvaluation(
            RetrievalBenchmarkRoute route,
            String caseId,
            RetrievalBenchmarkSplit split,
            RetrievalBenchmarkCategory category,
            ClaimSubjectType subjectType,
            String subjectSlug,
            RetrievalDecisionType expectedDecision,
            RetrievalDecisionType actualDecision,
            Integer expectedRank,
            List<RetrievalExpectedRank> expectedClaimRanks,
            List<RetrievalExpectedRank> expectedChunkRanks,
            List<String> selectedClaimIds,
            List<String> selectedChunkIds
    ) {
        this.route = route;
        this.caseId = caseId;
        this.split = split;
        this.category = category;
        this.subjectType = subjectType;
        this.subjectSlug = subjectSlug;
        this.expectedDecision = expectedDecision;
        this.actualDecision = actualDecision;
        this.expectedClaimRanks = stableRanks(expectedClaimRanks);
        this.expectedChunkRanks = stableRanks(expectedChunkRanks);
        Integer bestRank = bestRank(
                this.expectedClaimRanks, this.expectedChunkRanks);
        this.expectedRank = this.expectedClaimRanks.isEmpty()
                && this.expectedChunkRanks.isEmpty()
                ? expectedRank
                : bestRank;
        this.selectedClaimIds = List.copyOf(selectedClaimIds);
        this.selectedChunkIds = List.copyOf(selectedChunkIds);
    }

    public RetrievalBenchmarkRoute getRoute() { return route; }
    public String getCaseId() { return caseId; }
    public RetrievalBenchmarkSplit getSplit() { return split; }
    public RetrievalBenchmarkCategory getCategory() { return category; }
    public ClaimSubjectType getSubjectType() { return subjectType; }
    public String getSubjectSlug() { return subjectSlug; }
    public RetrievalDecisionType getExpectedDecision() { return expectedDecision; }
    public RetrievalDecisionType getActualDecision() { return actualDecision; }
    public Integer getExpectedRank() { return expectedRank; }
    public List<RetrievalExpectedRank> getExpectedClaimRanks() {
        return expectedClaimRanks;
    }
    public List<RetrievalExpectedRank> getExpectedChunkRanks() {
        return expectedChunkRanks;
    }
    public List<String> getSelectedClaimIds() { return selectedClaimIds; }
    public List<String> getSelectedChunkIds() { return selectedChunkIds; }

    private List<RetrievalExpectedRank> stableRanks(
            List<RetrievalExpectedRank> source
    ) {
        List<RetrievalExpectedRank> copy = new ArrayList<>(source);
        copy.sort(Comparator
                .comparing(RetrievalExpectedRank::getTargetType)
                .thenComparing(RetrievalExpectedRank::getTargetId));
        return List.copyOf(copy);
    }

    private Integer bestRank(
            List<RetrievalExpectedRank> claimRanks,
            List<RetrievalExpectedRank> chunkRanks
    ) {
        Integer best = null;
        for (RetrievalExpectedRank expected
                : concat(claimRanks, chunkRanks)) {
            Integer rank = expected.getRank();
            if (rank != null && (best == null || rank < best)) {
                best = rank;
            }
        }
        return best;
    }

    private List<RetrievalExpectedRank> concat(
            List<RetrievalExpectedRank> first,
            List<RetrievalExpectedRank> second
    ) {
        List<RetrievalExpectedRank> combined =
                new ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return combined;
    }
}
