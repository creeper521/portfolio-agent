package com.portfolio.agent.answer.intelligence.execution.resultpolicy;

import com.portfolio.agent.answer.domain.GroundedAnswerContribution;
import com.portfolio.agent.answer.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import com.portfolio.agent.answer.intelligence.execution.support.EvidenceSupportAssessment;
import com.portfolio.agent.answer.intelligence.execution.support.RecommendationRankingPolicy;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle;

public final class RecommendationResultPolicy implements PortfolioResultPolicy {
    private final RecommendationRankingPolicy rankingPolicy;

    public RecommendationResultPolicy() { this(new RecommendationRankingPolicy()); }
    public RecommendationResultPolicy(RecommendationRankingPolicy rankingPolicy) {
        this.rankingPolicy = java.util.Objects.requireNonNull(rankingPolicy, "rankingPolicy");
    }

    @Override
    public PortfolioAnswerMaterial material(
            ValidatedEvidenceBundle bundle, EvidenceSupportAssessment assessment, String title) {
        java.util.List<com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceUnit> units =
                rankingPolicy.rank(assessment.getSelectedUnits());
        java.util.List<String> statements = units.stream()
                .map(unit -> unit.getClaim().getStatement() + " " + unit.getClaim().getDetail()).toList();
        java.util.List<String> references = units.stream()
                .map(unit -> unit.getSourceReference().getReferenceKey()).distinct().toList();
        java.util.List<PublicSourceReferenceValue> sourceReferences = units.stream()
                .map(unit -> unit.getSourceReference())
                .map(reference -> new PublicSourceReferenceValue(reference.getReferenceKey(), reference.getLabel(),
                        reference.getPublishedVersion(),
                        reference.getSourceType().name(), reference.getSubjectRoute(), reference.getEvidenceRoute()))
                .distinct().toList();
        GroundedAnswerContribution contribution = new GroundedAnswerContribution(
                statements, references, sourceReferences, java.util.List.of(), assessment.getOmittedLabels());
        return PortfolioAnswerMaterial.fromContribution(
                PortfolioAnswerMaterial.MaterialKind.RECOMMENDATION, title, contribution);
    }
}
