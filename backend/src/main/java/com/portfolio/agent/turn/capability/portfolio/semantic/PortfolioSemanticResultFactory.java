package com.portfolio.agent.turn.capability.portfolio.semantic;

import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceBundle;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.List;
import java.util.Optional;

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
                    recommendation(task, invocation, bundle);
            default -> throw new IllegalArgumentException("unsupported portfolio task");
        };
    }

    private Optional<PortfolioSemanticResult> fact(
            PortfolioEvidenceInvocation invocation, ValidatedEvidenceBundle bundle) {
        PortfolioSupportEvaluator.Evaluation support = evaluator.fact(invocation, bundle);
        return support.hasSupport() ? Optional.of(new PortfolioSemanticResult.Fact(
                support.coverage(), invocation.getSubjectScope(),
                support.getSelectedUnits(), support.getOmissions())) : Optional.empty();
    }

    private Optional<PortfolioSemanticResult> comparison(
            PortfolioEvidenceInvocation invocation, ValidatedEvidenceBundle bundle) {
        PortfolioSupportEvaluator.Evaluation support = evaluator.comparison(invocation, bundle);
        return support.hasSupport() ? Optional.of(new PortfolioSemanticResult.Comparison(
                support.coverage(), invocation.getSubjectScope(),
                support.getSelectedUnits(), support.getOmissions())) : Optional.empty();
    }

    private Optional<PortfolioSemanticResult> recommendation(
            SemanticTask task, PortfolioEvidenceInvocation invocation,
            ValidatedEvidenceBundle bundle) {
        PortfolioSupportEvaluator.Evaluation support = evaluator.recommendation(bundle);
        if (!support.hasSupport()) return Optional.empty();
        int requestedSize =
                ((UserGoalProposal.PortfolioRecommendationParameters)
                        task.getParameters().getParameters())
                        .getRequestedSize();
        List<String> selectedSubjects = support.getSelectedUnits().stream()
                .map(value -> value.getSubjectId()).distinct().limit(requestedSize).toList();
        if (selectedSubjects.isEmpty()) return Optional.empty();
        PortfolioSemanticResult.Coverage coverage = selectedSubjects.size() == requestedSize
                ? PortfolioSemanticResult.Coverage.FULL : PortfolioSemanticResult.Coverage.PARTIAL;
        List<String> omissions = coverage == PortfolioSemanticResult.Coverage.FULL
                ? List.of() : List.of("REQUESTED_SIZE");
        return Optional.of(new PortfolioSemanticResult.Recommendation(
                coverage, invocation.getSubjectScope(), support.getSelectedUnits(),
                omissions, requestedSize, selectedSubjects));
    }
}
