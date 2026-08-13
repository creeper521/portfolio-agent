package com.portfolio.agent.answer.intelligence.execution.resultpolicy;

import com.portfolio.agent.answer.composition.domain.ComparisonAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.CoverageState;
import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.SubjectReference;
import com.portfolio.agent.answer.composition.domain.SupportTarget;
import com.portfolio.agent.answer.intelligence.execution.domain.CandidateSubject;
import com.portfolio.agent.answer.intelligence.execution.support.EvidenceSupportAssessment;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceUnit;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** P4.1 comparison remains deterministic while using the same typed seam. */
public final class ComparisonResultPolicy implements PortfolioResultPolicy {
    @Override
    public PortfolioAnswerMaterial material(SemanticTask task, ValidatedEvidenceBundle bundle,
            EvidenceSupportAssessment assessment, List<CandidateSubject> publicSubjects) {
        List<CandidateSubject> selected = task.getSubjectReferences().stream()
                .map(reference -> publicSubjects.stream()
                        .filter(subject -> reference.getSubjectId().equals(subject.getSubjectId()))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("public subject unavailable")))
                .toList();
        List<SubjectReference> subjects = selected.stream()
                .map(subject -> new SubjectReference(subject.getTitle())).toList();
        Map<String, SubjectReference> references = new LinkedHashMap<>();
        Map<String, CandidateSubject> ownership = new LinkedHashMap<>();
        for (int index = 0; index < selected.size(); index++) {
            references.put(selected.get(index).getSubjectId(), subjects.get(index));
            ownership.put(selected.get(index).getSubjectId(), selected.get(index));
        }
        FactResultPolicy.requireAuthoritativeOwnership(
                assessment.getSelectedUnits(), ownership);
        List<ComparisonAnswerMaterial.SubjectCell> cells = selected.stream().map(subject -> {
            List<ValidatedEvidenceUnit> units = assessment.getSelectedUnits().stream()
                    .filter(unit -> subject.getSubjectId().equals(unit.getSubjectId())).toList();
            List<ExpressionStatement> entries = FactResultPolicy.entries(
                    units, references, SupportTarget.DIMENSION);
            return new ComparisonAnswerMaterial.SubjectCell(references.get(subject.getSubjectId()),
                    entries.isEmpty() ? CoverageState.UNCOVERED : CoverageState.COVERED, entries);
        }).toList();
        ComparisonAnswerMaterial.ComparisonDimension dimension =
                new ComparisonAnswerMaterial.ComparisonDimension(
                "APPROVED_EVIDENCE", cells, null);
        return new ComparisonAnswerMaterial(String.join(" 与 ", subjects.stream()
                .map(SubjectReference::getPublicLabel).toList()), subjects, List.of(dimension),
                List.of(), assessment.getOmittedLabels());
    }
}
