package com.portfolio.agent.turn.capability.portfolio.semantic;

import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceBundle;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class PortfolioSemanticResultFactory {
    private final PortfolioSupportEvaluator evaluator;
    public PortfolioSemanticResultFactory(PortfolioSupportEvaluator evaluator) {
        this.evaluator = java.util.Objects.requireNonNull(evaluator, "evaluator");
    }

    public Optional<PortfolioSemanticResult> create(
            SemanticTask task,
            PortfolioEvidenceInvocation invocation,
            ValidatedEvidenceBundle bundle) {
        return switch (task.getType()) {
            case PORTFOLIO_FACT -> fact(invocation, bundle);
            case PORTFOLIO_COMPARE -> comparison(invocation, bundle);
            case PORTFOLIO_RECOMMEND ->
                    recommendation(invocation, bundle);
            default -> throw new IllegalArgumentException("unsupported portfolio task");
        };
    }

    private Optional<PortfolioSemanticResult> fact(
            PortfolioEvidenceInvocation invocation, ValidatedEvidenceBundle bundle) {
        PortfolioSupportEvaluator.Evaluation support = evaluator.fact(invocation, bundle);
        return support.hasSupport() ? Optional.of(new PortfolioSemanticResult.Fact(
                support.coverage(), invocation.getSubjectScope(),
                support.getSelectedUnits(), support.getOmissions(),
                invocation.getDepth())) : Optional.empty();
    }

    private Optional<PortfolioSemanticResult> comparison(
            PortfolioEvidenceInvocation invocation, ValidatedEvidenceBundle bundle) {
        PortfolioSupportEvaluator.Evaluation support = evaluator.comparison(invocation, bundle);
        return support.hasSupport() ? Optional.of(new PortfolioSemanticResult.Comparison(
                support.coverage(), invocation.getSubjectScope(),
                support.getSelectedUnits(), support.getOmissions(),
                invocation.getDimensions().stream().map(value ->
                        UserGoalProposal.PortfolioComparisonDimension.valueOf(value)).toList()))
                : Optional.empty();
    }

    private Optional<PortfolioSemanticResult> recommendation(
            PortfolioEvidenceInvocation invocation, ValidatedEvidenceBundle bundle) {
        if (bundle.getUnits().isEmpty()) return Optional.empty();
        int requestedSize = invocation.getRequestedSize();
        Set<String> constraints = invocation.getRecommendationConstraints();
        Map<String, List<com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit>>
                bySubject = bundle.getUnits().stream().collect(Collectors.groupingBy(
                value -> value.getSubjectId(), java.util.LinkedHashMap::new, Collectors.toList()));
        List<RankedSubject> ranked = bySubject.entrySet().stream()
                .map(entry -> rank(entry.getKey(), entry.getValue(), constraints))
                .sorted(Comparator.comparingInt(RankedSubject::matchedConstraintCount).reversed()
                        .thenComparing(Comparator.comparingInt(RankedSubject::evidenceScore).reversed())
                        .thenComparing(RankedSubject::subjectId))
                .limit(requestedSize).toList();
        List<String> selectedSubjects = ranked.stream().map(RankedSubject::subjectId).toList();
        Set<String> unsatisfied = new LinkedHashSet<>();
        ranked.forEach(value -> unsatisfied.addAll(value.unsatisfiedConstraints()));
        PortfolioSemanticResult.Coverage coverage = selectedSubjects.size() == requestedSize
                && unsatisfied.isEmpty() ? PortfolioSemanticResult.Coverage.FULL
                : PortfolioSemanticResult.Coverage.PARTIAL;
        List<String> omissions = selectedSubjects.size() == requestedSize
                ? List.of() : List.of("REQUESTED_SIZE");
        List<com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit>
                selectedUnits = selectedSubjects.stream().flatMap(value -> bySubject.get(value).stream())
                .toList();
        return Optional.of(new PortfolioSemanticResult.Recommendation(
                coverage, invocation.getSubjectScope(), selectedUnits,
                omissions, requestedSize,
                ranked.stream().map(value -> new PortfolioSemanticResult.Recommendation
                        .RecommendationItem(value.subjectId(), value.reasonCodes())).toList(),
                List.copyOf(unsatisfied)));
    }

    private RankedSubject rank(
            String subjectId,
            List<com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit> units,
            Set<String> constraints) {
        com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit first =
                units.getFirst();
        Set<String> unsatisfied = constraints.stream()
                .filter(value -> !matches(first, value))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<PortfolioSemanticResult.Recommendation.RecommendationReasonCode> reasons =
                new ArrayList<>();
        if (constraints.stream().anyMatch(value -> value.startsWith("CAREER_TRACK_")
                && matches(first, value))) {
            reasons.add(PortfolioSemanticResult.Recommendation.RecommendationReasonCode
                    .CAREER_TRACK_MATCH);
        }
        if (constraints.stream().anyMatch(value -> value.startsWith("CAPABILITY_")
                && matches(first, value))) {
            reasons.add(PortfolioSemanticResult.Recommendation.RecommendationReasonCode
                    .CAPABILITY_MATCH);
        }
        if (units.stream().anyMatch(value -> value.getClaim().getCategory()
                == com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory.IMPLEMENTATION)) {
            reasons.add(PortfolioSemanticResult.Recommendation.RecommendationReasonCode
                    .VERIFIED_IMPLEMENTATION);
        }
        if (units.stream().anyMatch(value -> value.getClaim().getCategory()
                == com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory.VERIFICATION)) {
            reasons.add(PortfolioSemanticResult.Recommendation.RecommendationReasonCode
                    .VERIFIED_VERIFICATION);
        }
        if (units.stream().anyMatch(value -> value.getClaim().getCategory()
                == com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory.OUTCOME)) {
            reasons.add(PortfolioSemanticResult.Recommendation.RecommendationReasonCode
                    .VERIFIED_OUTCOME);
        }
        if (reasons.isEmpty()) {
            reasons.add(PortfolioSemanticResult.Recommendation.RecommendationReasonCode
                    .VERIFIED_PUBLIC_EVIDENCE);
        }
        return new RankedSubject(subjectId, constraints.size() - unsatisfied.size(),
                units.stream().map(value -> value.getClaim().getCategory()).distinct().toList().size(),
                List.copyOf(unsatisfied), List.copyOf(reasons));
    }

    private boolean matches(
            com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit unit,
            String constraint) {
        if (constraint.startsWith("CAREER_TRACK_")) {
            return constraint.substring("CAREER_TRACK_".length()).equals(unit.getCareerTrack());
        }
        if (constraint.startsWith("CAPABILITY_")) {
            return unit.getCapabilityCodes().contains(
                    constraint.substring("CAPABILITY_".length()));
        }
        return false;
    }

    private record RankedSubject(
            String subjectId, int matchedConstraintCount, int evidenceScore,
            List<String> unsatisfiedConstraints,
            List<PortfolioSemanticResult.Recommendation.RecommendationReasonCode> reasonCodes) { }
}
