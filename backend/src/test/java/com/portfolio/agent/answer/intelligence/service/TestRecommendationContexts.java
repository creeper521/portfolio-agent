package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import java.util.List;
import java.util.Set;

final class TestRecommendationContexts {

    private TestRecommendationContexts() {
    }

    static PortfolioRecommendationContext context() {
        PortfolioConditions conditions = new PortfolioConditions(
                "BACKEND", "INTERVIEWER", Set.of("JAVA"), null, 2);
        String batchId = new RecommendationBatchFingerprint().calculate(
                "public-1", conditions, List.of("project-a", "project-b"));
        return new PortfolioRecommendationContext(
                batchId, "public-1", "BACKEND", "INTERVIEWER", Set.of("JAVA"), 2,
                List.of("project-a", "project-b"));
    }
}
