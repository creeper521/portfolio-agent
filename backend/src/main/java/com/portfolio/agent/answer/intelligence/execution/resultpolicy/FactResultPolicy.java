package com.portfolio.agent.answer.intelligence.execution.resultpolicy;

import com.portfolio.agent.answer.domain.GroundedAnswerContribution;
import com.portfolio.agent.answer.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import com.portfolio.agent.answer.intelligence.execution.support.EvidenceSupportAssessment;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle;

/** Fact result policy; it never fills omitted facets with invented text. */
public final class FactResultPolicy implements PortfolioResultPolicy {
    @Override
    public PortfolioAnswerMaterial material(
            ValidatedEvidenceBundle bundle, EvidenceSupportAssessment assessment, String title) {
        return PortfolioAnswerMaterial.fromContribution(
                PortfolioAnswerMaterial.MaterialKind.FACT, title, contribution(assessment));
    }

    private GroundedAnswerContribution contribution(EvidenceSupportAssessment assessment) {
        return contributionOf(assessment);
    }

    static GroundedAnswerContribution contributionOf(EvidenceSupportAssessment assessment) {
        java.util.List<String> statements = assessment.getSelectedUnits().stream()
                .map(unit -> unit.getClaim().getStatement() + " " + unit.getClaim().getDetail()).toList();
        java.util.List<String> references = assessment.getSelectedUnits().stream()
                .map(unit -> unit.getSourceReference().getReferenceKey()).distinct().toList();
        java.util.List<PublicSourceReferenceValue> sourceReferences = assessment.getSelectedUnits().stream()
                .map(unit -> unit.getSourceReference())
                .map(reference -> new PublicSourceReferenceValue(reference.getReferenceKey(), reference.getLabel(),
                        reference.getPublishedVersion(),
                        reference.getSourceType().name(), reference.getSubjectRoute(), reference.getEvidenceRoute()))
                .distinct().toList();
        return new GroundedAnswerContribution(statements, references, sourceReferences, java.util.List.of(),
                assessment.getOmittedLabels());
    }
}
