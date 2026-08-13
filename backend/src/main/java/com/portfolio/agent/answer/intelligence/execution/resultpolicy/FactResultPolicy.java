package com.portfolio.agent.answer.intelligence.execution.resultpolicy;

import com.portfolio.agent.answer.composition.domain.ControlledPredicate;
import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.FactAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.FocusMode;
import com.portfolio.agent.answer.composition.domain.GroundedStatement;
import com.portfolio.agent.answer.composition.domain.OrderingPolicy;
import com.portfolio.agent.answer.composition.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.PresentationRole;
import com.portfolio.agent.answer.composition.domain.StatementType;
import com.portfolio.agent.answer.composition.domain.SubjectReference;
import com.portfolio.agent.answer.composition.domain.SummaryPolicy;
import com.portfolio.agent.answer.composition.domain.SupportTarget;
import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import com.portfolio.agent.answer.intelligence.execution.domain.CandidateSubject;
import com.portfolio.agent.answer.intelligence.execution.support.EvidenceSupportAssessment;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceUnit;
import com.portfolio.agent.answer.intelligence.execution.validation.PublicSourceReference;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds the closed, public P4 material directly from validated evidence. */
public final class FactResultPolicy implements PortfolioResultPolicy {
    @Override
    public PortfolioAnswerMaterial material(SemanticTask task, ValidatedEvidenceBundle bundle,
            EvidenceSupportAssessment assessment, List<CandidateSubject> publicSubjects) {
        CandidateSubject subject = publicSubjects.stream()
                .filter(value -> task.getSubjectReferences().stream()
                        .anyMatch(reference -> reference.getSubjectId().equals(value.getSubjectId())))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("public subject unavailable"));
        Map<String, CandidateSubject> ownership = Map.of(subject.getSubjectId(), subject);
        requireAuthoritativeOwnership(assessment.getSelectedUnits(), ownership);
        SubjectReference reference = new SubjectReference(subject.getTitle());
        List<ExpressionStatement> entries = entries(assessment.getSelectedUnits(),
                Map.of(subject.getSubjectId(), reference), SupportTarget.SUBJECT);
        Map<AnswerSectionType, List<ExpressionStatement>> bySection = new LinkedHashMap<>();
        entries.forEach(entry -> bySection.computeIfAbsent(entry.getAllowedSection(), ignored -> new ArrayList<>())
                .add(entry));
        List<FactAnswerMaterial.FactSection> sections = bySection.entrySet().stream()
                .map(entry -> new FactAnswerMaterial.FactSection(entry.getKey(), entry.getValue(),
                        OrderingPolicy.STABLE)).toList();
        FocusMode focus = task.getParameters() instanceof
                com.portfolio.agent.answer.routing.domain.SemanticTaskParameters.PortfolioFact fact
                && fact.getFacets().size() == 1 ? FocusMode.FOCUSED : FocusMode.OVERVIEW;
        return new FactAnswerMaterial(subject.getTitle(), reference, focus, sections,
                focus == FocusMode.OVERVIEW ? SummaryPolicy.REQUIRED : SummaryPolicy.FORBIDDEN,
                List.of(), assessment.getOmittedLabels());
    }

    static List<ExpressionStatement> entries(List<ValidatedEvidenceUnit> units,
            Map<String, SubjectReference> subjects, SupportTarget supportTarget) {
        List<ExpressionStatement> entries = new ArrayList<>();
        int order = 0;
        for (ValidatedEvidenceUnit unit : units) {
            SubjectReference subject = Objects.requireNonNull(subjects.get(unit.getSubjectId()),
                    "public subject label");
            AnswerClaimProjection claim = unit.getClaim();
            AnswerSectionType section = section(claim);
            GroundedStatement statement = new GroundedStatement(StatementType.FACT, List.of(subject),
                    predicate(claim), claim.getStatement(), claim.getDetail(),
                    AnswerClaimCategory.valueOf(claim.getCategory().name()),
                    AnswerAchievementStatus.valueOf(claim.getAchievementStatus().name()),
                    AnswerContributionType.valueOf(claim.getContributionType().name()),
                    AnswerVerificationBasis.valueOf(claim.getVerificationBasis().name()),
                    AnswerMateriality.valueOf(claim.getMateriality().name()), supportTarget,
                    List.of(publicReference(unit)));
            entries.add(new ExpressionStatement(statement, PresentationRole.REQUIRED, section, order++));
        }
        return List.copyOf(entries);
    }

    static PublicSourceReferenceValue publicReference(ValidatedEvidenceUnit unit) {
        PublicSourceReference reference = unit.getSourceReference();
        return new PublicSourceReferenceValue(reference.getReferenceKey(), reference.getLabel(),
                reference.getPublishedVersion(), reference.getSourceType().name(),
                reference.getSubjectRoute(), reference.getEvidenceRoute());
    }

    static void requireAuthoritativeOwnership(List<ValidatedEvidenceUnit> units,
            Map<String, CandidateSubject> subjects) {
        for (ValidatedEvidenceUnit unit : units) {
            CandidateSubject subject = subjects.get(unit.getSubjectId());
            if (subject == null) {
                throw new IllegalArgumentException(
                        "validated evidence subject is outside the selected public subjects");
            }
            if (!subject.getSubjectRoute().equals(unit.getSourceReference().getSubjectRoute())) {
                throw new IllegalArgumentException(
                        "validated evidence public route conflicts with its selected subject");
            }
        }
    }

    private static AnswerSectionType section(AnswerClaimProjection claim) {
        return switch (claim.getCategory()) {
            case BACKGROUND -> AnswerSectionType.BACKGROUND;
            case RESPONSIBILITY -> AnswerSectionType.RESPONSIBILITY;
            case VERIFICATION -> AnswerSectionType.VERIFICATION;
            case LIMITATION -> AnswerSectionType.STATUS;
            default -> AnswerSectionType.SOLUTION;
        };
    }

    private static ControlledPredicate predicate(AnswerClaimProjection claim) {
        return switch (claim.getCategory()) {
            case RESPONSIBILITY -> ControlledPredicate.RESPONSIBLE_FOR;
            case IMPLEMENTATION, TECHNICAL_DECISION -> ControlledPredicate.IMPLEMENTED;
            case VERIFICATION -> ControlledPredicate.VERIFIED_BY_TEST;
            case OUTCOME -> ControlledPredicate.ACHIEVED_OUTCOME;
            case LIMITATION -> ControlledPredicate.HAS_LIMITATION;
            default -> ControlledPredicate.DESCRIBES;
        };
    }
}
