package com.portfolio.agent.turn.capability.portfolio.semantic;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceBundle;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PortfolioSupportEvaluator {
    public Evaluation fact(
            PortfolioEvidenceInvocation invocation, ValidatedEvidenceBundle bundle) {
        List<ValidatedEvidenceUnit> selected = new ArrayList<>();
        List<String> omissions = new ArrayList<>();
        for (PortfolioEvidenceInvocation.FacetProfile facet : invocation.getFacets()) {
            List<ValidatedEvidenceUnit> matches = bundle.getUnits().stream()
                    .filter(unit -> categories(facet).contains(unit.getClaim().getCategory())).toList();
            if (matches.isEmpty()) omissions.add(facet.name());
            else addDistinct(selected, matches.getFirst());
        }
        return Evaluation.of(selected, omissions);
    }

    public Evaluation comparison(
            PortfolioEvidenceInvocation invocation, ValidatedEvidenceBundle bundle) {
        List<ValidatedEvidenceUnit> selected = new ArrayList<>();
        List<String> omissions = new ArrayList<>();
        for (String dimension : invocation.getDimensions()) {
            Set<AnswerClaimCategory> categories = dimensionCategories(dimension);
            for (AuthorizedSubject subject : subjects(invocation)) {
                List<ValidatedEvidenceUnit> matches = bundle.getUnits().stream()
                        .filter(unit -> unit.getSubjectId().equals(subject.id)
                                && categories.contains(unit.getClaim().getCategory())).toList();
                if (matches.isEmpty()) omissions.add(subject.id + "/" + dimension);
                else matches.forEach(unit -> addDistinct(selected, unit));
            }
        }
        return Evaluation.of(selected, omissions);
    }

    private List<AuthorizedSubject> subjects(PortfolioEvidenceInvocation invocation) {
        return invocation.getSubjectScope().getSubjects().stream()
                .map(value -> new AuthorizedSubject(value.getReference())).toList();
    }

    private Set<AnswerClaimCategory> categories(PortfolioEvidenceInvocation.FacetProfile facet) {
        return switch (facet) {
            case BACKGROUND -> EnumSet.of(AnswerClaimCategory.BACKGROUND);
            case RESPONSIBILITY -> EnumSet.of(AnswerClaimCategory.RESPONSIBILITY);
            case IMPLEMENTATION -> EnumSet.of(AnswerClaimCategory.IMPLEMENTATION);
            case TECHNICAL_DECISION -> EnumSet.of(AnswerClaimCategory.TECHNICAL_DECISION);
            case VERIFICATION -> EnumSet.of(AnswerClaimCategory.VERIFICATION);
            case OUTCOME -> EnumSet.of(AnswerClaimCategory.OUTCOME);
            case LIMITATION -> EnumSet.of(AnswerClaimCategory.LIMITATION);
            case RECOMMENDATION -> EnumSet.allOf(AnswerClaimCategory.class);
        };
    }

    private Set<AnswerClaimCategory> dimensionCategories(String dimension) {
        return switch (dimension) {
            case "ARCHITECTURE" ->
                    EnumSet.of(AnswerClaimCategory.TECHNICAL_DECISION);
            case "IMPLEMENTATION" -> EnumSet.of(AnswerClaimCategory.IMPLEMENTATION);
            case "OUTCOME" -> EnumSet.of(AnswerClaimCategory.OUTCOME);
            case "RISKS" -> EnumSet.of(AnswerClaimCategory.LIMITATION);
            case "VERIFICATION" -> EnumSet.of(AnswerClaimCategory.VERIFICATION);
            default -> throw new IllegalArgumentException(
                    "unsupported portfolio comparison dimension");
        };
    }

    private void addDistinct(List<ValidatedEvidenceUnit> target, ValidatedEvidenceUnit unit) {
        boolean exists = target.stream().anyMatch(value ->
                value.getClaim().getId().equals(unit.getClaim().getId())
                        && value.getSourceReference().getReferenceKey().equals(
                        unit.getSourceReference().getReferenceKey()));
        if (!exists) target.add(unit);
    }

    public static final class Evaluation {
        private final List<ValidatedEvidenceUnit> selectedUnits;
        private final List<String> omissions;
        private Evaluation(List<ValidatedEvidenceUnit> selectedUnits, List<String> omissions) {
            this.selectedUnits = List.copyOf(selectedUnits);
            this.omissions = List.copyOf(new LinkedHashSet<>(omissions));
        }
        static Evaluation of(List<ValidatedEvidenceUnit> units, List<String> omissions) {
            return new Evaluation(units, omissions);
        }
        public boolean hasSupport() { return !selectedUnits.isEmpty(); }
        public PortfolioSemanticResult.Coverage coverage() {
            return omissions.isEmpty() ? PortfolioSemanticResult.Coverage.FULL
                    : PortfolioSemanticResult.Coverage.PARTIAL;
        }
        public List<ValidatedEvidenceUnit> getSelectedUnits() { return selectedUnits; }
        public List<String> getOmissions() { return omissions; }
    }

    private static final class AuthorizedSubject {
        private final String id;
        private AuthorizedSubject(String id) { this.id = id; }
    }
}
