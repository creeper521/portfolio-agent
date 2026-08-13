package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.domain.ConversationContextEntry;
import com.portfolio.agent.answer.context.domain.ConversationContextSummary;
import com.portfolio.agent.answer.context.domain.ConversationContinuationStatus;
import com.portfolio.agent.answer.context.domain.RecommendationContext;
import com.portfolio.agent.answer.context.domain.RecentSemanticTaskContext;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Projects only public labels and continuation affordances; no opaque or content-bearing fields. */
public final class SafeContextSummaryProjector {
    public static final String SCHEMA_VERSION = "p3-summary-v1";

    public ConversationContextSummary project(ConversationContextEntry entry) {
        return switch (entry.getContextType()) {
            case RECENT_SEMANTIC_TASK -> recent(
                    entry.getValue().getRecentSemanticTaskContext());
            case RECOMMENDATION -> recommendation(
                    entry.getValue().getRecommendationContext());
        };
    }

    private ConversationContextSummary recent(RecentSemanticTaskContext context) {
        List<String> facets = sorted(context.getFacets());
        List<String> dimensions = sorted(context.getDimensions());
        String taskType = publicTaskType(context.getTaskType());
        boolean canRefine = context.getTaskType()
                == SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_RECOMMEND
                || context.getTaskType()
                == SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_REFINE_RECOMMENDATION;
        return new ConversationContextSummary(
                SCHEMA_VERSION, ConversationContinuationStatus.AVAILABLE, taskType,
                List.of(), facets, dimensions, List.of(), canRefine);
    }

    private ConversationContextSummary recommendation(RecommendationContext context) {
        List<String> facets = new ArrayList<>();
        facets.addAll(context.getBaselineCriteria());
        facets.addAll(context.getConstraints());
        facets.addAll(context.getExclusions());
        facets.sort(Comparator.naturalOrder());
        String taskType = context.getParentContextHandle() == null
                ? "RECOMMENDATION" : "REFINE";
        return new ConversationContextSummary(
                SCHEMA_VERSION, ConversationContinuationStatus.AVAILABLE, taskType,
                List.of(), List.copyOf(facets), List.of(),
                sorted(context.getPreferences()), true);
    }

    public ConversationContextSummary unavailable(ConversationContinuationStatus status) {
        if (status == ConversationContinuationStatus.AVAILABLE) {
            throw new IllegalArgumentException("available status requires a Context entry");
        }
        return new ConversationContextSummary(
                SCHEMA_VERSION, status, null, List.of(), List.of(), List.of(), List.of(), false);
    }

    private static String publicTaskType(SemanticRoutingTypes.SemanticTaskType taskType) {
        return switch (taskType) {
            case PORTFOLIO_FACT -> "FACT";
            case PORTFOLIO_COMPARE -> "COMPARE";
            case PORTFOLIO_RECOMMEND -> "RECOMMENDATION";
            case PORTFOLIO_REFINE_RECOMMENDATION -> "REFINE";
            default -> throw new IllegalArgumentException("unsupported context task type");
        };
    }

    private static List<String> sorted(java.util.Collection<String> values) {
        return values.stream().sorted().toList();
    }
}
