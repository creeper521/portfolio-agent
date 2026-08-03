package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import java.util.List;
import java.util.Set;

final class TestRecommendationContexts {

    private TestRecommendationContexts() {
    }

    static PortfolioRecommendationContext context() {
        return context(List.of("project-a", "project-b"), 2);
    }

    static PortfolioRecommendationContext context(List<String> selectedIds, int requestedSize) {
        PortfolioConditions conditions = new PortfolioConditions(
                "BACKEND", "INTERVIEWER", Set.of("JAVA"), null, requestedSize);
        String batchId = new RecommendationBatchFingerprint().calculate(
                "public-1", conditions, selectedIds);
        return new PortfolioRecommendationContext(
                batchId, "public-1", "BACKEND", "INTERVIEWER", Set.of("JAVA"), requestedSize,
                selectedIds);
    }
}
