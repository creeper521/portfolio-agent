package com.portfolio.agent.answer.composition.domain;

import java.util.List;
import java.util.Objects;

public final class RecommendationAnswerMaterial extends PortfolioAnswerMaterial {
    private final List<RecommendationCandidate> orderedCandidates;
    private final RefineSource refineSource;

    public RecommendationAnswerMaterial(String publicTitle,
            List<RecommendationCandidate> orderedCandidates, List<String> fixedGlobalCaveats,
            List<String> omittedTopicLabels, RefineSource refineSource) {
        super(publicTitle, fixedGlobalCaveats, omittedTopicLabels);
        this.orderedCandidates = List.copyOf(
                Objects.requireNonNull(orderedCandidates, "orderedCandidates"));
        DomainValues.distinctTextCopy(this.orderedCandidates.stream()
                .map(candidate -> candidate.getCandidateReference().getPublicLabel()).toList(),
                "candidateLabels");
        this.refineSource = Objects.requireNonNull(refineSource, "refineSource");
    }

    @Override public MaterialKind getMaterialKind() { return MaterialKind.RECOMMENDATION; }
    public List<RecommendationCandidate> getOrderedCandidates() { return orderedCandidates; }
    public RefineSource getRefineSource() { return refineSource; }
    @Override public List<ExpressionStatement> getExpressionStatements() {
        return orderedCandidates.stream().flatMap(candidate -> candidate.getOrderedCriteria().stream())
                .flatMap(criterion -> criterion.getStatementEntries().stream()).toList();
    }
    @Override public List<String> getPublicSubjectLabels() {
        return orderedCandidates.stream()
                .map(candidate -> candidate.getCandidateReference().getPublicLabel()).toList();
    }

    public static final class RecommendationCandidate {
        private final CandidateReference candidateReference;
        private final RecommendationTier recommendationTier;
        private final List<RecommendationCriterion> orderedCriteria;
        private final List<String> fixedItemCaveats;
        public RecommendationCandidate(CandidateReference candidateReference,
                RecommendationTier recommendationTier,
                List<RecommendationCriterion> orderedCriteria, List<String> fixedItemCaveats) {
            this.candidateReference = Objects.requireNonNull(candidateReference, "candidateReference");
            this.recommendationTier = Objects.requireNonNull(recommendationTier, "recommendationTier");
            this.orderedCriteria = List.copyOf(Objects.requireNonNull(orderedCriteria, "orderedCriteria"));
            DomainValues.distinctTextCopy(this.orderedCriteria.stream()
                    .map(RecommendationCriterion::getCriterionKey).toList(), "criterionKeys");
            this.fixedItemCaveats = DomainValues.distinctTextCopy(fixedItemCaveats, "fixedItemCaveats");
        }
        public CandidateReference getCandidateReference() { return candidateReference; }
        public RecommendationTier getRecommendationTier() { return recommendationTier; }
        public List<RecommendationCriterion> getOrderedCriteria() { return orderedCriteria; }
        public List<String> getFixedItemCaveats() { return fixedItemCaveats; }
    }

    public static final class RecommendationCriterion {
        private final String criterionKey;
        private final List<ExpressionStatement> statementEntries;
        public RecommendationCriterion(String criterionKey, List<ExpressionStatement> statementEntries) {
            this.criterionKey = DomainValues.requireText(criterionKey, "criterionKey");
            this.statementEntries = List.copyOf(
                    Objects.requireNonNull(statementEntries, "statementEntries"));
            requireUniqueOrders(this.statementEntries, "statementEntries");
        }
        public String getCriterionKey() { return criterionKey; }
        public List<ExpressionStatement> getStatementEntries() { return statementEntries; }
    }
}
