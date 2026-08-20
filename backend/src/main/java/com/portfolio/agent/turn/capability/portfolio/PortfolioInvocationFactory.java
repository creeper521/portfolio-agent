package com.portfolio.agent.turn.capability.portfolio;

import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import com.portfolio.agent.turn.execution.TaskExecutionContext;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.ArrayList;
import java.util.List;

public final class PortfolioInvocationFactory {
    private final CorpusBackend primaryBackend;

    public PortfolioInvocationFactory(CorpusBackend primaryBackend) {
        this.primaryBackend = java.util.Objects.requireNonNull(primaryBackend, "primaryBackend");
    }

    public PortfolioEvidenceInvocation create(TaskExecutionContext context) {
        SemanticTask task = context.getTask();
        if (task.getSourceDomain() != SemanticTask.SourceDomain.PORTFOLIO) {
            throw new IllegalArgumentException("only portfolio tasks are accepted");
        }
        if (context.getDeadline().isExpired()) {
            throw new IllegalArgumentException("deadline is expired");
        }
        List<PortfolioEvidenceInvocation.FacetProfile> facets = new ArrayList<>();
        List<String> dimensions = new ArrayList<>();
        AuthorizedSubjectScope scope;
        UserGoalProposal.GoalParameters parameters = task.getParameters().getParameters();
        if (parameters instanceof UserGoalProposal.PortfolioFactParameters fact) {
            scope = AuthorizedSubjectScope.exact(task.getSubjectReferences(), context.getContentReleaseId());
            fact.getFacets().stream().sorted().forEach(value -> facets.addAll(facets(value)));
        } else if (parameters instanceof UserGoalProposal.PortfolioCompareParameters comparison) {
            scope = AuthorizedSubjectScope.exact(task.getSubjectReferences(), context.getContentReleaseId());
            dimensions.addAll(comparison.getDimensions().stream().sorted().toList());
        } else if (parameters instanceof UserGoalProposal.PortfolioRecommendationParameters) {
            scope = task.getSubjectReferences().isEmpty()
                    ? AuthorizedSubjectScope.allPublished(context.getContentReleaseId())
                    : AuthorizedSubjectScope.exact(task.getSubjectReferences(), context.getContentReleaseId());
            facets.add(PortfolioEvidenceInvocation.FacetProfile.RECOMMENDATION);
        } else {
            throw new IllegalArgumentException("unsupported portfolio parameters");
        }
        SearchStrategy strategy = scope.getMode() == AuthorizedSubjectScope.Mode.EXACT
                ? SearchStrategy.EXACT : SearchStrategy.HYBRID;
        CorpusBackend fallbackBackend = primaryBackend == CorpusBackend.POSTGRESQL
                ? CorpusBackend.BUNDLE : null;
        SearchStrategy fallbackStrategy = primaryBackend == CorpusBackend.POSTGRESQL
                ? (strategy == SearchStrategy.HYBRID ? SearchStrategy.KEYWORD : strategy) : null;
        return new PortfolioEvidenceInvocation(
                task.getType(), scope, facets.stream().distinct().toList(), dimensions,
                context.getContentReleaseId(), primaryBackend, strategy,
                fallbackBackend, fallbackStrategy);
    }

    private List<PortfolioEvidenceInvocation.FacetProfile> facets(UserGoalProposal.Facet facet) {
        return switch (facet) {
            case OVERVIEW, BACKGROUND -> List.of(PortfolioEvidenceInvocation.FacetProfile.BACKGROUND);
            case RESPONSIBILITY -> List.of(PortfolioEvidenceInvocation.FacetProfile.RESPONSIBILITY);
            case SOLUTION -> List.of(
                    PortfolioEvidenceInvocation.FacetProfile.IMPLEMENTATION,
                    PortfolioEvidenceInvocation.FacetProfile.TECHNICAL_DECISION);
            case VERIFICATION -> List.of(PortfolioEvidenceInvocation.FacetProfile.VERIFICATION);
            case STATUS -> List.of(
                    PortfolioEvidenceInvocation.FacetProfile.OUTCOME,
                    PortfolioEvidenceInvocation.FacetProfile.LIMITATION);
        };
    }
}
