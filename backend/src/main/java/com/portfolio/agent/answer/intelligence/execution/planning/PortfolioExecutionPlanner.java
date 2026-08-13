package com.portfolio.agent.answer.intelligence.execution.planning;

import com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope;
import com.portfolio.agent.answer.intelligence.execution.domain.ComparisonDimensionProfile;
import com.portfolio.agent.answer.intelligence.execution.domain.EvidenceSelectionPolicy;
import com.portfolio.agent.answer.intelligence.execution.domain.FacetRetrievalProfile;
import com.portfolio.agent.answer.intelligence.execution.domain.PortfolioEvidenceInvocation;
import com.portfolio.agent.answer.routing.domain.AuthorizedContextReference;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskExecutionContext;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Pure compiler from a trusted P2 task/context to one typed retrieval invocation. */
public final class PortfolioExecutionPlanner {

    private final PortfolioCapabilityCatalog catalog;

    public PortfolioExecutionPlanner(PortfolioCapabilityCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public PortfolioExecutionPlan plan(SemanticTaskExecutionContext context) {
        Objects.requireNonNull(context, "context");
        SemanticTask task = context.getSemanticTask();
        if (task.getSourceDomain() != SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO
                || !isPortfolioTask(task.getTaskType())) {
            throw new IllegalArgumentException("only portfolio tasks are accepted");
        }
        if (!context.getExpectedContentVersion().equals(context.getSemanticTask()
                .getSubjectReferences().stream().findFirst()
                .map(SubjectReference::getContentVersion).orElse(context.getExpectedContentVersion()))) {
            throw new IllegalArgumentException("task and execution content versions conflict");
        }
        if (context.getTaskExecutionAllowance().getLogicalRetrievalLimit() < 1
                || context.getTaskExecutionAllowance().isExpired(java.time.Instant.now())) {
            throw new IllegalArgumentException("portfolio retrieval allowance is unavailable");
        }

        PortfolioEvidenceInvocation invocation = invocationFor(task, context);
        return new PortfolioExecutionPlan(
                task.getTaskId(),
                List.of(new PortfolioExecutionPlan.PlannedInvocation(
                        PortfolioCapabilityCatalog.CAPABILITY_ID, invocation)));
    }

    private PortfolioEvidenceInvocation invocationFor(
            SemanticTask task, SemanticTaskExecutionContext context) {
        AuthorizedSubjectScope scope;
        List<FacetRetrievalProfile> facets = new ArrayList<>();
        List<ComparisonDimensionProfile> dimensions = new ArrayList<>();
        SemanticTaskParameters parameters = task.getParameters();
        if (parameters instanceof SemanticTaskParameters.PortfolioFact fact) {
            scope = AuthorizedSubjectScope.exactSubjects(
                    List.of(fact.getSubject()), context.getExpectedContentVersion());
            List<SemanticRoutingTypes.PortfolioFacet> values = new ArrayList<>(fact.getFacets());
            if (values.isEmpty()) {
                values.add(SemanticRoutingTypes.PortfolioFacet.OVERVIEW);
            }
            values.sort(Comparator.comparingInt(Enum::ordinal));
            for (SemanticRoutingTypes.PortfolioFacet value : values) {
                facets.add(FacetRetrievalProfile.forFacet(value));
            }
        } else if (parameters instanceof SemanticTaskParameters.PortfolioCompare comparison) {
            scope = AuthorizedSubjectScope.exactSubjects(
                    comparison.getSubjects(), context.getExpectedContentVersion());
            List<SemanticRoutingTypes.ComparisonDimension> values = new ArrayList<>(comparison.getDimensions());
            if (values.isEmpty()) {
                values.add(SemanticRoutingTypes.ComparisonDimension.IMPLEMENTATION);
            }
            values.sort(Comparator.comparingInt(Enum::ordinal));
            for (SemanticRoutingTypes.ComparisonDimension value : values) {
                dimensions.add(ComparisonDimensionProfile.forDimension(value));
            }
        } else if (parameters instanceof SemanticTaskParameters.PortfolioRecommend recommendation) {
            scope = recommendation.getCandidateSubjects().isEmpty()
                    ? AuthorizedSubjectScope.allPublishedCandidates(context.getExpectedContentVersion())
                    : AuthorizedSubjectScope.exactSubjects(
                            recommendation.getCandidateSubjects(), context.getExpectedContentVersion());
            addRecommendationProfiles(facets);
        } else if (parameters instanceof SemanticTaskParameters.PortfolioRefinement refinement) {
            AuthorizedContextReference reference = findRecommendationReference(context);
            if (reference.getRecommendationScopeBinding().isEmpty()) {
                throw new IllegalArgumentException("recommendation context scope is unavailable");
            }
            scope = reference.getRecommendationScopeBinding().orElseThrow().getScope();
            if (!reference.getRecommendationScopeBinding().orElseThrow()
                    .canRefineTo(new ArrayList<>(refinement.getRemovedSubjects()))) {
                throw new IllegalArgumentException("refinement scope cannot expand");
            }
            addRecommendationProfiles(facets);
        } else {
            throw new IllegalArgumentException("portfolio task parameters are unsupported");
        }
        return new PortfolioEvidenceInvocation(
                scope, facets, dimensions, EvidenceSelectionPolicy.defaults(),
                context.getExpectedContentVersion());
    }

    private void addRecommendationProfiles(List<FacetRetrievalProfile> facets) {
        facets.add(FacetRetrievalProfile.forFacet(SemanticRoutingTypes.PortfolioFacet.RESPONSIBILITY));
        facets.add(FacetRetrievalProfile.forFacet(SemanticRoutingTypes.PortfolioFacet.IMPLEMENTATION));
        facets.add(FacetRetrievalProfile.forFacet(SemanticRoutingTypes.PortfolioFacet.VERIFICATION));
        facets.add(FacetRetrievalProfile.forFacet(SemanticRoutingTypes.PortfolioFacet.OUTCOME));
    }

    private AuthorizedContextReference findRecommendationReference(
            SemanticTaskExecutionContext context) {
        return context.getAuthorizedContextReferences().stream()
                .filter(value -> "RECOMMENDATION".equals(value.getExpectedContextType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("recommendation context is unavailable"));
    }

    private boolean isPortfolioTask(SemanticRoutingTypes.SemanticTaskType taskType) {
        return taskType == SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT
                || taskType == SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_COMPARE
                || taskType == SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_RECOMMEND
                || taskType == SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_REFINE_RECOMMENDATION;
    }
}
